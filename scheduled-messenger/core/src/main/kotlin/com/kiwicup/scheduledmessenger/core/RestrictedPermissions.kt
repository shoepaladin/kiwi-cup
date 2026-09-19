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
     * True when the SMS group will be refused on sight. Hard restriction landed in Android 10
     * (API 29); Android 15 (API 35) additionally gates the default-SMS-app *role* behind the same
     * user opt-in, so on 35+ neither the permissions nor the role can be obtained until the user
     * allows restricted settings.
     */
    fun smsLikelyRestricted(sdkInt: Int, installerPackage: String?): Boolean =
        sdkInt >= 29 && !allowlistsRestrictedPermissions(installerPackage)

    /** On these versions the role request is refused too, so offering it first cannot help. */
    fun roleAlsoRestricted(sdkInt: Int, installerPackage: String?): Boolean =
        sdkInt >= 35 && !allowlistsRestrictedPermissions(installerPackage)
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
    /**
     * Set from [InstallSource.smsLikelyRestricted]. When the installer could not allowlist, the
     * refusal is a foregone conclusion, so the gate skips straight to the instructions instead of
     * firing a request that the system answers with an alarming dialog and nothing else.
     */
    restrictedByInstaller: Boolean = false
): PermissionGateState {
    if (states.none { it.permission in required }) return PermissionGateState.READY
    val diagnoses = PermissionDiagnostics.diagnose(states).filterKeys { it in required }.values
    return when {
        diagnoses.all { it == PermissionDiagnosis.GRANTED } -> PermissionGateState.READY
        restrictedByInstaller -> PermissionGateState.RESTRICTED
        diagnoses.any { it == PermissionDiagnosis.BLOCKED_AS_RESTRICTED } -> PermissionGateState.RESTRICTED
        diagnoses.any { it == PermissionDiagnosis.NOT_YET_ASKED } -> PermissionGateState.ASK
        diagnoses.any { it == PermissionDiagnosis.DENIED_CAN_RETRY } -> PermissionGateState.EXPLAIN_AND_ASK
        else -> PermissionGateState.OPEN_SETTINGS
    }
}

/**
 * The steps that actually clear the restriction, in the order the user has to perform them.
 * Kept here rather than in the UI so a test can hold the wording to the one phrase that matters:
 * the overflow-menu item is the whole point, and an explanation that omits it is useless.
 */
object RestrictedPermissionHelp {
    const val OVERFLOW_ITEM = "Allow restricted settings"

    /**
     * Order matters and is the whole point. On Android 15+ the restriction covers the
     * default-SMS-app role as well as the permissions, so asking to be the default app *first*
     * gets refused silently, with no dialog shown at all. The overflow item has to come before
     * anything else is attempted.
     */
    val steps: List<String> = listOf(
        "Open this app's App info page (the button below goes straight there).",
        "Tap the three-dot menu in the top corner, then \"$OVERFLOW_ITEM\". On Android 15 and " +
            "newer this sits at the bottom of the page, so scroll down. This step has to come " +
            "first — until it is done, Android silently refuses everything below.",
        "Still in App info, open Permissions, tap SMS, and choose Allow.",
        "Return here and tap Check again. The app will then offer to become your default SMS " +
            "app, which is what unlocks sending and receiving."
    )

    /** The alternative for anyone with a computer to hand: the shell installer allowlists. */
    const val ADB_ALTERNATIVE =
        "Or reinstall with \"adb install\" from a computer: the shell installer allowlists these " +
            "permissions automatically, so none of the steps above are needed."
}
