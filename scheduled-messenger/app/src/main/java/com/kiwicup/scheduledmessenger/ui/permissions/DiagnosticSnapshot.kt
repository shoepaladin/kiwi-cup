package com.kiwicup.scheduledmessenger.ui.permissions

import android.app.Activity
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Telephony
import com.kiwicup.scheduledmessenger.core.DiagnosticReport
import com.kiwicup.scheduledmessenger.core.DiagnosticSection
import com.kiwicup.scheduledmessenger.core.InstallSource
import com.kiwicup.scheduledmessenger.core.PermissionDiagnostics
import com.kiwicup.scheduledmessenger.core.RestrictedPermissions
import com.kiwicup.scheduledmessenger.core.gateState
import com.kiwicup.scheduledmessenger.data.system.DefaultSmsApp

/**
 * Reads the system state behind the permission gate so a user without adb can report facts rather
 * than symptoms. Every reading is taken defensively: a diagnostic that crashes while explaining a
 * failure is worse than useless, so a reading that throws is recorded as an error and the rest of
 * the report still gets written.
 */
object DiagnosticSnapshot {

    fun collect(context: Context, activity: Activity?, log: PermissionAskLog): DiagnosticReport =
        DiagnosticReport(
            listOf(
                device(),
                app(context),
                install(context),
                verdict(context, activity, log),
                restrictedSettings(context),
                role(context),
                permissions(context, activity, log)
            )
        )

    private fun reading(block: () -> Any?): String =
        runCatching { block()?.toString() ?: "null" }
            .getOrElse { "error: ${it.javaClass.simpleName}: ${it.message}" }

    private fun device() = DiagnosticSection(
        "Device",
        listOf(
            "android release" to reading { Build.VERSION.RELEASE },
            "sdk int" to reading { Build.VERSION.SDK_INT },
            "manufacturer" to reading { Build.MANUFACTURER },
            "model" to reading { Build.MODEL },
            "security patch" to reading {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "n/a"
            }
        )
    )

    private fun app(context: Context) = DiagnosticSection(
        "App",
        listOf(
            "package" to reading { context.packageName },
            "version" to reading {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            },
            "version code" to reading {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
                else @Suppress("DEPRECATION") info.versionCode.toLong()
            },
            "target sdk" to reading { context.applicationInfo.targetSdkVersion }
        )
    )

    /**
     * The installer is the whole ballgame: only one holding `WHITELIST_RESTRICTED_PERMISSIONS`
     * allowlists the SMS group. All three source fields are recorded because they can disagree —
     * a browser download, for instance, typically initiates while the system package installer
     * installs — and our heuristic reads only one of them.
     */
    private fun install(context: Context): DiagnosticSection {
        val pm = context.packageManager
        val entries = mutableListOf<Pair<String, String>>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            entries += "installing package" to reading {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            }
            entries += "initiating package" to reading {
                pm.getInstallSourceInfo(context.packageName).initiatingPackageName
            }
            entries += "originating package" to reading {
                pm.getInstallSourceInfo(context.packageName).originatingPackageName
            }
        } else {
            entries += "installer package" to reading {
                @Suppress("DEPRECATION") pm.getInstallerPackageName(context.packageName)
            }
        }
        entries += "heuristic read" to reading { AppPermissions.installerPackage(context) }
        return DiagnosticSection("Install source", entries)
    }

    private fun verdict(context: Context, activity: Activity?, log: PermissionAskLog) = DiagnosticSection(
        "Verdict",
        listOf(
            "sms likely restricted" to reading { AppPermissions.smsRestrictedByInstaller(context) },
            "role also restricted" to reading {
                InstallSource.roleAlsoRestricted(Build.VERSION.SDK_INT, AppPermissions.installerPackage(context))
            },
            "gate state" to reading {
                gateState(
                    AppPermissions.states(context, activity, log),
                    AppPermissions.requiredSet,
                    AppPermissions.smsRestrictedByInstaller(context)
                )
            }
        )
    )

    private fun role(context: Context) = DiagnosticSection(
        "Default SMS role",
        listOf(
            "current default app" to reading { Telephony.Sms.getDefaultSmsPackage(context) },
            "we hold the role" to reading { DefaultSmsApp.isDefault(context) },
            "role available" to reading {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) "n/a"
                else context.getSystemService(RoleManager::class.java)?.isRoleAvailable(RoleManager.ROLE_SMS)
            },
            "role held" to reading {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) "n/a"
                else context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS)
            },
            // Null here means the system will not even offer the prompt, which is the single most
            // useful reading when the role dialog never appeared.
            "request intent" to reading {
                if (DefaultSmsApp.requestIntent(context) == null) "null (system will not offer it)" else "available"
            }
        )
    )

    /**
     * Per permission, the three readings that disambiguate a refusal. `granted` and `rationale`
     * are what the gate already reasons about; the app-op mode is the extra one, because a
     * permission that is granted but whose op is `ignored` is being silently blocked rather than
     * denied, and nothing else we can read tells those apart.
     */
    private fun permissions(context: Context, activity: Activity?, log: PermissionAskLog): DiagnosticSection {
        val declared = runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.toSet().orEmpty()
        }.getOrDefault(emptySet())

        val states = runCatching { AppPermissions.states(context, activity, log) }.getOrDefault(emptyList())
        val diagnoses = runCatching { PermissionDiagnostics.diagnose(states) }.getOrDefault(emptyMap())

        val entries = states.map { state ->
            val short = state.permission.removePrefix("android.permission.")
            val flags = listOf(
                if (state.granted) "granted" else "denied",
                "rationale=${state.canShowRationale}",
                "asks=${state.timesAsked}",
                "op=${appOpMode(context, state.permission)}",
                "restricted=${RestrictedPermissions.isRestricted(state.permission)}",
                "inManifest=${state.permission in declared}",
                diagnoses[state.permission]?.name ?: "?"
            )
            short to flags.joinToString(" ")
        }
        return DiagnosticSection("Permissions", entries)
    }

    /**
     * Whether the user's "Allow restricted settings" tap actually took effect, which is the one
     * thing the instructions screen asks for and the one thing it cannot otherwise confirm.
     *
     * The op name is written out rather than taken from [AppOpsManager], whose constant for it is
     * not public API; the string is the stable identifier the platform uses. If this reads
     * `allowed` while the SMS permissions are still denied, then clearing restricted settings is
     * not the remedy for a hard-restricted permission and only a reinstall can help.
     */
    private fun restrictedSettings(context: Context) = DiagnosticSection(
        "Restricted settings",
        listOf(
            "access_restricted_settings" to opMode(context, "android:access_restricted_settings")
        )
    )

    /** `ignored` is the tell for a silently blocked permission; `allowed` means the op is not the problem. */
    private fun appOpMode(context: Context, permission: String): String {
        val op = runCatching { AppOpsManager.permissionToOp(permission) }.getOrNull() ?: return "none"
        return opMode(context, op)
    }

    private fun opMode(context: Context, op: String): String = reading {
        val manager = context.getSystemService(AppOpsManager::class.java) ?: return@reading "unavailable"
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            manager.unsafeCheckOpNoThrow(op, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION") manager.checkOpNoThrow(op, Process.myUid(), context.packageName)
        }
        when (mode) {
            AppOpsManager.MODE_ALLOWED -> "allowed"
            AppOpsManager.MODE_IGNORED -> "ignored"
            AppOpsManager.MODE_ERRORED -> "errored"
            AppOpsManager.MODE_DEFAULT -> "default"
            else -> "mode$mode"
        }
    }
}
