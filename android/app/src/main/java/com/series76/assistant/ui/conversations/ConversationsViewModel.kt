package com.series76.assistant.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.series76.assistant.data.auth.AuthRepository
import com.series76.assistant.data.catalog.CatalogRepository
import com.series76.assistant.data.conversation.ConversationRepository
import com.series76.assistant.data.remote.ApiResult
import com.series76.assistant.data.remote.CatalogEntry
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
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
    // Feeds the part number builder on the search box; empty hides the builder.
    val catalog: List<CatalogEntry> = emptyList(),
)

class ConversationsViewModel(
    private val conversations: ConversationRepository,
    private val auth: AuthRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            (catalog.catalog() as? ApiResult.Success)?.let { result -> _uiState.update { it.copy(catalog = result.value) } }
        }
    }

    /** Reloads the first page. Keeps what is on screen while it refreshes, and if the refresh fails. */
    fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = conversations.sessions()) {
                is ApiResult.Success ->
                    _uiState.value =
                        ConversationsUiState(
                            result.value.items,
                            loading = false,
                            nextCursor = result.value.nextCursor,
                            catalog = _uiState.value.catalog,
                        )
                is ApiResult.Failure -> _uiState.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    /** Appends the next page. The list calls it as its end comes into view, so it ignores repeats while one loads. */
    fun loadMore() {
        val state = _uiState.value
        val cursor = state.nextCursor ?: return
        if (state.loading || state.loadingMore) return
        _uiState.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            when (val result = conversations.sessions(cursor)) {
                is ApiResult.Success ->
                    _uiState.update {
                        it.copy(
                            sessions = it.sessions.orEmpty() + result.value.items,
                            nextCursor = result.value.nextCursor,
                            loadingMore = false,
                        )
                    }
                is ApiResult.Failure -> _uiState.update { it.copy(loadingMore = false, error = result.message) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }
}
