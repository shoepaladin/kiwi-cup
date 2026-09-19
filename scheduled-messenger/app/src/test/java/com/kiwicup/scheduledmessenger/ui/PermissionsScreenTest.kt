package com.kiwicup.scheduledmessenger.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.PermissionGateState
import com.kiwicup.scheduledmessenger.core.RestrictedPermissionHelp
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

    private fun show(
        state: PermissionGateState,
        missing: List<String> = listOf(Manifest.permission.READ_SMS),
        onRequest: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onRecheck: () -> Unit = {}
    ) {
        compose.setContent {
            ScheduledMessengerTheme(dynamicColor = false) {
                PermissionsScreen(
                    missing = missing,
                    state = state,
                    onRequest = onRequest,
                    onOpenSettings = onOpenSettings,
                    onRecheck = onRecheck
                )
            }
        }
    }

    @Test
    fun listsMissingPermissionsAndRequestsOnTap() {
        var requests = 0
        show(
            state = PermissionGateState.ASK,
            missing = listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS),
            onRequest = { requests++ }
        )
        compose.onNodeWithTag("missing_${Manifest.permission.READ_SMS}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("missing_${Manifest.permission.SEND_SMS}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("grant_button").performScrollTo().performClick()
        assertEquals(1, requests)
    }

    @Test
    fun retryableDenialStillOffersTheGrantButton() {
        var requests = 0
        show(state = PermissionGateState.EXPLAIN_AND_ASK, onRequest = { requests++ })
        compose.onNodeWithTag("grant_button").performScrollTo().performClick()
        assertEquals(1, requests)
    }

    @Test
    fun permanentDenialOffersSettings() {
        var opened = 0
        show(state = PermissionGateState.OPEN_SETTINGS, onOpenSettings = { opened++ })
        compose.onNodeWithTag("denied_hint").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("open_settings").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun restrictedStateExplainsTheSideloadBlockInsteadOfBlamingTheUser() {
        show(state = PermissionGateState.RESTRICTED)
        compose.onNodeWithTag("restricted_hint").performScrollTo().assertIsDisplayed()
        // The ordinary "you turned these down" copy would be wrong and confusing here.
        compose.onNodeWithTag("denied_hint").assertDoesNotExist()
    }

    @Test
    fun restrictedStateListsEveryStepIncludingTheOverflowMenuItem() {
        show(state = PermissionGateState.RESTRICTED)
        RestrictedPermissionHelp.steps.indices.forEach { index ->
            compose.onNodeWithTag("restricted_step_$index").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText(RestrictedPermissionHelp.OVERFLOW_ITEM, substring = true).assertExists()
    }

    @Test
    fun restrictedStateSendsTheUserToAppInfo() {
        var opened = 0
        show(state = PermissionGateState.RESTRICTED, onOpenSettings = { opened++ })
        compose.onNodeWithTag("open_app_info").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun restrictedStateNeverOffersAGrantButtonThatCannotWork() {
        // Re-requesting a restricted permission is refused instantly, so offering the button
        // would just teach the user that the app is broken.
        show(state = PermissionGateState.RESTRICTED)
        compose.onNodeWithTag("grant_button").assertDoesNotExist()
    }

    @Test
    fun restrictedStateOffersARecheckAfterTheUserFixesIt() {
        var rechecks = 0
        show(state = PermissionGateState.RESTRICTED, onRecheck = { rechecks++ })
        compose.onNodeWithTag("restricted_recheck").performScrollTo().performClick()
        assertEquals(1, rechecks)
    }

    @Test
    fun restrictedStateKeepsAnEscapeHatchForAMisjudgedInstaller() {
        // The installer check is a heuristic and the restriction can be cleared mid-session, so
        // the user must never be trapped on this screen with no way to fire the real prompt.
        var requests = 0
        show(state = PermissionGateState.RESTRICTED, onRequest = { requests++ })
        compose.onNodeWithTag("restricted_try_prompt").performScrollTo().performClick()
        assertEquals(1, requests)
    }

    @Test
    fun restrictedStateMentionsTheAdbAlternative() {
        show(state = PermissionGateState.RESTRICTED)
        compose.onNodeWithTag("adb_hint").performScrollTo().assertIsDisplayed()
    }
}
