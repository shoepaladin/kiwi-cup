package com.kiwicup.scheduledmessenger.core

/**
 * Android hard-restricts the SMS and call-log permissions for apps whose installer did not
 * allowlist them. The Play Store allowlists; a file manager, a browser download or an F-Droid
 * style sideload does not. Requesting one of these then fails in a way that looks like nothing
 * else: the system denies it instantly, without ever drawing a dialog, and tells the user the app
 * "was denied access to SMS".
 *
 * This matters because the usual remedy is wrong. Sending the user to the app's settings page
 * does not help: the SMS toggle is there, but turning it on repeats the same refusal. The user
 * must first pick "Allow restricted settings" from the overflow menu on the App info page.
 */
object RestrictedPermissions {
    private const val PREFIX = "android.permission."

    val sms: Set<String> = setOf(
        "${PREFIX}READ_SMS",
        "${PREFIX}RECEIVE_SMS",
        "${PREFIX}SEND_SMS",
        "${PREFIX}RECEIVE_MMS",
        "${PREFIX}RECEIVE_WAP_PUSH",
        "${PREFIX}READ_CELL_BROADCASTS"
    )

    val callLog: Set<String> = setOf(
        "${PREFIX}READ_CALL_LOG",
        "${PREFIX}WRITE_CALL_LOG",
        "${PREFIX}PROCESS_OUTGOING_CALLS"
    )

    val all: Set<String> = sms + callLog

    fun isRestricted(permission: String): Boolean = permission in all
}

/**
 * Who installed the app decides whether its restricted permissions were allowlisted, and that is
 * knowable before asking for anything. Only an installer holding
 * `WHITELIST_RESTRICTED_PERMISSIONS` can allowlist, which in practice means the Play Store or the
 * shell. Everything else — a file manager, a browser download, F-Droid, Obtainium — cannot, and on
 * those installs the SMS group is refused without a dialog.
 */
object InstallSource {
    const val PLAY_STORE = "com.android.vending"

    /** `adb install` runs as the shell, which allowlists restricted permissions and records no
     *  installing package. A null installer therefore means shell-installed, not unknown. */
    fun allowlistsRestrictedPermissions(installerPackage: String?): Boolean =
        installerPackage == null || installerPackage == PLAY_STORE

    /**
     * True when a *runtime permission request* for the SMS group will be refused on sight. Hard
     * restriction landed in Android 10 (API 29).
     *
     * This says nothing about the default-SMS-app role. An earlier version of this file claimed
     * the role was restricted alongside the permissions from API 35, and a device report from a
     * Pixel 8a on API 37, sideloaded via com.google.android.packageinstaller, disproved it:
     * `isRoleAvailable` was true and `createRequestRoleIntent` returned an intent. The role is not
     * blocked by the installer — it is the way *past* the installer, because the role controller
     * grants the SMS group to whichever app holds the role. That is why sideloaded SMS apps work
     * at all. Never use this flag to suppress a role request.
     */
    fun smsLikelyRestricted(sdkInt: Int, installerPackage: String?): Boolean =
        sdkInt >= 29 && !allowlistsRestrictedPermissions(installerPackage)
}

/** Whether the default-SMS-app role can be asked for, as the platform currently reports it. */
enum class SmsRoleStatus {
    /** We are the default SMS app, so the SMS group came with it. */
    HELD,

    /** The system will draw the role dialog if we ask. */
    OFFERABLE,

    /** No SMS role on this device — a tablet with no radio — so it cannot carry anything. */
    UNAVAILABLE
}

/** One permission exactly as the system currently reports it, plus our own count of asks. */
data class PermissionState(
    val permission: String,
    val granted: Boolean,
    /**
     * `shouldShowRequestPermissionRationale`. Note it is false both before the first ask and
     * after a permanent denial, which is why [timesAsked] is needed to tell those apart.
     */
    val canShowRationale: Boolean,
    /** How many times this app has launched a request for it. The platform does not track this. */
    val timesAsked: Int
) {
    init {
        require(timesAsked >= 0) { "timesAsked cannot be negative" }
    }

    val isRestricted: Boolean get() = RestrictedPermissions.isRestricted(permission)
}

/** Why a permission is unusable, and therefore what the user should be told to do about it. */
enum class PermissionDiagnosis {
    GRANTED,
    NOT_YET_ASKED,

    /** The user said no once. Asking again still shows a dialog. */
    DENIED_CAN_RETRY,

    /** The user said no enough times that Android stopped asking. The settings toggle works. */
    DENIED_NEEDS_SETTINGS,

    /** The system refused without asking, because the installer did not allowlist it. */
    BLOCKED_AS_RESTRICTED
}

object PermissionDiagnostics {

    /**
     * Diagnoses a whole request batch at once, because one permission's result is evidence about
     * another's. A restricted permission is denied with no dialog, so if a sibling permission in
     * the same batch clearly *did* get a dialog, any restricted one that came back denied was
     * refused by the system rather than by the user.
     */
    fun diagnose(states: List<PermissionState>): Map<String, PermissionDiagnosis> {
        val dialogAppeared = states.any { !it.isRestricted && it.timesAsked > 0 && (it.granted || it.canShowRationale) }
        return states.associate { it.permission to diagnose(it, dialogAppeared) }
    }

    fun diagnose(state: PermissionState, dialogAppeared: Boolean = false): PermissionDiagnosis = when {
        state.granted -> PermissionDiagnosis.GRANTED
        state.timesAsked == 0 -> PermissionDiagnosis.NOT_YET_ASKED
        state.canShowRationale -> PermissionDiagnosis.DENIED_CAN_RETRY
        // Denied with no rationale offered. A real user denial leaves rationale true the first
        // time, so on a single ask only a restricted permission can land here. On later asks we
        // need a sibling permission to prove the dialog is being drawn at all.
        state.isRestricted && (state.timesAsked == 1 || dialogAppeared) ->
            PermissionDiagnosis.BLOCKED_AS_RESTRICTED
        else -> PermissionDiagnosis.DENIED_NEEDS_SETTINGS
    }
}

/** What the permission gate should put on screen. */
enum class PermissionGateState {
    /** Everything required is granted; show the app. */
    READY,

    /** Nothing asked yet: fire the request. */
    ASK,

    /**
     * Ask to become the default SMS app. This comes before any permission request, because on a
     * sideload the role is the only route to the SMS group: requesting the permissions directly
     * is refused without a dialog, while the role controller grants them outright.
     */
    REQUEST_ROLE,

    /** The user turned something down but can be asked again. */
    EXPLAIN_AND_ASK,

    /** Android will not prompt again; the settings toggle is the way out. */
    OPEN_SETTINGS,

    /** Sideload restriction: settings alone will not fix it, so show the real instructions. */
    RESTRICTED
}

/**
 * Collapses the permissions that block the app into the one screen to show. [RESTRICTED] outranks
 * the others: while it holds, the remaining permissions cannot be granted by any route the other
 * states suggest, so telling the user to "try again" or to open settings would just waste taps.
 *
 * [states] should be the whole request batch, including permissions the app can live without,
 * since those are what corroborate a restriction. [required] narrows which of them actually gate
 * the app.
 */
fun gateState(
    states: List<PermissionState>,
    required: Set<String> = states.mapTo(mutableSetOf()) { it.permission },
    /** As the platform reports it; see [SmsRoleStatus]. */
    role: SmsRoleStatus = SmsRoleStatus.UNAVAILABLE,
    /** Whether the role dialog has already been put in front of the user this install. */
    roleOffered: Boolean = false,
    /**
     * Set from [InstallSource.smsLikelyRestricted]. Note where this is consulted: only *after* the
     * role has been offered and not taken. An earlier version checked it first and returned
     * [PermissionGateState.RESTRICTED] before anything had been asked for, which made the app
     * refuse to try and then blame Android for the result.
     */
    restrictedByInstaller: Boolean = false
): PermissionGateState {
    if (states.none { it.permission in required }) return PermissionGateState.READY
    val diagnoses = PermissionDiagnostics.diagnose(states).filterKeys { it in required }.values
    if (diagnoses.all { it == PermissionDiagnosis.GRANTED }) return PermissionGateState.READY

    // The role leads, unconditionally. On a sideload it is the only route to the SMS group, since
    // the role controller grants the group outright where a runtime request is refused; on a Play
    // Store install it is required anyway to send and receive.
    if (role == SmsRoleStatus.OFFERABLE && !roleOffered) return PermissionGateState.REQUEST_ROLE

    val ordinary = when {
        diagnoses.any { it == PermissionDiagnosis.NOT_YET_ASKED } -> PermissionGateState.ASK
        diagnoses.any { it == PermissionDiagnosis.DENIED_CAN_RETRY } -> PermissionGateState.EXPLAIN_AND_ASK
        else -> PermissionGateState.OPEN_SETTINGS
    }

    // Holding the role means the group is grantable by the ordinary route whatever the installer
    // was, so the sideload instructions would be actively misleading here.
    if (role == SmsRoleStatus.HELD) return ordinary

    // The role was offered and declined, or does not exist on this device. Only now is a refusal
    // a foregone conclusion, and only now are the instructions the right thing to show.
    val refusalIsCertain =
        restrictedByInstaller || diagnoses.any { it == PermissionDiagnosis.BLOCKED_AS_RESTRICTED }
    return if (refusalIsCertain) PermissionGateState.RESTRICTED else ordinary
}

/**
 * The steps that actually clear the restriction, in the order the user has to perform them.
 * Kept here rather than in the UI so a test can hold the wording to the one phrase that matters:
 * the overflow-menu item is the whole point, and an explanation that omits it is useless.
 */
object RestrictedPermissionHelp {
    const val OVERFLOW_ITEM = "Allow restricted settings"
    const val ROLE_ACTION = "Make this your default SMS app"

    /**
     * Order matters and an earlier version had it exactly backwards. It put the overflow item
     * first on the theory that the role was restricted until restricted settings were allowed. A
     * device report disproved that: on a sideloaded Pixel 8a running API 37 the role was
     * available and offerable while every SMS permission sat denied.
     *
     * The role leads because it is the remedy, not another thing the restriction blocks: the role
     * controller grants the SMS group to its holder, which is the only reason a sideloaded SMS
     * app can work at all. The settings route below is the fallback for when the role dialog does
     * not appear or is declined.
     */
    val steps: List<String> = listOf(
        "Tap \"$ROLE_ACTION\" below and accept. Holding that role is what grants SMS access, and " +
            "on a sideloaded install it is the only thing that can.",
        "Only if no dialog appeared, or you declined it: open this app's App info page, tap the " +
            "three-dot menu in the top corner, then \"$OVERFLOW_ITEM\". On Android 15 and newer " +
            "this sits at the bottom of the page, so scroll down.",
        "Still in App info, open Permissions, tap SMS, and choose Allow.",
        "Return here and tap Check again."
    )

    /** The alternative for anyone with a computer to hand: the shell installer allowlists. */
    const val ADB_ALTERNATIVE =
        "Or reinstall with \"adb install\" from a computer: the shell installer allowlists these " +
            "permissions automatically, so none of the steps above are needed."
}
