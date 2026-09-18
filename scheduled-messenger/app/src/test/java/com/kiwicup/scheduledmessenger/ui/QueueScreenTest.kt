package com.kiwicup.scheduledmessenger.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.MessageStatus
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import com.kiwicup.scheduledmessenger.data.local.entity.ScheduledMessage
import com.kiwicup.scheduledmessenger.ui.queue.QueueActions
import com.kiwicup.scheduledmessenger.ui.queue.QueueItem
import com.kiwicup.scheduledmessenger.ui.queue.QueueScreen
import com.kiwicup.scheduledmessenger.ui.queue.QueueUiState
import com.kiwicup.scheduledmessenger.ui.theme.ScheduledMessengerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    private val pending = ScheduledMessage(id = 1, recipientAddress = "+15550001111", messageBody = "Happy birthday!", targetTimestamp = now + hour, createdAt = now)
    private val failed = ScheduledMessage(id = 2, recipientAddress = "+15550002222", messageBody = "Old one", targetTimestamp = now - hour, status = MessageStatus.FAILED, failureReason = "No cellular service", createdAt = now)
    private val reminder = Reminder(id = 3, threadId = 9, reminderText = "Reply to Sam", triggerTimestamp = now + 2 * hour)

    private fun actions(
        onCancel: (Long) -> Unit = {},
        onDone: (Long) -> Unit = {},
        onClear: () -> Unit = {}
    ) = QueueActions(
        onCancelMessage = onCancel,
        onDeleteMessage = {},
        onEditMessage = { _, _, _, _ -> },
        onRescheduleMessage = { _, _ -> },
        onCompleteReminder = onDone,
        onDeleteReminder = {},
        onEditReminder = { _, _, _ -> },
        onClearHistory = onClear,
        onOpenThread = {}
    )

    private fun render(state: QueueUiState, actions: QueueActions = actions()) {
        compose.setContent {
            ScheduledMessengerTheme(dynamicColor = false) {
                QueueScreen(
                    state = state,
                    nowMillis = { now },
                    validateTarget = { null },
                    actions = actions,
                    onSnackbarShown = {},
                    onBack = {}
                )
            }
        }
    }

    @Test
    fun emptyQueueShowsHint() {
        render(QueueUiState())
        compose.onNodeWithTag("queue_empty").assertIsDisplayed()
    }

    @Test
    fun rendersUpcomingAndHistorySections() {
        render(
            QueueUiState(
                upcoming = listOf(QueueItem.Message(pending), QueueItem.Note(reminder)),
                history = listOf(QueueItem.Message(failed))
            )
        )
        compose.onNodeWithText("Upcoming").assertIsDisplayed()
        compose.onNodeWithText("Happy birthday!").assertIsDisplayed()
        compose.onNodeWithTag("clear_history").assertIsDisplayed()
        // Later rows sit below the fold on the test display; scroll the lazy list to them.
        compose.onNodeWithTag("queue_list").performScrollToNode(hasText("Reply to Sam"))
        compose.onNodeWithText("Reply to Sam").assertIsDisplayed()
        compose.onNodeWithTag("queue_list").performScrollToNode(hasTestTag("reschedule_2"))
        compose.onNodeWithText("History").assertExists()
        compose.onNodeWithText("No cellular service").assertIsDisplayed()
        compose.onNodeWithTag("reschedule_2").assertIsDisplayed()
    }

    @Test
    fun cancelAndDoneInvokeCallbacks() {
        val cancelled = mutableListOf<Long>()
        val done = mutableListOf<Long>()
        render(
            QueueUiState(upcoming = listOf(QueueItem.Message(pending), QueueItem.Note(reminder))),
            actions(onCancel = { cancelled += it }, onDone = { done += it })
        )
        compose.onNodeWithTag("cancel_1").performClick()
        compose.onNodeWithTag("done_reminder_3").performClick()
        assertEquals(listOf(1L), cancelled)
        assertEquals(listOf(3L), done)
    }

    @Test
    fun editOpensDialogPrefilledWithMessage() {
        render(QueueUiState(upcoming = listOf(QueueItem.Message(pending))))
        compose.onNodeWithTag("edit_1").performClick()
        compose.onNodeWithText("Edit scheduled message").assertIsDisplayed()
        compose.onNodeWithTag("edit_body").assertTextContains("Happy birthday!")
        compose.onNodeWithTag("edit_recipient").assertTextContains("+15550001111")
    }
}
