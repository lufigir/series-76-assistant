package com.series76.assistant.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.series76.assistant.R
import com.series76.assistant.data.remote.ActuatorData
import com.series76.assistant.data.remote.Electrical
import com.series76.assistant.data.remote.Rating
import com.series76.assistant.data.remote.Requirements
import com.series76.assistant.ui.components.ErrorState
import com.series76.assistant.ui.components.ListPlaceholder
import com.series76.assistant.ui.components.PulsingDot
import com.series76.assistant.ui.components.SectionLabel
import com.series76.assistant.ui.theme.Series76Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val USER_ITEM = "user"
private const val ASSISTANT_ITEM = "assistant"
private const val WORKING_ITEM = "working"
private const val ONE_SECOND_MILLIS = 1000L

@Composable
fun ChatRoute(viewModel: ChatViewModel, onBack: () -> Unit, onOpenDebug: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Android cuts off the microphone in the background, so a recording never outlives the visible screen.
    LifecycleStartEffect(Unit) { onStopOrDispose { viewModel.cancelRecording() } }

    ChatScreen(
        uiState = uiState,
        draft = viewModel.draft,
        onDraftChange = viewModel::updateDraft,
        onSend = viewModel::sendDraft,
        onSuggestion = viewModel::sendSuggestion,
        onRetrySend = viewModel::retry,
        onRetryHistory = viewModel::loadHistory,
        onToggleRecording = viewModel::toggleRecording,
        onCancelRecording = viewModel::cancelRecording,
        onMicDenied = viewModel::onMicDenied,
        onBack = onBack,
        onOpenDebug = onOpenDebug,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onSuggestion: (String) -> Unit,
    onRetrySend: (ChatMessage) -> Unit,
    onRetryHistory: () -> Unit,
    onToggleRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onMicDenied: () -> Unit,
    onBack: () -> Unit,
    onOpenDebug: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val itemCount = uiState.messages.size + if (uiState.sending) 1 else 0

    // Saved, so that coming back from the debug screen or a rotation keeps the reader's place instead of jumping.
    var placedItems by rememberSaveable { mutableIntStateOf(0) }
    // Aligns the newest item's top, so a long table opens at its answer and not at its last row. History jumps there
    // at once, because scrolling through a whole conversation on opening it only looks like the screen is lost.
    LaunchedEffect(itemCount) {
        if (itemCount > 0 && itemCount != placedItems) {
            if (placedItems == 0) listState.scrollToItem(itemCount - 1) else listState.animateScrollToItem(itemCount - 1)
        }
        placedItems = itemCount
    }
    // A long answer can fill the screen by itself, and being inside it is not being behind.
    val canJump by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index
            lastVisible != null && lastVisible < layout.totalItemsCount - 1
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            conversationTitle(uiState.messages, uiState.loadingHistory),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            Composer(
                uiState,
                draft,
                onDraftChange,
                onSend,
                onToggleRecording,
                onCancelRecording,
                onMicDenied,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            uiState.requirements?.let { RequirementsStrip(requirementChips(it)) }
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.loadingHistory -> ListPlaceholder()
                    uiState.historyError != null -> ErrorState(uiState.historyError, onRetryHistory)
                    uiState.messages.isEmpty() && !uiState.sending -> StartPanel(onPick = onDraftChange)
                    else ->
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(
                                uiState.messages,
                                key = { it.id },
                                contentType = { if (it.fromUser) USER_ITEM else ASSISTANT_ITEM },
                            ) { message ->
                                Box(Modifier.animateItem()) {
                                    if (message.fromUser) {
                                        UserMessage(message, onRetry = { onRetrySend(message) })
                                    } else {
                                        AssistantMessage(
                                            message = message,
                                            unit = preferredUnit(uiState.requirements),
                                            onSuggestion = onSuggestion,
                                            onOpenDebug = { onOpenDebug(message.id) },
                                        )
                                    }
                                }
                            }
                            if (uiState.sending) {
                                item(key = WORKING_ITEM, contentType = WORKING_ITEM) {
                                    Box(Modifier.animateItem()) { WorkingAnswer() }
                                }
                            }
                        }
                }
                JumpToLatest(
                    visible = canJump && uiState.messages.isNotEmpty(),
                    onClick = { scope.launch { listState.animateScrollToItem(itemCount - 1) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun JumpToLatest(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(120)), exit = fadeOut(tween(120)), modifier = modifier) {
        SmallFloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Go to the latest answer")
        }
    }
}

private fun preferredUnit(requirements: Requirements?): TorqueDisplay =
    TorqueDisplay.entries.firstOrNull { it.apiName == requirements?.minTorque?.unit } ?: TorqueDisplay.NM

@Composable
private fun RequirementsStrip(chips: List<String>) {
    if (chips.isEmpty()) return
    Column {
        Text(
            chips.joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier =
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private data class Starter(val task: String, val query: String)

private val STARTERS =
    listOf(
        Starter("Look up a part number", "What is the torque of 761A00-11300000/A?"),
        Starter("Find a model", "Explosionproof at 220 V, modulating, with at least 500 Nm"),
        Starter("Compare", "The 110 V weatherproof models above 1000 Nm"),
    )

@Composable
private fun StartPanel(onPick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Ask by part number or by requirements. Every figure comes from the Series 76 data tables.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        STARTERS.forEach { starter ->
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable { onPick(starter.query) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SectionLabel(starter.task)
                Text(starter.query, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun UserMessage(message: ChatMessage, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        // A flat tinted block, not a bubble: this reads as a query put to a tool, not as a text message.
        Text(
            message.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier =
                Modifier.widthIn(max = 320.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        message.error?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantMessage(
    message: ChatMessage,
    unit: TorqueDisplay,
    onSuggestion: (String) -> Unit,
    onOpenDebug: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Column(
        // pointerInput and not combinedClickable, which would announce the gesture to end users using TalkBack.
        modifier =
            Modifier.fillMaxWidth().pointerInput(message.id) {
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenDebug()
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutcomeNote(message.outcome)
        Text(message.text, style = MaterialTheme.typography.bodyLarge)
        when (message.products.size) {
            0 -> Unit
            1 -> ProductCard(message.products.single(), unit)
            else -> ComparisonTable("${message.products.size} models", message.products, unit)
        }
        if (message.alternatives.isNotEmpty()) {
            ComparisonTable(
                title = "Closest alternatives",
                products = message.alternatives.map { it.actuator },
                defaultUnit = unit,
                misses = message.alternatives.map { candidate -> candidate.unmet.map(::unmetText) },
            )
        }
        if (message.suggestions.isNotEmpty()) {
            SectionLabel("Did you mean")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                message.suggestions.forEach { suggestion ->
                    SuggestionChip(onClick = { onSuggestion(suggestion) }, label = { Text(suggestion) })
                }
            }
        }
        IconButton(
            onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("answer", message.text))) }
            },
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(
                painterResource(R.drawable.ic_copy),
                contentDescription = "Copy answer",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OutcomeNote(outcome: String?) {
    val note = outcomeNote(outcome) ?: return
    Text(
        note,
        style = MaterialTheme.typography.labelLarge,
        color =
            when (outcomeTone(outcome)) {
                OutcomeTone.CAUTION -> MaterialTheme.colorScheme.tertiary
                OutcomeTone.NEGATIVE -> MaterialTheme.colorScheme.error
                OutcomeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
            },
    )
}

@Composable
private fun WorkingAnswer() {
    val elapsed by
        produceState(0) {
            while (true) {
                delay(ONE_SECOND_MILLIS)
                value += 1
            }
        }
    WorkingRow(workingText(elapsed))
}

@Composable
private fun WorkingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Composer(
    uiState: ChatUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onMicDenied: () -> Unit,
) {
    val context = LocalContext.current
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) onToggleRecording() else onMicDenied()
        }
    val onMic = {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) onToggleRecording() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            uiState.voiceError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            AnimatedContent(
                targetState = uiState.voice,
                transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                label = "composer",
            ) { voice ->
                when (voice) {
                    VoiceState.RECORDING -> RecordingBar(onStop = onToggleRecording, onCancel = onCancelRecording)
                    VoiceState.TRANSCRIBING ->
                        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), Alignment.CenterStart) {
                            WorkingRow("Transcribing. You can review it before sending.")
                        }
                    VoiceState.IDLE -> TextComposer(draft, onDraftChange, uiState.sending, onSend, onMic)
                }
            }
        }
    }
}

@Composable
private fun TextComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text("Ask about an actuator") },
            shape = MaterialTheme.shapes.medium,
            maxLines = 4,
            modifier = Modifier.weight(1f),
        )
        // One slot for both: with nothing to send, the microphone is the only action that makes sense.
        AnimatedContent(
            targetState = draft.isBlank(),
            transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
            label = "action",
        ) { empty ->
            if (empty) {
                IconButton(onClick = onMic, modifier = Modifier.size(56.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_mic),
                        contentDescription = "Record a voice message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FilledIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSend()
                    },
                    enabled = !sending,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun RecordingBar(onStop: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(56.dp)
                .border(1.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.medium)
                .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PulsingDot(color = MaterialTheme.colorScheme.error)
        Text("Listening", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Discard recording") }
        FilledIconButton(
            onClick = onStop,
            shape = MaterialTheme.shapes.medium,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
        ) {
            Icon(painterResource(R.drawable.ic_stop), contentDescription = "Stop recording")
        }
    }
}

private val PREVIEW_PRODUCT =
    ActuatorData(
        partNumber = "761A00-11300000/A",
        enclosure = "weatherproof",
        powerSupply = "110v_single_phase",
        service = "on_off_and_modulating",
        notAvailable = false,
        notCsaCertified = false,
        cyclesPerHour = 36,
        startsPerHour = 960,
        motorWatts = 15,
        ratings = listOf(Rating("on_off", 700, 80, 58.3, 40), Rating("modulating", 580, 65, 48.3, 40)),
        electricals = listOf(Electrical("60hz", 17, 1.2, 1.4), Electrical("50hz", 20, 1.1, 1.3)),
    )

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ChatPreview() {
    Series76Theme {
        ChatScreen(
            uiState =
                ChatUiState(
                    messages =
                        listOf(
                            ChatMessage("1", true, "What is the torque of 761A00-11300000/A?"),
                            ChatMessage(
                                "2",
                                false,
                                "For on/off service it delivers 80 Nm at 40% duty.",
                                outcome = "answered",
                                products = listOf(PREVIEW_PRODUCT),
                            ),
                        ),
                ),
            draft = "",
            onDraftChange = {},
            onSend = {},
            onSuggestion = {},
            onRetrySend = {},
            onRetryHistory = {},
            onToggleRecording = {},
            onCancelRecording = {},
            onMicDenied = {},
            onBack = {},
            onOpenDebug = {},
        )
    }
}
