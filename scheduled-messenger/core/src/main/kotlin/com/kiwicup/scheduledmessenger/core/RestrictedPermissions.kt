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
    required: Set<String> = states.mapTo(mutableSetOf()) { it.permission }
): PermissionGateState {
    if (states.none { it.permission in required }) return PermissionGateState.READY
    val diagnoses = PermissionDiagnostics.diagnose(states).filterKeys { it in required }.values
    return when {
        diagnoses.all { it == PermissionDiagnosis.GRANTED } -> PermissionGateState.READY
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

    val steps: List<String> = listOf(
        "Open this app's App info page (the button below goes straight there).",
        "Tap the three-dot menu in the top corner, then \"$OVERFLOW_ITEM\". On Android 15 and " +
            "newer you may need to scroll to the bottom of the page to find it.",
        "Come back to Permissions, tap SMS, and choose Allow.",
        "Return here and tap Check again."
    )

    /** The alternative for anyone with a computer to hand: adb allowlists these on install. */
    const val ADB_ALTERNATIVE =
        "Installing with \"adb install\" instead allowlists these permissions automatically."
}
