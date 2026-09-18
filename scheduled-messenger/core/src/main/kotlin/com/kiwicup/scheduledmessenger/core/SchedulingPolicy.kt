package com.kiwicup.scheduledmessenger.core

/** What to do with a pending message when its job is (re)created. */
sealed class RecoveryAction {
    /** Target is still in the future: enqueue with this delay. */
    data class DispatchLater(val delayMillis: Long) : RecoveryAction()

    /** Target already passed but is within the lateness window: send right away. */
    object DispatchNow : RecoveryAction()

    /** Target passed too long ago (device was off / app was dead): mark FAILED, do not send. */
    object Expire : RecoveryAction()
}

/**
 * Pure time arithmetic for scheduling. No Android types so it runs on the JVM.
 *
 * Design choice: a message that comes due while the phone is off is still sent if the phone
 * comes back within [maxLatenessMillis] (default 6 hours). A "happy birthday" sent two hours
 * late is welcome; one sent three days late is not. The window is a constructor parameter so
 * product can tune it without touching the workers.
 */
class SchedulingPolicy(
    val maxLatenessMillis: Long = DEFAULT_MAX_LATENESS_MILLIS
) {
    init {
        require(maxLatenessMillis >= 0) { "maxLatenessMillis must be >= 0" }
    }

    /** Delay to pass to WorkManager; never negative. */
    fun initialDelayMillis(nowMillis: Long, targetMillis: Long): Long =
        (targetMillis - nowMillis).coerceAtLeast(0L)

    /** True when the target is in the past by more than the lateness window. */
    fun isExpired(nowMillis: Long, targetMillis: Long): Boolean =
        nowMillis - targetMillis > maxLatenessMillis

    /** Decision used both on first enqueue and when re-enqueueing after reboot. */
    fun recoveryAction(nowMillis: Long, targetMillis: Long): RecoveryAction = when {
        targetMillis > nowMillis -> RecoveryAction.DispatchLater(targetMillis - nowMillis)
        isExpired(nowMillis, targetMillis) -> RecoveryAction.Expire
        else -> RecoveryAction.DispatchNow
    }

    /** A user may not schedule in the past; a tiny grace avoids "now" being rejected. */
    fun isValidTarget(nowMillis: Long, targetMillis: Long, graceMillis: Long = 60_000L): Boolean =
        targetMillis >= nowMillis - graceMillis

    companion object {
        const val DEFAULT_MAX_LATENESS_MILLIS: Long = 6L * 60L * 60L * 1000L
    }
}
