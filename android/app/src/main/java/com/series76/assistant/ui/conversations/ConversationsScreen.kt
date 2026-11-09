package com.series76.assistant.ui.conversations

import com.series76.assistant.ui.components.keepPartNumbersWhole
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.series76.assistant.data.remote.SessionSummary
import com.series76.assistant.ui.components.BrandMark
import com.series76.assistant.ui.components.rememberLocalClock
import com.series76.assistant.ui.components.EmptyState
import com.series76.assistant.ui.components.ErrorState
import com.series76.assistant.ui.components.ListPlaceholder
import com.series76.assistant.ui.components.rememberPlaceholderVisible
import com.series76.assistant.ui.components.SectionLabel
import com.series76.assistant.ui.theme.Series76Theme
import com.series76.assistant.ui.theme.TopBarHeight
import com.series76.assistant.ui.theme.sideRoom

@Composable
fun ConversationsRoute(viewModel: ConversationsViewModel, onOpen: (id: String, title: String) -> Unit, onNew: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Runs each time this screen returns from a chat, whose new turns change the list.
    LaunchedEffect(Unit) { viewModel.load() }
    ConversationsScreen(
        uiState,
        onOpen,
        onNew,
        onRefresh = viewModel::load,
        onLoadMore = viewModel::loadMore,
        onSignOut = viewModel::signOut,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    uiState: ConversationsUiState,
    onOpen: (id: String, title: String) -> Unit,
    onNew: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSignOut: () -> Unit,
) {
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }
    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your conversations stay saved, and you will need your email and password to come back.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingSignOut = false
                        onSignOut()
                    },
                ) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmingSignOut = false }) { Text("Cancel") } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = TopBarHeight,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BrandMark(size = 30.dp)
                        Column {
                            Text("Series 76", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Actuator assistant",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { confirmingSignOut = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New conversation") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.semantics { contentDescription = "New conversation" },
            )
        },
    ) { padding ->
        val sessions = uiState.sessions
        val placeholder = rememberPlaceholderVisible(sessions == null && uiState.loading)
        PullToRefreshBox(
            isRefreshing = uiState.loading && sessions != null,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                placeholder -> ListPlaceholder(padding)
                // Blank for the first moments of a load, which usually ends before a placeholder is worth showing.
                sessions == null && uiState.loading -> Unit
                sessions == null -> ErrorState(uiState.error.orEmpty(), onRefresh, padding)
                sessions.isEmpty() && uiState.error != null -> ErrorState(uiState.error, onRefresh, padding)
                sessions.isEmpty() ->
                    EmptyState(
                        title = "No conversations yet",
                        detail = "Ask about a part number, or describe the actuator you need.",
                        contentPadding = padding,
                    )
                else -> SessionList(sessions, uiState.error, uiState.loadingMore, onOpen, onLoadMore, padding)
            }
        }
    }
}

@Composable
private fun SessionList(
    sessions: List<SessionSummary>,
    error: String?,
    loadingMore: Boolean,
    onOpen: (id: String, title: String) -> Unit,
    onLoadMore: () -> Unit,
    padding: PaddingValues,
) {
    val clock = rememberLocalClock()
    val groups = remember(sessions, clock) { groupSessions(sessions, clock.today, clock.zone, clock.use24Hour) }
    val listState = rememberLazyListState()
    // Asks for the next page a few rows before the end, so it usually arrives before the reader gets there.
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - PREFETCH_ROWS
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
    // Padding instead of a narrower list, so a swipe in the margins of a tablet still scrolls.
    BoxWithConstraints {
        val side = sideRoom(maxWidth)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            // The bottom clears the navigation bar and the button, so neither covers the last conversation.
            contentPadding =
                PaddingValues(start = side, end = side, bottom = padding.calculateBottomPadding() + 88.dp),
        ) {
            error?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            groups.forEach { group ->
                // Pinned while its conversations scroll under it, so the reader always knows which day they are in.
                stickyHeader(key = group.label, contentType = "day") {
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        SectionLabel(
                            group.label,
                            modifier =
                                Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
                        )
                    }
                }
                items(group.sessions, key = { it.id }, contentType = { "session" }) { session ->
                    SessionItem(session, onClick = { onOpen(session.id, session.title) }, modifier = Modifier.animateItem())
                }
            }
            if (loadingMore) {
                item(key = "more", contentType = "more") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

private const val PREFETCH_ROWS = 5

@Composable
private fun SessionItem(session: SessionRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    session.time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            session.preview?.let { preview ->
                Text(
                    keepPartNumbersWhole(preview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConversationsPreview() {
    Series76Theme {
        ConversationsScreen(
            uiState =
                ConversationsUiState(
                    sessions =
                        listOf(
                            SessionSummary(
                                "1",
                                "Torque of 765B00-11300000/A in Nm?",
                                "2026-11-03T10:15:00+00:00",
                                preview = "The 765B00-11300000/A is weatherproof, on 110 V single phase.",
                            ),
                            SessionSummary("2", "Explosionproof at 24 V", "2026-10-30T08:00:00+00:00"),
                        ),
                    loading = false,
                ),
            onOpen = { _, _ -> },
            onNew = {},
            onRefresh = {},
            onLoadMore = {},
            onSignOut = {},
        )
    }
}
