package com.kiwicup.scheduledmessenger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.ui.components.MessageInputBar
import com.kiwicup.scheduledmessenger.ui.theme.ScheduledMessengerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageInputBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 1_700_000_000_000L

    private fun render(onSendNow: () -> Unit = {}, onSchedule: (Long) -> Unit = {}, validate: (Long) -> String? = { null }) {
        compose.setContent {
            var text by remember { mutableStateOf("") }
            ScheduledMessengerTheme(dynamicColor = false) {
                MessageInputBar(
                    text = text,
                    onTextChange = { text = it },
                    onSendNow = onSendNow,
                    onSchedule = onSchedule,
                    nowMillis = { now },
                    validateTarget = validate
                )
            }
        }
    }

    @Test
    fun buttonsDisabledUntilTextIsTyped() {
        render()
        compose.onNodeWithTag("send_button").assertIsNotEnabled()
        compose.onNodeWithTag("schedule_button").assertIsNotEnabled()
        compose.onNodeWithTag("message_input").performTextInput("hello")
        compose.onNodeWithTag("send_button").assertIsEnabled()
        compose.onNodeWithTag("schedule_button").assertIsEnabled()
        compose.onNodeWithTag("segment_counter").assertIsDisplayed()
    }

    @Test
    fun sendNowInvokesCallback() {
        var sent = 0
        render(onSendNow = { sent++ })
        compose.onNodeWithTag("message_input").performTextInput("hello")
        compose.onNodeWithTag("send_button").performClick()
        assertEquals(1, sent)
    }

    @Test
    fun scheduleWalksThroughDateAndTimeAndEmitsFutureTimestamp() {
        val scheduled = mutableListOf<Long>()
        render(onSchedule = { scheduled += it })
        compose.onNodeWithTag("message_input").performTextInput("later")
        compose.onNodeWithTag("schedule_button").performClick()

        compose.onNodeWithTag("picker_next").assertIsDisplayed().performClick()
        compose.onNodeWithText("Pick a time").assertIsDisplayed()
        compose.onNodeWithTag("picker_confirm").performClick()

        assertEquals(1, scheduled.size)
        // Default selection is one hour ahead, rounded to the minute.
        assertTrue(scheduled[0] > now)
        assertTrue(scheduled[0] - now <= 61L * 60L * 1000L)
    }

    @Test
    fun validationErrorKeepsDialogOpen() {
        val scheduled = mutableListOf<Long>()
        render(onSchedule = { scheduled += it }, validate = { "Pick a time in the future" })
        compose.onNodeWithTag("message_input").performTextInput("later")
        compose.onNodeWithTag("schedule_button").performClick()
        compose.onNodeWithTag("picker_next").performClick()
        compose.onNodeWithTag("picker_confirm").performClick()

        // The clock dialog is taller than the test display; the error line exists but may sit below the fold.
        compose.onNodeWithTag("picker_error").assertExists()
        compose.onNodeWithText("Pick a time").assertExists()
        assertTrue(scheduled.isEmpty())
    }
}
