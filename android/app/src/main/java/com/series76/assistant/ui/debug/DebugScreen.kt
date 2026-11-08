package com.series76.assistant.ui.debug

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.series76.assistant.data.remote.ValidationTrace
import com.series76.assistant.ui.components.IconBadge
import com.series76.assistant.ui.components.SectionLabel
import com.series76.assistant.ui.theme.SpecValueStyle
import com.series76.assistant.ui.theme.TopBarHeight
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
                expandedHeight = TopBarHeight,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebugContent(debug: MessageDebug, padding: PaddingValues) {
    val trace = debug.trace
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(trace.model, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Prompt ${trace.promptVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            // Two tiles a row on a phone, more on a wider screen.
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Metric("Latency", "${trace.latencyMs} ms", Modifier.weight(1f))
                Metric("Cost", formatCost(trace.costUsd), Modifier.weight(1f))
                Metric("Tokens in", "${trace.inputTokens}", Modifier.weight(1f))
                Metric("Tokens out", "${trace.outputTokens}", Modifier.weight(1f))
                Metric("Model calls", "${trace.modelCalls.size}", Modifier.weight(1f))
                Metric("Tool calls", "${trace.tools.size}", Modifier.weight(1f))
            }
        }
        item { Validation(trace.validation) }
        item { SectionLabel("Tools", modifier = Modifier.padding(top = 8.dp)) }
        if (trace.tools.isEmpty()) {
            item { Text("No tools were called for this answer.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(trace.tools) { tool -> ToolCard(tool) }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = SpecValueStyle.copy(fontSize = 18.sp, lineHeight = 24.sp))
    }
}

@Composable
private fun Validation(validation: ValidationTrace) {
    val clean = !validation.retried && !validation.usedTemplate && validation.rejected.isEmpty()
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(if (clean) colors.primaryContainer else colors.tertiaryContainer, MaterialTheme.shapes.large)
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (clean) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (clean) colors.onPrimaryContainer else colors.onTertiaryContainer,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (clean) "Every number was backed by a tool result" else "The validator stepped in",
                style = MaterialTheme.typography.titleSmall,
                color = if (clean) colors.onPrimaryContainer else colors.onTertiaryContainer,
            )
            if (!clean) {
                Text(
                    "Retried: ${if (validation.retried) "yes" else "no"} · From template: " +
                        if (validation.usedTemplate) "yes" else "no",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onTertiaryContainer,
                )
            }
            if (validation.rejected.isNotEmpty()) {
                Text(
                    "Numbers rejected: ${validation.rejected.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolTrace) {
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(
        onClick = { open = !open },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Filled.Build, size = 36.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(tool.name, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                    Text(
                        "${tool.latencyMs} ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Hide parameters and data" else "Show parameters and data",
                )
            }
            Text("Parameters", style = MaterialTheme.typography.labelMedium)
            Code(pretty(tool.parameters))
            if (open) {
                Text("Returned", style = MaterialTheme.typography.labelMedium)
                Code(pretty(tool.result))
            }
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
