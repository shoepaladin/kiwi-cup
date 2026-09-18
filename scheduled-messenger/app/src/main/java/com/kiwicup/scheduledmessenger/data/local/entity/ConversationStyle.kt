package com.kiwicup.scheduledmessenger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Look of one conversation, keyed by the other party's normalised phone number. */
@Entity(tableName = "conversation_styles")
data class ConversationStyle(
    @PrimaryKey val address: String,
    val incomingBubbleColor: Int? = null,
    val outgoingBubbleColor: Int? = null,
    /** Absolute path of a wallpaper copied into app storage; null for none. */
    val wallpaperPath: String? = null,
    /** 0..100, how much to darken the wallpaper so text stays readable. */
    val wallpaperDimPercent: Int = 30
) {
    val isEmpty: Boolean
        get() = incomingBubbleColor == null && outgoingBubbleColor == null && wallpaperPath == null
}
