package com.kiwicup.scheduledmessenger.notifications

/** The conversation currently on screen, so its notifications are not posted over it. */
object VisibleThread {
    @Volatile var current: Long? = null
}
