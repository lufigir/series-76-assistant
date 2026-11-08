package com.series76.assistant.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.series76.assistant.data.catalog.CatalogRepository
import com.series76.assistant.data.conversation.ConversationRepository
import com.series76.assistant.data.remote.ActuatorData
import com.series76.assistant.data.remote.ApiResult
import com.series76.assistant.data.remote.Candidate
import com.series76.assistant.data.remote.CatalogEntry
import com.series76.assistant.data.remote.ConversationResponse
import com.series76.assistant.data.remote.MAX_QUERY_LENGTH
import com.series76.assistant.data.remote.MessageView
import com.series76.assistant.data.remote.Requirements
import com.series76.assistant.data.voice.VoiceRepository
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    val outcome: String? = null,
    val products: List<ActuatorData> = emptyList(),
    val alternatives: List<Candidate> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val matches: List<String> = emptyList(),
    val followUps: List<String> = emptyList(),
    val requirements: Requirements? = null,
    val error: String? = null,
    val createdAt: String? = null,
)

enum class VoiceState { IDLE, RECORDING, TRANSCRIBING }

data class ChatUiState(
    // The backend's name for the conversation; null until the first answer of a new one.
    val title: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val requirements: Requirements? = null,
    val loadingHistory: Boolean = false,
    val historyError: String? = null,
    val sending: Boolean = false,
    val voice: VoiceState = VoiceState.IDLE,
    val voiceError: String? = null,
    val olderCursor: String? = null,
    val loadingOlder: Boolean = false,
    // Empty until it loads, or if it fails: the part number aids then stay hidden and typing works as before.
    val catalog: List<CatalogEntry> = emptyList(),
)

class ChatViewModel(
    private val savedState: SavedStateHandle,
    openedSessionId: String?,
    openedTitle: String?,
    private val conversations: ConversationRepository,
    private val voice: VoiceRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    // A new chat learns its id from the first answer, and it has to survive the process dying after that.
    private var sessionId: String?
        get() = savedState[SESSION_ID]
        set(value) {
            savedState[SESSION_ID] = value
        }

    // Compose state, not part of the flow: a text field fed through a flow updates a frame late, and the Compose
    // text guidance warns that this loses characters typed in between.
    var draft by mutableStateOf(savedState[DRAFT] ?: "")
        private set

    private val _uiState = MutableStateFlow(ChatUiState(title = savedState[TITLE] ?: openedTitle))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        if (sessionId == null) sessionId = openedSessionId
        if (sessionId != null) loadHistory()
        viewModelScope.launch {
            (catalog.catalog() as? ApiResult.Success)?.let { result -> _uiState.update { it.copy(catalog = result.value) } }
        }
    }

    fun loadHistory() {
        val id = sessionId ?: return
        _uiState.update { it.copy(loadingHistory = true, historyError = null) }
        viewModelScope.launch {
            when (val result = conversations.messages(id)) {
                is ApiResult.Success ->
                    _uiState.update { state ->
                        state.copy(
                            loadingHistory = false,
                            messages = result.value.items.map(::toChatMessage),
                            // The latest page always holds the latest answer, which carries the current requirements.
                            requirements = result.value.items.lastOrNull { it.requirements != null }?.requirements,
                            olderCursor = result.value.nextCursor,
                        )
                    }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(loadingHistory = false, historyError = result.message) }
            }
        }
    }

    /** Puts the page before the oldest message on top. The list calls it as its top comes into view. */
    fun loadOlder() {
        val id = sessionId ?: return
        val state = _uiState.value
        val cursor = state.olderCursor ?: return
        if (state.loadingHistory || state.loadingOlder) return
        _uiState.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            when (val result = conversations.messages(id, cursor)) {
                is ApiResult.Success ->
                    _uiState.update { state ->
                        state.copy(
                            loadingOlder = false,
                            messages = result.value.items.map(::toChatMessage) + state.messages,
                            olderCursor = result.value.nextCursor,
                        )
                    }
                // The cursor stays, so scrolling back to the top tries again.
                is ApiResult.Failure -> _uiState.update { it.copy(loadingOlder = false) }
            }
        }
    }

    fun updateDraft(text: String) {
        // A paste or a transcript added to the draft can pass the limit, so the cut happens here and not in the field.
        val capped = text.take(MAX_QUERY_LENGTH)
        savedState[DRAFT] = capped
        draft = capped
        _uiState.update { it.copy(voiceError = null) }
    }

    fun sendDraft() {
        val query = draft.trim()
        if (query.isEmpty() || _uiState.value.sending) return
        updateDraft("")
        send(ChatMessage(id = UUID.randomUUID().toString(), fromUser = true, text = query, createdAt = now()))
    }

    fun sendSuggestion(text: String) {
        if (_uiState.value.sending) return
        send(ChatMessage(id = UUID.randomUUID().toString(), fromUser = true, text = text, createdAt = now()))
    }

    /** Only ever runs on a tap: repeating a POST by itself could ask the model twice for one question. */
    fun retry(message: ChatMessage) {
        if (_uiState.value.sending) return
        _uiState.update { state -> state.copy(messages = state.messages - message) }
        send(message.copy(error = null))
    }

    private fun send(message: ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + message, sending = true) }
        viewModelScope.launch {
            when (val result = conversations.send(sessionId, message.text)) {
                is ApiResult.Success -> {
                    sessionId = result.value.sessionId
                    savedState[TITLE] = result.value.title
                    _uiState.update { state ->
                        state.copy(
                            title = result.value.title,
                            sending = false,
                            messages = state.messages + toChatMessage(result.value),
                            requirements = result.value.requirements,
                        )
                    }
                }
                is ApiResult.Failure ->
                    _uiState.update { state ->
                        state.copy(
                            sending = false,
                            messages =
                                state.messages.map {
                                    if (it.id == message.id) it.copy(error = result.message) else it
                                },
                        )
                    }
            }
        }
    }

    fun onMicDenied() {
        _uiState.update { it.copy(voiceError = "Microphone access is off. You can still type your question.") }
    }

    fun toggleRecording() {
        when (_uiState.value.voice) {
            VoiceState.IDLE -> startRecording()
            VoiceState.RECORDING -> stopAndTranscribe()
            VoiceState.TRANSCRIBING -> Unit
        }
    }

    private fun startRecording() {
        val started = voice.startRecording(onLimit = ::stopAndTranscribe)
        _uiState.update {
            if (started) it.copy(voice = VoiceState.RECORDING, voiceError = null)
            else it.copy(voiceError = "The microphone could not start. You can still type your question.")
        }
    }

    private fun stopAndTranscribe() {
        if (_uiState.value.voice != VoiceState.RECORDING) return
        _uiState.update { it.copy(voice = VoiceState.TRANSCRIBING) }
        viewModelScope.launch {
            when (val result = voice.stopAndTranscribe()) {
                null ->
                    _uiState.update {
                        it.copy(voice = VoiceState.IDLE, voiceError = "That recording was too short. Please try again.")
                    }
                is ApiResult.Success -> {
                    // The text goes into the box, not to the agent: a part number heard wrong must be caught here.
                    updateDraft(listOf(draft.trim(), result.value).filter { it.isNotEmpty() }.joinToString(" "))
                    _uiState.update { it.copy(voice = VoiceState.IDLE) }
                }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(voice = VoiceState.IDLE, voiceError = result.message) }
            }
        }
    }

    fun cancelRecording() {
        if (_uiState.value.voice != VoiceState.RECORDING) return
        voice.discardRecording()
        _uiState.update { it.copy(voice = VoiceState.IDLE) }
    }

    override fun onCleared() {
        voice.discardRecording()
    }

    private fun toChatMessage(message: MessageView) =
        ChatMessage(
            id = message.id,
            fromUser = message.role == "user",
            text = message.content,
            outcome = message.outcome,
            products = message.products,
            alternatives = message.alternatives,
            suggestions = message.suggestions,
            matches = message.matches,
            followUps = message.followUps,
            requirements = message.requirements,
            createdAt = message.createdAt,
        )

    private fun toChatMessage(response: ConversationResponse) =
        ChatMessage(
            id = response.messageId,
            fromUser = false,
            text = response.answer,
            outcome = response.outcome,
            products = response.products,
            alternatives = response.alternatives,
            suggestions = response.suggestions,
            matches = response.matches,
            followUps = response.followUps,
            requirements = response.requirements,
            createdAt = now(),
        )

    private fun now() = OffsetDateTime.now().toString()

    private companion object {
        const val SESSION_ID = "session_id"
        const val TITLE = "title"
        const val DRAFT = "draft"
    }
}
