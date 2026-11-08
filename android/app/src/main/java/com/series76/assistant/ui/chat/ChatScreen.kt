package com.series76.assistant.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListLayoutInfo
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
import com.series76.assistant.ui.components.rememberPlaceholderVisible
import com.series76.assistant.ui.components.SectionLabel
import com.series76.assistant.ui.theme.Series76Theme
import com.series76.assistant.ui.theme.ReadingWidth
import com.series76.assistant.ui.theme.TopBarHeight
import com.series76.assistant.ui.theme.sideRoom
import kotlinx.coroutines.launch

private const val USER_ITEM = "user"
private const val ASSISTANT_ITEM = "assistant"
private const val WORKING_ITEM = "working"
private const val TAIL_ROOM = "tail-room"
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
    val folded = remember(uiState.messages) { foldedResults(uiState.messages) }
    // Each day adds its header to the list, so the last index counts them too.
    val itemCount = uiState.messages.size + days.size + if (uiState.sending) 1 else 0

    // What sits at the bottom. Older pages land on top without changing it, so loading them never scrolls the reader.
    val tail = "${uiState.messages.lastOrNull()?.id}:${uiState.sending}"
    // Saved, so that coming back from the debug screen or a rotation keeps the reader's place instead of jumping.
    var placedTail by rememberSaveable { mutableStateOf<String?>(null) }
    // Pins the latest question to the top, as the reader sent it and once its answer arrives, so a one-line reply and a
    // long table both read as question, then answer. History jumps there at once, because scrolling through a whole
    // conversation on opening it only looks like the screen is lost.
    val anchor = latestQuestionIndex(days) ?: (itemCount - 1)
    // Room under the latest turn, so its question can reach the top while the answer is still short. It starts a screen
    // tall when a turn is placed and then shrinks to what the turn leaves free, which keeps the question where it is.
    val screenHeight = LocalWindowInfo.current.containerSize.height
    var tailRoom by remember { mutableIntStateOf(0) }
    // Once the reader scrolls the room out of sight, the question has been read and the room only lets the list run on
    // into blank space, so it goes until the next turn. It goes while out of sight, so nothing on screen moves.
    var roomReleased by remember { mutableStateOf(false) }
    val currentTail by rememberUpdatedState(tail)
    LaunchedEffect(listState, anchor) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val settled = placedTail == currentTail && !listState.isScrollInProgress
            if (settled && layout.visibleItemsInfo.none { it.key == TAIL_ROOM }) RELEASED_ROOM else fittedTailRoom(layout, anchor)
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect {
                if (it == RELEASED_ROOM) roomReleased = true
                tailRoom = if (roomReleased) 0 else it
            }
    }
    LaunchedEffect(tail) {
        if (itemCount > 0 && tail != placedTail) {
            // One frame without room first: a conversation that fits on the screen as it is has nothing to scroll past,
            // and pinning it would only let the list run on into blank space.
            roomReleased = true
            tailRoom = 0
            withFrameNanos {}
            if (listState.canScrollBackward || listState.canScrollForward) {
                roomReleased = false
                tailRoom = screenHeight
                // One frame, so the list measures the new room before it scrolls into it.
                withFrameNanos {}
                if (placedTail == null) listState.scrollToItem(anchor) else listState.animateScrollToItem(anchor)
            }
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
            // The room under the latest turn is the last item, so the latest message is the one before it.
            lastVisible != null && lastVisible < layout.totalItemsCount - 2
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                expandedHeight = TopBarHeight,
                    title = {
                        Text(
                            conversationTitle(uiState.title, uiState.messages, uiState.loadingHistory),
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
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.widthIn(max = ReadingWidth)) {
                    Composer(
                        uiState,
                        draft,
                        onDraftChange,
                        onSend,
                        onToggleRecording,
                        onCancelRecording,
                        onMicDenied,
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            uiState.requirements?.let { requirements ->
                val chips = requirementChips(requirements)
                if (!stripRepeatsTitle(chips, uiState.title)) {
                    // Aligned with the centered answers on a wide screen, so the strip reads as theirs.
                    BoxWithConstraints {
                        Box(Modifier.padding(horizontal = sideRoom(maxWidth))) { RequirementsStrip(chips) }
                    }
                }
            }
            val placeholder = rememberPlaceholderVisible(uiState.loadingHistory)
            Box(modifier = Modifier.weight(1f)) {
                when {
                    placeholder -> ListPlaceholder()
                    // Blank for the first moments of a load, which usually ends before a placeholder is worth showing.
                    uiState.loadingHistory -> Unit
                    uiState.historyError != null -> ErrorState(uiState.historyError, onRetryHistory)
                    uiState.messages.isEmpty() && !uiState.sending -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.widthIn(max = ReadingWidth)) { StartPanel(onPick = onDraftChange) }
                        }
                    else ->
                        BoxWithConstraints {
                            // Padding instead of a narrower list, so a swipe in the margins of a tablet still scrolls.
                            val side = sideRoom(maxWidth)
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 12.dp + side, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                                                    foldResults = message.id in folded,
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
                                item(key = TAIL_ROOM, contentType = TAIL_ROOM) {
                                    Spacer(Modifier.height(with(LocalDensity.current) { tailRoom.toDp() }))
                                }
                            }
                        }
                }
                // A bar over the list and not a row in it: a row on top would become the anchor and push the page down.
                if (uiState.loadingOlder) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
                // Hidden while the list moves, so it never sits over a table's last column as the reader scrolls past.
                JumpToLatest(
                    visible = canJump && !listState.isScrollInProgress && uiState.messages.isNotEmpty(),
                    onClick = { scope.launch { listState.animateScrollToItem(anchor) } },
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

/**
 * The room that lets the latest question sit at the top with the list scrolled to its end: what the viewport has left
 * after the question and everything below it. Null while the question is off screen, when it cannot be measured.
 */
/** Marks the room under the latest turn as scrolled out of sight, apart from any height the room can take. */
private const val RELEASED_ROOM = -1

private fun fittedTailRoom(layout: LazyListLayoutInfo, anchor: Int): Int? {
    val question = layout.visibleItemsInfo.firstOrNull { it.index == anchor } ?: return null
    val last = layout.visibleItemsInfo.lastOrNull { it.key != TAIL_ROOM } ?: return null
    // The turn runs past the bottom of the screen, so it already fills it.
    if (last.index < layout.totalItemsCount - 2) return 0
    val turn = last.offset + last.size - question.offset
    return (layout.viewportEndOffset - layout.afterContentPadding - turn - layout.mainAxisItemSpacing).coerceAtLeast(0)
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandMark(size = 48.dp)
        Text(
            "What do you need to know?",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "Ask by part number or by requirements. Every figure comes from the Series 76 data tables.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        STARTERS.forEach { starter ->
            Surface(
                onClick = { onPick(starter.query) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(painterResource(starter.icon))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
