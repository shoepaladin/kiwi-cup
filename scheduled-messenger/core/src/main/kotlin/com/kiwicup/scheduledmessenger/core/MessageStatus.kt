package com.kiwicup.scheduledmessenger.core

/**
 * Lifecycle of a scheduled message.
 *
 * The spec calls for PENDING / SENT / FAILED. Two extra states are added on purpose:
 *  - [SENDING] is the "claimed" state a worker moves a row into atomically before it touches
 *    the radio. It is what makes the delete-vs-dispatch race safe: a worker that fails to claim
 *    (0 rows updated) simply stops.
 *  - [CANCELLED] records a user cancellation so the queue history stays honest instead of the
 *    row silently vanishing while a worker may still be in flight.
 */
enum class MessageStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    CANCELLED;

    /** True when no further automatic transition should ever happen. */
    val isTerminal: Boolean
        get() = this == SENT || this == FAILED || this == CANCELLED

    /** True when the message still needs a WorkManager job to exist. */
    val needsWork: Boolean
        get() = this == PENDING
}

/** Status of a message that lives in a conversation thread (inbox / sent history). */
enum class SmsStatus {
    RECEIVED,
    SENT,
    FAILED
}
