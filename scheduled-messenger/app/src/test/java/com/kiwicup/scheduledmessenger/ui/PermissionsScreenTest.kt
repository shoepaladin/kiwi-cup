package com.kiwicup.scheduledmessenger.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.ui.permissions.PermissionsScreen
import com.kiwicup.scheduledmessenger.ui.theme.ScheduledMessengerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun listsMissingPermissionsAndRequestsOnTap() {
        var requests = 0
        compose.setContent {
            ScheduledMessengerTheme(dynamicColor = false) {
                PermissionsScreen(
                    missing = listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS),
                    permanentlyDenied = false,
                    onRequest = { requests++ },
                    onOpenSettings = {}
                )
            }
        }
        compose.onNodeWithTag("missing_${Manifest.permission.READ_SMS}").assertIsDisplayed()
        compose.onNodeWithTag("missing_${Manifest.permission.SEND_SMS}").assertIsDisplayed()
        compose.onNodeWithTag("grant_button").performClick()
        assertEquals(1, requests)
    }

    @Test
    fun permanentDenialOffersSettings() {
        var opened = 0
        compose.setContent {
            ScheduledMessengerTheme(dynamicColor = false) {
                PermissionsScreen(
                    missing = listOf(Manifest.permission.READ_SMS),
                    permanentlyDenied = true,
                    onRequest = {},
                    onOpenSettings = { opened++ }
                )
            }
        }
        compose.onNodeWithTag("denied_hint").assertIsDisplayed()
        compose.onNodeWithTag("open_settings").performClick()
        assertEquals(1, opened)
    }
}
