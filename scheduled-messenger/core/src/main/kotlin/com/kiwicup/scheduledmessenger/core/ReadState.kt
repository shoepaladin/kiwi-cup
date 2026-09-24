package com.kiwicup.scheduledmessenger.core

/**
 * The one judgement call in read tracking: while a conversation is on screen, which unread
 * messages should quietly become read?
 *
 * Only ones that arrived during this visit (id above the highest id when it opened), and never
 * one the user explicitly marked unread during the visit — otherwise "Mark unread" would be undone
 * the moment the screen resumed, or the moment any new text arrived.
 */
object ReadState {
    fun arrivalsToMarkRead(unreadIds: Collection<Long>, openedAtMaxId: Long, markedUnreadThisVisit: Set<Long>): List<Long> =
        unreadIds.filter { it > openedAtMaxId && it !in markedUnreadThisVisit }
}
