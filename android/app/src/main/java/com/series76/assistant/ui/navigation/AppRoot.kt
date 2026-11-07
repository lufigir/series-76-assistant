package com.series76.assistant.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.series76.assistant.di.AppContainer
import com.series76.assistant.ui.chat.ChatRoute
import com.series76.assistant.ui.chat.ChatViewModel
import com.series76.assistant.ui.conversations.ConversationsRoute
import com.series76.assistant.ui.conversations.ConversationsViewModel
import com.series76.assistant.ui.debug.DebugRoute
import com.series76.assistant.ui.debug.DebugViewModel
import com.series76.assistant.ui.login.LoginRoute
import com.series76.assistant.ui.login.LoginViewModel
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
private data object ConversationsKey : NavKey

@Serializable
private data class ChatKey(val sessionId: String?) : NavKey

@Serializable
private data class DebugKey(val messageId: String) : NavKey

private enum class AuthState { LOADING, SIGNED_OUT, SIGNED_IN }

// A short shift and a fade: enough to show which way the screen went, without making anyone wait for it.
private const val TRANSITION_MILLIS = 200
private const val SHIFT_DIVISOR = 12

private fun forward(): ContentTransform =
    (slideInHorizontally(tween(TRANSITION_MILLIS)) { it / SHIFT_DIVISOR } + fadeIn(tween(TRANSITION_MILLIS))) togetherWith
        fadeOut(tween(TRANSITION_MILLIS / 2))

private fun backward(): ContentTransform =
    fadeIn(tween(TRANSITION_MILLIS)) togetherWith
        (slideOutHorizontally(tween(TRANSITION_MILLIS)) { it / SHIFT_DIVISOR } + fadeOut(tween(TRANSITION_MILLIS / 2)))

/** The signed-in screens live in their own composition, so signing out also discards their back stack. */
@Composable
fun AppRoot(container: AppContainer) {
    val authFlow =
        remember(container) {
            container.authRepository.token.map { if (it == null) AuthState.SIGNED_OUT else AuthState.SIGNED_IN }
        }
    val auth by authFlow.collectAsStateWithLifecycle(initialValue = AuthState.LOADING)

    Surface(modifier = Modifier.fillMaxSize()) {
        when (auth) {
            // The token loads in milliseconds, so a spinner here would only flash.
            AuthState.LOADING -> Unit
            AuthState.SIGNED_OUT ->
                LoginRoute(
                    viewModel(factory = viewModelFactory { initializer { LoginViewModel(container.authRepository) } }),
                )
            AuthState.SIGNED_IN -> SignedInNavigation(container)
        }
    }
}

@Composable
private fun SignedInNavigation(container: AppContainer) {
    val backStack = rememberNavBackStack(ConversationsKey)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        transitionSpec = { forward() },
        popTransitionSpec = { backward() },
        predictivePopTransitionSpec = { backward() },
        entryProvider =
            entryProvider {
                entry<ConversationsKey> {
                    ConversationsRoute(
                        viewModel =
                            viewModel(
                                factory =
                                    viewModelFactory {
                                        initializer {
                                            ConversationsViewModel(
                                                container.conversationRepository,
                                                container.authRepository,
                                            )
                                        }
                                    },
                            ),
                        onOpen = { backStack.add(ChatKey(it)) },
                        onNew = { backStack.add(ChatKey(null)) },
                    )
                }
                entry<ChatKey> { key ->
                    ChatRoute(
                        viewModel =
                            viewModel(
                                factory =
                                    viewModelFactory {
                                        initializer {
                                            ChatViewModel(
                                                createSavedStateHandle(),
                                                key.sessionId,
                                                container.conversationRepository,
                                                container.voiceRepository,
                                                container.catalogRepository,
                                            )
                                        }
                                    },
                            ),
                        onBack = { backStack.removeLastOrNull() },
                        onOpenDebug = { backStack.add(DebugKey(it)) },
                    )
                }
                entry<DebugKey> { key ->
                    DebugRoute(
                        viewModel =
                            viewModel(
                                factory =
                                    viewModelFactory {
                                        initializer {
                                            DebugViewModel(key.messageId, container.conversationRepository)
                                        }
                                    },
                            ),
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
    )
}
