package com.series76.assistant.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.series76.assistant.R
import com.series76.assistant.ui.components.BrandMark
import com.series76.assistant.ui.components.IconBadge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.series76.assistant.data.remote.ActuatorData
import com.series76.assistant.data.remote.Electrical
import com.series76.assistant.data.remote.Rating
import com.series76.assistant.data.remote.Requirements
import com.series76.assistant.ui.components.ErrorState
import com.series76.assistant.ui.components.ListPlaceholder
import com.series76.assistant.ui.components.SectionLabel
import com.series76.assistant.ui.theme.Series76Theme
import kotlinx.coroutines.launch

private const val USER_ITEM = "user"
private const val ASSISTANT_ITEM = "assistant"
private const val WORKING_ITEM = "working"
private const val DAY_ITEM = "day"
private const val OLDER_PREFETCH_ROWS = 3

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
        onLoadOlder = viewModel::loadOlder,
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
    onLoadOlder: () -> Unit,
    onToggleRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onMicDenied: () -> Unit,
    onBack: () -> Unit,
    onOpenDebug: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val days = remember(uiState.messages) { chatDays(uiState.messages) }
    // Each day adds its header to the list, so the last index counts them too.
    val itemCount = uiState.messages.size + days.size + if (uiState.sending) 1 else 0

    // What sits at the bottom. Older pages land on top without changing it, so loading them never scrolls the reader.
    val tail = "${uiState.messages.lastOrNull()?.id}:${uiState.sending}"
    // Saved, so that coming back from the debug screen or a rotation keeps the reader's place instead of jumping.
    var placedTail by rememberSaveable { mutableStateOf<String?>(null) }
    // Aligns the newest item's top, so a long table opens at its answer and not at its last row. History jumps there
    // at once, because scrolling through a whole conversation on opening it only looks like the screen is lost.
    LaunchedEffect(tail) {
        if (itemCount > 0 && tail != placedTail) {
            if (placedTail == null) listState.scrollToItem(itemCount - 1) else listState.animateScrollToItem(itemCount - 1)
        }
        if (itemCount > 0) placedTail = tail
    }
    // Asks for the page before a few rows ahead of the top. LazyColumn keeps the first visible row in place by its key
    // when rows are added above it, so the reader stays where they were.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.totalItemsCount > 0 && listState.firstVisibleItemIndex < OLDER_PREFETCH_ROWS }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadOlder() }
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
                            days.forEach { day ->
                                // Not pinned: a pinned label would sit on top of the answer being read, and most conversations
                                // span a single day.
                                item(key = "day-${day.label}", contentType = DAY_ITEM) { DayHeader(day.label) }
                                items(
                                    day.messages,
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
                                                isLatest = !uiState.sending && message === uiState.messages.last(),
                                                onSuggestion = onSuggestion,
                                                onOpenDebug = { onOpenDebug(message.id) },
                                            )
                                        }
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
                // A bar over the list and not a row in it: a row on top would become the anchor and push the page down.
                if (uiState.loadingOlder) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
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
private fun DayHeader(label: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier =
                Modifier.semantics { heading() }
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
        )
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
    // The strip stays pinned above the answers, so a long list folds to two lines and opens on a tap.
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(
            requirementsLine(chips),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(onClickLabel = if (expanded) "Fold the requirements" else "Show every requirement") {
                        expanded = !expanded
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private data class Starter(val task: String, val query: String, val icon: Int)

private val STARTERS =
    listOf(
        Starter("Look up a part number", "What is the torque of 761A00-11300000/A?", R.drawable.ic_search),
        Starter("Find a model", "Explosionproof at 220 V, modulating, with at least 500 Nm", R.drawable.ic_actuator),
        Starter("Compare", "The 110 V weatherproof models above 1000 Nm", R.drawable.ic_compare),
    )

@Composable
private fun StartPanel(onPick: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrandMark(size = 48.dp)
        Text(
            "What do you need to know?",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "Ask by part number or by requirements. Every figure comes from the Series 76 data tables.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        STARTERS.forEach { starter ->
            Surface(
                onClick = { onPick(starter.query) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(painterResource(starter.icon))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(starter.task, style = MaterialTheme.typography.titleSmall)
                        Text(
                            starter.query,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
            onLoadOlder = {},
            onToggleRecording = {},
            onCancelRecording = {},
            onMicDenied = {},
            onBack = {},
            onOpenDebug = {},
        )
    }
}
