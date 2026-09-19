package com.kiwicup.scheduledmessenger.core

/**
 * How far from the newest message still counts as "reading the latest".
 *
 * One item, matching the established convention: Fossify and QKSMS both scroll only when the
 * user's last visible item was exactly one short of the end. A tolerance rather than an exact
 * match matters because a partially visible row would otherwise read as "scrolled away".
 */
const val LATEST_MESSAGE_TOLERANCE = 1

/**
 * Whether a conversation should jump to the newest message.
 *
 * The rule is not "always". Scrolling unconditionally on every new message means a text arriving
 * while the user is reading history yanks them away from it, which is what this app did before.
 * Every reference implementation gates on the user already being at the bottom, and makes exactly
 * one exception: sending. Having just sent something, the user wants to see it land.
 *
 * @param firstVisibleIndex index of the first visible item in a reverse-laid-out list, where 0 is
 *   the newest message. Anything above the tolerance means the user has scrolled into history.
 * @param sendRequested the user pressed send, which overrides their scroll position.
 */
fun shouldScrollToLatest(firstVisibleIndex: Int, sendRequested: Boolean): Boolean =
    sendRequested || firstVisibleIndex <= LATEST_MESSAGE_TOLERANCE
