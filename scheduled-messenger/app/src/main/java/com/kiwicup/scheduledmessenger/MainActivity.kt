package com.kiwicup.scheduledmessenger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.kiwicup.scheduledmessenger.ui.theme.ThemeViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kiwicup.scheduledmessenger.notifications.ComposeRequest
import com.kiwicup.scheduledmessenger.notifications.DeepLinks
import com.kiwicup.scheduledmessenger.ui.navigation.AppNavHost
import com.kiwicup.scheduledmessenger.ui.permissions.PermissionGate
import com.kiwicup.scheduledmessenger.ui.theme.ScheduledMessengerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var deepLinkThreadId by mutableStateOf<Long?>(null)
    private var composeRequest by mutableStateOf<ComposeRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) consumeDeepLink(intent)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val settings by themeViewModel.settings.collectAsState()
            ScheduledMessengerTheme(settings = settings) {
                PermissionGate {
                    AppNavHost(
                        deepLinkThreadId = deepLinkThreadId,
                        onDeepLinkConsumed = { deepLinkThreadId = null },
                        composeRequest = composeRequest,
                        onComposeRequestConsumed = { composeRequest = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeDeepLink(intent)
    }

    private fun consumeDeepLink(intent: Intent?) {
        if (intent == null) return
        if (intent.action == DeepLinks.ACTION_OPEN_THREAD && intent.hasExtra(DeepLinks.EXTRA_THREAD_ID)) {
            deepLinkThreadId = intent.getLongExtra(DeepLinks.EXTRA_THREAD_ID, -1L).takeIf { it >= 0 }
            return
        }
        // "Send message" from Contacts, the dialer or a share sheet (sms:/smsto:/mms:/mmsto:).
        ComposeRequest.from(intent)?.let { composeRequest = it }
    }
}
