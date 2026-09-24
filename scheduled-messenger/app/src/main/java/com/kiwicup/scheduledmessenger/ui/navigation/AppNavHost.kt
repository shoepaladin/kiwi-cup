package com.kiwicup.scheduledmessenger.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.kiwicup.scheduledmessenger.core.SendOutcome
import com.kiwicup.scheduledmessenger.notifications.ComposeRequest
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kiwicup.scheduledmessenger.data.system.DefaultSmsApp
import com.kiwicup.scheduledmessenger.work.ExactAlarms
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kiwicup.scheduledmessenger.ui.compose.ComposeScreen
import com.kiwicup.scheduledmessenger.ui.compose.ComposeViewModel
import com.kiwicup.scheduledmessenger.ui.conversations.ConversationsScreen
import com.kiwicup.scheduledmessenger.ui.conversations.ConversationsViewModel
import com.kiwicup.scheduledmessenger.ui.queue.QueueActions
import com.kiwicup.scheduledmessenger.ui.queue.QueueScreen
import com.kiwicup.scheduledmessenger.ui.queue.QueueViewModel
import com.kiwicup.scheduledmessenger.ui.settings.SettingsScreen
import com.kiwicup.scheduledmessenger.ui.settings.SettingsViewModel
import com.kiwicup.scheduledmessenger.ui.thread.ThreadScreen
import com.kiwicup.scheduledmessenger.ui.thread.ThreadStyleActions
import com.kiwicup.scheduledmessenger.ui.thread.ThreadViewModel

object Routes {
    const val CONVERSATIONS = "conversations"
    const val COMPOSE = "compose?to={to}&body={body}"
    fun compose(to: String = "", body: String = "") = "compose?to=${Uri.encode(to)}&body=${Uri.encode(body)}"
    const val QUEUE = "queue"
    const val SETTINGS = "settings"
    const val THREAD = "thread/{threadId}"
    fun thread(threadId: Long) = "thread/$threadId"
}

/**
 * @param deepLinkThreadId thread requested by a notification tap; consumed once via [onDeepLinkConsumed].
 */
@Composable
fun AppNavHost(
    deepLinkThreadId: Long?,
    onDeepLinkConsumed: () -> Unit,
    composeRequest: ComposeRequest? = null,
    onComposeRequestConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    LaunchedEffect(deepLinkThreadId) {
        if (deepLinkThreadId != null) {
            navController.navigate(Routes.thread(deepLinkThreadId)) { launchSingleTop = true }
            onDeepLinkConsumed()
        }
    }
    LaunchedEffect(composeRequest) {
        if (composeRequest != null) {
            navController.navigate(Routes.compose(composeRequest.recipients, composeRequest.body)) { launchSingleTop = true }
            onComposeRequestConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Routes.CONVERSATIONS) {
        composable(Routes.CONVERSATIONS) {
            val vm: ConversationsViewModel = hiltViewModel()
            val threads by vm.threads.collectAsState()
            val context = LocalContext.current
            var isDefault by remember { mutableStateOf(DefaultSmsApp.isDefault(context)) }
            val requestDefault = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                isDefault = DefaultSmsApp.isDefault(context)
            }
            // The user may change the default app in system settings; re-check whenever we come back.
            LifecycleResumeEffect(Unit) {
                isDefault = DefaultSmsApp.isDefault(context)
                onPauseOrDispose { }
            }
            ConversationsScreen(
                threads = threads,
                onOpenThread = { navController.navigate(Routes.thread(it)) },
                onNewMessage = { navController.navigate(Routes.compose()) },
                onOpenQueue = { navController.navigate(Routes.QUEUE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                isDefaultSmsApp = isDefault,
                onRequestDefault = { DefaultSmsApp.requestIntent(context)?.let(requestDefault::launch) }
            )
        }
        composable(
            route = Routes.THREAD,
            arguments = listOf(navArgument("threadId") { type = NavType.LongType })
        ) {
            val vm: ThreadViewModel = hiltViewModel()
            val state by vm.state.collectAsState()
            // "Looking at the conversation" means resumed on screen, not merely on the back stack.
            LifecycleResumeEffect(vm) {
                vm.onScreenResumed()
                onPauseOrDispose { vm.onScreenPaused() }
            }
            val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) vm.setWallpaper(uri)
            }
            val pickAttachment = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) vm.attach(uri)
            }
            ThreadScreen(
                state = state,
                nowMillis = vm::now,
                onDraftChange = vm::onDraftChange,
                onSendNow = vm::sendNow,
                onSchedule = vm::scheduleAt,
                validateTarget = vm::validateTarget,
                onRemind = vm::remind,
                onSnackbarShown = vm::snackbarShown,
                onBack = { navController.popBackStack() },
                styleActions = ThreadStyleActions(
                    onBubbleColors = vm::setBubbleColors,
                    onPickWallpaper = {
                        pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onClearWallpaper = vm::clearWallpaper,
                    onDim = vm::setWallpaperDim,
                    onReset = vm::resetStyle
                ),
                onAttach = { pickAttachment.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                onRemoveAttachment = vm::removeAttachment,
                onToggleRead = vm::toggleRead
            )
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = hiltViewModel()
            val settings by vm.settings.collectAsState()
            val context = LocalContext.current
            var exactAllowed by remember { mutableStateOf(vm.exactAlarmsAllowed()) }
            LifecycleResumeEffect(Unit) {
                exactAllowed = vm.exactAlarmsAllowed()
                onPauseOrDispose { }
            }
            SettingsScreen(
                settings = settings,
                onThemeMode = vm::setThemeMode,
                onDynamicColor = vm::setDynamicColor,
                onSeedColor = vm::setSeedColor,
                onBubbleColors = vm::setBubbleColors,
                onReset = vm::reset,
                onBack = { navController.popBackStack() },
                exactAlarmsAllowed = exactAllowed,
                onOpenExactAlarmSettings = ExactAlarms.settingsIntent(context)?.let { intent -> { context.startActivity(intent) } }
            )
        }
        composable(
            route = Routes.COMPOSE,
            arguments = listOf(
                navArgument("to") { type = NavType.StringType; defaultValue = "" },
                navArgument("body") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            val vm: ComposeViewModel = hiltViewModel()
            val state by vm.state.collectAsState()
            val pickAttachment = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) vm.attach(uri)
            }
            ComposeScreen(
                state = state,
                nowMillis = vm::now,
                onRecipientQueryChange = vm::onRecipientQueryChange,
                onPickSuggestion = vm::onPickSuggestion,
                onRemoveChip = vm::onRemoveChip,
                onBodyChange = vm::onBodyChange,
                onSendNow = vm::sendNow,
                onSchedule = vm::scheduleAt,
                validateTarget = vm::validateTarget,
                onDone = { outcome, threadId ->
                    when (outcome) {
                        // Sending leaves you in the conversation you just sent to, which is what
                        // both QKSMS and Fossify do — neither returns you to the conversation
                        // list. A group message goes out as MMS and its thread id is only known
                        // once imported, so that one still falls back to the list.
                        SendOutcome.SENT_NOW -> if (threadId != null) {
                            navController.navigate(Routes.thread(threadId)) {
                                popUpTo(Routes.CONVERSATIONS)
                            }
                        } else {
                            navController.popBackStack(Routes.CONVERSATIONS, inclusive = false)
                        }

                        SendOutcome.SCHEDULED ->
                            navController.navigate(Routes.QUEUE) { popUpTo(Routes.CONVERSATIONS) }
                    }
                },
                onBack = { navController.popBackStack() },
                onAttach = { pickAttachment.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                onRemoveAttachment = vm::removeAttachment,
                isDefaultSmsApp = DefaultSmsApp.isDefault(LocalContext.current)
            )
        }
        composable(Routes.QUEUE) {
            val vm: QueueViewModel = hiltViewModel()
            val state by vm.state.collectAsState()
            QueueScreen(
                state = state,
                nowMillis = vm::now,
                validateTarget = vm::validateTargetOnly,
                actions = QueueActions(
                    onCancelMessage = vm::cancelMessage,
                    onDeleteMessage = vm::deleteMessage,
                    onEditMessage = vm::editMessage,
                    onRescheduleMessage = vm::rescheduleMessage,
                    onCompleteReminder = vm::completeReminder,
                    onDeleteReminder = vm::deleteReminder,
                    onEditReminder = vm::editReminder,
                    onClearHistory = vm::clearHistory,
                    onOpenThread = { navController.navigate(Routes.thread(it)) }
                ),
                onSnackbarShown = vm::snackbarShown,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
