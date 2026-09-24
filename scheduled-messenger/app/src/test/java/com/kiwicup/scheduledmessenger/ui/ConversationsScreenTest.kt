package com.kiwicup.scheduledmessenger.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.data.local.dao.ThreadSummary
import com.kiwicup.scheduledmessenger.ui.conversations.ConversationsScreen
import com.kiwicup.scheduledmessenger.ui.theme.ScheduledMessengerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun summary(threadId: Long, unread: Int) = ThreadSummary(
        threadId = threadId, address = "+1555000$threadId", body = "hi", timestamp = 1_700_000_000_000L,
        messageCount = 3, unreadCount = unread
    )

    private fun render(threads: List<ThreadSummary>) {
        compose.setContent {
            ScheduledMessengerTheme(dynamicColor = false) {
                ConversationsScreen(threads = threads, onOpenThread = {}, onNewMessage = {}, onOpenQueue = {})
            }
        }
    }

    @Test
    fun anUnreadConversationShowsItsCount() {
        render(listOf(summary(1, unread = 3)))
        compose.onNodeWithTag("unread_badge_1").assertIsDisplayed()
    }

    @Test
    fun aReadConversationHasNoBadge() {
        render(listOf(summary(2, unread = 0)))
        compose.onNodeWithTag("unread_badge_2").assertDoesNotExist()
    }
}
