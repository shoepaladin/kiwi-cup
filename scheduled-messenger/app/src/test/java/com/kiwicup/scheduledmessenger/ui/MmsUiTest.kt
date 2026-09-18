package com.kiwicup.scheduledmessenger.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.Attachment
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import com.kiwicup.scheduledmessenger.ui.components.MessageInputBar
import com.kiwicup.scheduledmessenger.ui.compose.ComposeScreen
import com.kiwicup.scheduledmessenger.ui.compose.ComposeUiState
import com.kiwicup.scheduledmessenger.ui.conversations.ConversationsScreen
import com.kiwicup.scheduledmessenger.ui.theme.ScheduledMessengerTheme
import com.kiwicup.scheduledmessenger.ui.thread.ThreadScreen
import com.kiwicup.scheduledmessenger.ui.thread.ThreadUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MmsUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun defaultBannerShowsWhenNotDefaultAndRequests() {
        var requests = 0
        compose.setContent {
            ScheduledMessengerTheme(dynamicColor = false) {
                ConversationsScreen(threads = emptyList(), onOpenThread = {}, onNewMessage = {}, onOpenQueue = {},
                    isDefaultSmsApp = false, onRequestDefault = { requests++ })
            }
        }
        compose.onNodeWithTag("default_banner").assertIsDisplayed()
        compose.onNodeWithTag("request_default").performClick()
        assertEquals(1, requests)
    }

    @Test
    fun attachmentAloneEnablesSendAndShowsMmsHint() {
        var attachTaps = 0
        val removed = mutableListOf<Attachment>()
        val picture = Attachment("file:///tmp/a.jpg", "image/jpeg")
        compose.setContent {
            ScheduledMessengerTheme(dynamicColor = false) {
                MessageInputBar(
                    text = "", onTextChange = {}, onSendNow = {}, onSchedule = {}, nowMillis = { 0L }, validateTarget = { null },
                    attachments = listOf(picture), onAttach = { attachTaps++ }, onRemoveAttachment = { removed += it }
                )
            }
        }
        compose.onNodeWithTag("send_button").assertIsEnabled()
        compose.onNodeWithTag("mms_hint").assertIsDisplayed()
        compose.onNodeWithTag("pending_attachment_0").assertIsDisplayed()
        compose.onNodeWithTag("attach_button").performClick()
        compose.onNodeWithTag("remove_attachment_0").performClick()
        assertEquals(1, attachTaps)
        assertEquals(listOf(picture), removed)
    }

    @Test
    fun groupThreadShowsParticipantsSenderAndPictures() {
        val group = SmsMessage(
            id = 3, threadId = 9, address = "+15550001111", body = "", timestamp = 1, status = SmsStatus.RECEIVED,
            isMms = true, attachments = "content://mms/part/71|image/jpeg", recipients = "+15550001111,+15550002222"
        )
        compose.setContent {
            ScheduledMessengerTheme(dynamicColor = false) {
                ThreadScreen(
                    state = ThreadUiState(threadId = 9, address = "+15550001111", messages = listOf(group), participants = listOf("+15550001111", "+15550002222")),
                    nowMillis = { 0L }, onDraftChange = {}, onSendNow = {}, onSchedule = {}, validateTarget = { null },
                    onRemind = { _, _, _ -> }, onSnackbarShown = {}, onBack = {}
                )
            }
        }
        compose.onNodeWithText("+15550001111, +15550002222").assertIsDisplayed()
        compose.onNodeWithTag("sender_3").assertIsDisplayed()
        compose.onNodeWithTag("attachment_3_0").assertIsDisplayed()
    }

    @Test
    fun composeShowsGroupHintForSeveralRecipients() {
        compose.setContent {
            ScheduledMessengerTheme(dynamicColor = false) {
                ComposeScreen(
                    state = ComposeUiState(recipient = "+15550001111, +15550002222"),
                    nowMillis = { 0L }, onRecipientChange = {}, onBodyChange = {}, onSendNow = {}, onSchedule = {},
                    validateTarget = { null }, onDone = {}, onBack = {}
                )
            }
        }
        compose.onNodeWithTag("group_hint").assertIsDisplayed()
    }
}
