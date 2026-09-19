package com.kiwicup.scheduledmessenger.core

/**
 * What the user asked for when they submitted a message.
 *
 * Both routes go through the same scheduling machinery — that is deliberate, since it carries the
 * claim-once guarantee, the retry policy and the permission handling — but they are different
 * requests and must not be reported back the same way. Sending a text now is an ordinary
 * conversation; only a message queued for later belongs in the schedule.
 */
enum class SendOutcome {
    /** Send immediately. The result belongs in the conversation. */
    SENT_NOW,

    /** Queue for a future time. The result belongs in the schedule. */
    SCHEDULED
}

/**
 * Whether a stored message was genuinely queued for later, rather than sent immediately.
 *
 * Send-now is implemented as "schedule for this instant", so both land in the same table and the
 * schedule listed immediate sends as though they were pending — a text sent to a new contact
 * appeared as a scheduled message instead of a conversation. The two are separable without a
 * schema change: a row is only scheduled if its target is *after* the moment it was created.
 * Send-now takes the clock before the row is built, so its target can never be later than its
 * creation, while a future target always is.
 */
fun wasScheduledForLater(targetTimestamp: Long, createdAt: Long): Boolean = targetTimestamp > createdAt

/**
 * Whether a stored message should be listed on the schedule screen.
 *
 * Hiding every immediate send would be too blunt. A send that succeeded belongs in its
 * conversation and nowhere else, but a send that *failed* has to surface somewhere, and until
 * the conversation itself shows a failed state the schedule is the only place it can. So a
 * failure is listed whether or not it was ever scheduled.
 */
fun belongsInSchedule(targetTimestamp: Long, createdAt: Long, failed: Boolean): Boolean =
    failed || wasScheduledForLater(targetTimestamp, createdAt)
