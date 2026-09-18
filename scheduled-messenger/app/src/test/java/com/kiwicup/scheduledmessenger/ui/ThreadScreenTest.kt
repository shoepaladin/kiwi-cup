package com.kiwicup.scheduledmessenger.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import com.kiwicup.scheduledmessenger.ui.theme.ScheduledMessengerTheme
import com.kiwicup.scheduledmessenger.ui.thread.ThreadScreen
import com.kiwicup.scheduledmessenger.ui.thread.ThreadUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 1_700_000_000_000L
    private val incoming = SmsMessage(id = 1, threadId = 5, address = "+15550009999", body = "Dinner Friday?", timestamp = now - 60_000, status = SmsStatus.RECEIVED)
    private val outgoing = SmsMessage(id = 2, threadId = 5, address = "+15550009999", body = "Let me check", timestamp = now, status = SmsStatus.SENT, isIncoming = false)

    private fun render(state: ThreadUiState, onRemind: (SmsMessage, String, Long) -> Unit = { _, _, _ -> }) {
        compose.setContent {
            ScheduledMessengerTheme(dynamicColor = false) {
                ThreadScreen(
                    state = state,
                    nowMillis = { now },
                    onDraftChange = {},
                    onSendNow = {},
                    onSchedule = {},
                    validateTarget = { null },
                    onRemind = onRemind,
                    onSnackbarShown = {},
                    onBack = {}
                )
            }
        }
    }

    @Test
    fun rendersBubblesAndReminderBanner() {
        render(
            ThreadUiState(
                threadId = 5, address = "+15550009999", messages = listOf(incoming, outgoing),
                activeReminders = listOf(Reminder(id = 7, threadId = 5, reminderText = "Answer Sam", triggerTimestamp = now + 3_600_000))
            )
        )
        compose.onNodeWithText("Dinner Friday?").assertIsDisplayed()
        compose.onNodeWithText("Let me check").assertIsDisplayed()
        compose.onNodeWithTag("reminder_banner").assertIsDisplayed()
    }

    @Test
    fun longPressOpensRemindMenuAndCreatesReminder() {
        val created = mutableListOf<Triple<Long, String, Long>>()
        render(
            ThreadUiState(threadId = 5, address = "+15550009999", messages = listOf(incoming)),
            onRemind = { m, text, at -> created += Triple(m.id, text, at) }
        )

        compose.onNodeWithTag("bubble_1").performTouchInput { longClick() }
        compose.onNodeWithTag("menu_remind").assertIsDisplayed().performClick()

        compose.onNodeWithText("Remind me about this later").assertIsDisplayed()
        compose.onNodeWithTag("reminder_text").performTextClearance()
        compose.onNodeWithTag("reminder_text").performTextInput("Say yes to dinner")
        compose.onNodeWithTag("reminder_next").performClick()
        compose.onNodeWithTag("picker_next").performClick()
        compose.onNodeWithTag("picker_confirm").performClick()

        assertEquals(1, created.size)
        assertEquals(1L, created[0].first)
        assertEquals("Say yes to dinner", created[0].second)
        assertTrue(created[0].third > now)
    }
}
