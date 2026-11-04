package com.series76.assistant.ui.debug

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.series76.assistant.data.remote.ApiResult
import com.series76.assistant.data.conversation.ConversationRepository
import com.series76.assistant.data.remote.MessageDebug
import com.series76.assistant.data.remote.ToolTrace
import com.series76.assistant.ui.components.ErrorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

data class DebugUiState(
    val debug: MessageDebug? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class DebugViewModel(
    private val messageId: String,
    private val conversations: ConversationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = DebugUiState()
        viewModelScope.launch {
            _uiState.value =
                when (val result = conversations.debug(messageId)) {
                    is ApiResult.Success -> DebugUiState(result.value, loading = false)
                    is ApiResult.Failure -> DebugUiState(loading = false, error = result.message)
                }
        }
    }
}

private val PRETTY = Json { prettyPrint = true }

private fun pretty(element: JsonElement) = PRETTY.encodeToString(JsonElement.serializer(), element)

@Composable
fun DebugRoute(viewModel: DebugViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DebugScreen(uiState, onRetry = viewModel::load, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(uiState: DebugUiState, onRetry: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val debug = uiState.debug
        when {
            uiState.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            debug == null -> ErrorState(uiState.error.orEmpty(), onRetry = onRetry, contentPadding = padding)
            else -> DebugContent(debug, padding)
        }
    }
}

@Composable
private fun DebugContent(debug: MessageDebug, padding: PaddingValues) {
    val trace = debug.trace
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Model: ${trace.model}")
                    Text("Prompt: ${trace.promptVersion}")
                    Text("Tokens: ${trace.inputTokens} in, ${trace.outputTokens} out")
                    Text("Cost: ${trace.costUsd?.let { "USD $it" } ?: "not reported"}")
                    Text("Latency: ${trace.latencyMs} ms, ${trace.modelCalls.size} model call(s)")
                    Text("Validator retried: ${if (trace.validation.retried) "yes" else "no"}")
                    Text("Answer from template: ${if (trace.validation.usedTemplate) "yes" else "no"}")
                    if (trace.validation.rejected.isNotEmpty()) {
                        Text("Numbers rejected: ${trace.validation.rejected.joinToString()}")
                    }
                }
            }
        }
        if (trace.tools.isEmpty()) {
            item { Text("No tools were called for this answer.") }
        }
        items(trace.tools) { tool -> ToolCard(tool) }
    }
}

@Composable
private fun ToolCard(tool: ToolTrace) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${tool.name} (${tool.latencyMs} ms)", style = MaterialTheme.typography.titleSmall)
            Text("Parameters", style = MaterialTheme.typography.labelMedium)
            Code(pretty(tool.parameters))
            Text("Returned", style = MaterialTheme.typography.labelMedium)
            Code(pretty(tool.result))
        }
    }
}

@Composable
private fun Code(text: String) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        softWrap = false,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    )
}
