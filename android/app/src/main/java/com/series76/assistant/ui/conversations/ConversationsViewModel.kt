package com.series76.assistant.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.series76.assistant.data.auth.AuthRepository
import com.series76.assistant.data.conversation.ConversationRepository
import com.series76.assistant.data.remote.ApiResult
import com.series76.assistant.data.remote.SessionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val sessions: List<SessionSummary>? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class ConversationsViewModel(
    private val conversations: ConversationRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    /** Keeps what is on screen while it refreshes, and if the refresh fails. */
    fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = conversations.sessions()) {
                is ApiResult.Success -> _uiState.value = ConversationsUiState(result.value, loading = false)
                is ApiResult.Failure -> _uiState.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }
}
