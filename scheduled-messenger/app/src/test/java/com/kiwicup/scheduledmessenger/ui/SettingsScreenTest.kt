package com.kiwicup.scheduledmessenger.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.ThemeMode
import com.kiwicup.scheduledmessenger.data.settings.AppSettings
import com.kiwicup.scheduledmessenger.ui.settings.SettingsScreen
import com.kiwicup.scheduledmessenger.ui.theme.ScheduledMessengerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun themeChipsSeedSwatchAndHexReportChanges() {
        val modes = mutableListOf<ThemeMode>()
        val seeds = mutableListOf<Int>()
        val bubbles = mutableListOf<Pair<Int?, Int?>>()
        compose.setContent {
            ScheduledMessengerTheme(dynamicColor = false) {
                SettingsScreen(
                    settings = AppSettings(),
                    onThemeMode = { modes += it },
                    onDynamicColor = {},
                    onSeedColor = { seeds += it },
                    onBubbleColors = { i, o -> bubbles += i to o },
                    onReset = {},
                    onBack = {}
                )
            }
        }
        compose.onNodeWithTag("theme_DARK").performClick()
        compose.onNodeWithTag("seed_row").performScrollToNode(hasTestTag("seed_Indigo"))
        compose.onNodeWithTag("seed_Indigo").performClick()
        compose.onNodeWithTag("seed_hex").performScrollTo().performTextClearance()
        compose.onNodeWithTag("seed_hex").performTextInput("#ABCDEF")
        compose.onNodeWithTag("incoming_row").performScrollTo()
        compose.onNodeWithTag("incoming_row").performScrollToNode(hasTestTag("incoming_Green"))
        compose.onNodeWithTag("incoming_Green").performClick()

        assertEquals(listOf(ThemeMode.DARK), modes)
        assertEquals(listOf(0xFF3F51B5.toInt(), 0xFFABCDEF.toInt()), seeds)
        assertEquals(listOf<Pair<Int?, Int?>>(0xFF2E7D32.toInt() to null), bubbles)
        compose.onNodeWithTag("reset_settings").assertIsDisplayed()
    }
}
