package com.series76.assistant.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.series76.assistant.data.remote.SessionSummary
import com.series76.assistant.ui.components.CapsLabel
import com.series76.assistant.ui.components.EmptyState
import com.series76.assistant.ui.components.ErrorState
import com.series76.assistant.ui.components.ListPlaceholder
import com.series76.assistant.ui.theme.Series76Theme

@Composable
fun ConversationsRoute(viewModel: ConversationsViewModel, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Runs each time this screen returns from a chat, whose new turns change the list.
    LaunchedEffect(Unit) { viewModel.load() }
    ConversationsScreen(uiState, onOpen, onNew, onRefresh = viewModel::load, onSignOut = viewModel::signOut)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    uiState: ConversationsUiState,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Conversations") },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New conversation") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.semantics { contentDescription = "New conversation" },
            )
        },
    ) { padding ->
        val sessions = uiState.sessions
        PullToRefreshBox(
            isRefreshing = uiState.loading && sessions != null,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                sessions == null && uiState.loading -> ListPlaceholder(padding)
                sessions == null -> ErrorState(uiState.error.orEmpty(), onRefresh, padding)
                sessions.isEmpty() && uiState.error != null -> ErrorState(uiState.error, onRefresh, padding)
                sessions.isEmpty() ->
                    EmptyState(
                        title = "No conversations yet",
                        detail = "Ask about a part number, or describe the actuator you need.",
                        contentPadding = padding,
                    )
                else -> SessionList(sessions, uiState.error, onOpen, padding)
            }
        }
    }
}

@Composable
private fun SessionList(
    sessions: List<SessionSummary>,
    error: String?,
    onOpen: (String) -> Unit,
    padding: PaddingValues,
) {
    val groups = remember(sessions) { groupSessions(sessions) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // The bottom leaves room so the button never covers the last conversation.
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp),
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
            item(key = group.label) {
                CapsLabel(
                    group.label,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
                        .semantics { heading() },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            items(group.sessions, key = { it.id }) { session ->
                SessionItem(session, onClick = { onOpen(session.id) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun SessionItem(session: SessionRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                session.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            session.time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )
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
                            SessionSummary("1", "Torque of 765B00-11300000/A in Nm?", "2026-11-03T10:15:00+00:00"),
                            SessionSummary("2", "Explosionproof at 24 V", "2026-10-30T08:00:00+00:00"),
                        ),
                    loading = false,
                ),
            onOpen = {},
            onNew = {},
            onRefresh = {},
            onSignOut = {},
        )
    }
}
