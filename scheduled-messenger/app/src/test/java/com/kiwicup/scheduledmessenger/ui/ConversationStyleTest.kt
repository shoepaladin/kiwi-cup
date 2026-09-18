package com.kiwicup.scheduledmessenger.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.local.entity.ConversationStyle
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import com.kiwicup.scheduledmessenger.ui.theme.ScheduledMessengerTheme
import com.kiwicup.scheduledmessenger.ui.thread.ThreadLook
import com.kiwicup.scheduledmessenger.ui.thread.ThreadScreen
import com.kiwicup.scheduledmessenger.ui.thread.ThreadStyleActions
import com.kiwicup.scheduledmessenger.ui.thread.ThreadUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationStyleTest {

    @get:Rule
    val compose = createComposeRule()

    private val msg = SmsMessage(id = 1, threadId = 5, address = "+15550009999", body = "hi", timestamp = 1, status = SmsStatus.RECEIVED)

    @Test
    fun wallpaperRendersAndStyleDialogDrivesCallbacks() {
        val picks = mutableListOf<String>()
        val bubbles = mutableListOf<Pair<Int?, Int?>>()
        val style = ConversationStyle("15550009999", wallpaperPath = "/tmp/x", wallpaperDimPercent = 40)
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        compose.setContent {
            ScheduledMessengerTheme(dynamicColor = false) {
                ThreadScreen(
                    state = ThreadUiState(
                        threadId = 5, address = "+15550009999", messages = listOf(msg),
                        look = ThreadLook(wallpaper = bitmap, wallpaperDimPercent = 40, style = style)
                    ),
                    nowMillis = { 0L },
                    onDraftChange = {}, onSendNow = {}, onSchedule = {}, validateTarget = { null },
                    onRemind = { _, _, _ -> }, onSnackbarShown = {}, onBack = {},
                    styleActions = ThreadStyleActions(
                        onBubbleColors = { i, o -> bubbles += i to o },
                        onPickWallpaper = { picks += "pick" },
                        onClearWallpaper = { picks += "clear" },
                        onDim = {},
                        onReset = { picks += "reset" }
                    )
                )
            }
        }
        compose.onNodeWithTag("wallpaper").assertIsDisplayed()
        compose.onNodeWithTag("thread_menu").performClick()
        compose.onNodeWithTag("menu_style").performClick()
        // The dialog body scrolls; bring each control into view before tapping it.
        compose.onNodeWithTag("conv_outgoing_row").performScrollTo()
        compose.onNodeWithTag("conv_outgoing_row").performScrollToNode(hasTestTag("conv_outgoing_Pink"))
        compose.onNodeWithTag("conv_outgoing_Pink").performClick()
        compose.onNodeWithTag("pick_wallpaper").performScrollTo().performClick()
        compose.onNodeWithTag("clear_wallpaper").performScrollTo().performClick()
        compose.onNodeWithTag("reset_style").performClick()

        assertEquals(listOf("pick", "clear", "reset"), picks)
        assertEquals(listOf<Pair<Int?, Int?>>(null to 0xFFD81B60.toInt()), bubbles)
    }
}
