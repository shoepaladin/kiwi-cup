package com.kiwicup.scheduledmessenger.core

/**
 * Single source of truth for which status changes are legal.
 *
 * Kept as data (a map) rather than scattered `if` checks so the DAO update statements,
 * the worker and the UI all agree, and so the rule set is trivially unit-testable.
 */
object StatusTransitions {

    private val allowed: Map<MessageStatus, Set<MessageStatus>> = mapOf(
        MessageStatus.PENDING to setOf(MessageStatus.SENDING, MessageStatus.CANCELLED, MessageStatus.FAILED),
        // A SENDING message can go back to PENDING when WorkManager retries after a transient failure.
        MessageStatus.SENDING to setOf(MessageStatus.SENT, MessageStatus.FAILED, MessageStatus.PENDING),
        // Re-scheduling a failed message is the only way out of a terminal state and it is user-driven.
        MessageStatus.FAILED to setOf(MessageStatus.PENDING),
        MessageStatus.SENT to emptySet(),
        MessageStatus.CANCELLED to setOf(MessageStatus.PENDING)
    )

    fun canTransition(from: MessageStatus, to: MessageStatus): Boolean =
        allowed.getValue(from).contains(to)

    /** Returns [to] or throws with a descriptive message so bugs surface loudly in tests. */
    fun require(from: MessageStatus, to: MessageStatus): MessageStatus {
        check(canTransition(from, to)) { "Illegal status transition $from -> $to" }
        return to
    }
}
