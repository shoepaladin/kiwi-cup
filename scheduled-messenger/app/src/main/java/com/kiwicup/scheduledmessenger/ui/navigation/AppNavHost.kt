package com.kiwicup.scheduledmessenger.ui.navigation

import androidx.compose.runtime.Composable
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
import com.kiwicup.scheduledmessenger.ui.thread.ThreadScreen
import com.kiwicup.scheduledmessenger.ui.thread.ThreadViewModel

object Routes {
    const val CONVERSATIONS = "conversations"
    const val COMPOSE = "compose"
    const val QUEUE = "queue"
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
    navController: NavHostController = rememberNavController()
) {
    LaunchedEffect(deepLinkThreadId) {
        if (deepLinkThreadId != null) {
            navController.navigate(Routes.thread(deepLinkThreadId)) { launchSingleTop = true }
            onDeepLinkConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Routes.CONVERSATIONS) {
        composable(Routes.CONVERSATIONS) {
            val vm: ConversationsViewModel = hiltViewModel()
            val threads by vm.threads.collectAsState()
            ConversationsScreen(
                threads = threads,
                onOpenThread = { navController.navigate(Routes.thread(it)) },
                onNewMessage = { navController.navigate(Routes.COMPOSE) },
                onOpenQueue = { navController.navigate(Routes.QUEUE) }
            )
        }
        composable(
            route = Routes.THREAD,
            arguments = listOf(navArgument("threadId") { type = NavType.LongType })
        ) {
            val vm: ThreadViewModel = hiltViewModel()
            val state by vm.state.collectAsState()
            ThreadScreen(
                state = state,
                nowMillis = vm::now,
                onDraftChange = vm::onDraftChange,
                onSendNow = vm::sendNow,
                onSchedule = vm::scheduleAt,
                validateTarget = vm::validateTarget,
                onRemind = vm::remind,
                onSnackbarShown = vm::snackbarShown,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.COMPOSE) {
            val vm: ComposeViewModel = hiltViewModel()
            val state by vm.state.collectAsState()
            ComposeScreen(
                state = state,
                nowMillis = vm::now,
                onRecipientChange = vm::onRecipientChange,
                onBodyChange = vm::onBodyChange,
                onSendNow = vm::sendNow,
                onSchedule = vm::scheduleAt,
                validateTarget = vm::validateTarget,
                onDone = {
                    navController.navigate(Routes.QUEUE) {
                        popUpTo(Routes.CONVERSATIONS)
                    }
                },
                onBack = { navController.popBackStack() }
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
