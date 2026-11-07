package com.series76.assistant.ui.chat

import android.content.ClipData
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.series76.assistant.R
import com.series76.assistant.ui.components.SectionLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val USER_BUBBLE = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)

@Composable
internal fun UserMessage(message: ChatMessage, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        // The corner nearest the edge stays tight, which points the bubble at the side it came from.
        Text(
            message.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier =
                Modifier.widthIn(max = 320.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, USER_BUBBLE)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
internal fun AssistantMessage(
    message: ChatMessage,
    unit: TorqueDisplay,
    isLatest: Boolean,
    onSuggestion: (String) -> Unit,
    onOpenDebug: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    val copy = {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("answer", message.text))) }
        Unit
    }
    // Copying lives behind a long press, as in messaging apps, so no button takes a row under every answer.
    Box {
        Column(
            // pointerInput and not combinedClickable, which would announce the debug entry to end users using TalkBack.
            // TalkBack still reaches copying through its own action.
            modifier =
                Modifier.fillMaxWidth()
                    .semantics {
                        customActions = listOf(CustomAccessibilityAction("Copy answer") { copy(); true })
                    }
                    .pointerInput(message.id) {
                        detectTapGestures(
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuOpen = true
                            },
                        )
                    },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutcomeNote(
                message.outcome,
                hasResults =
                    message.products.isNotEmpty() || message.alternatives.isNotEmpty() || message.suggestions.isNotEmpty(),
            )
            Text(message.text, style = MaterialTheme.typography.bodyLarge)
            // Only on the latest answer: on an older one, a tap would answer a question the conversation has moved past.
            // Above the results, because a long list would otherwise push the answer to a question off the screen.
            if (isLatest && message.followUps.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.followUps.forEach { followUp ->
                        SuggestionChip(
                            onClick = { onSuggestion(followUp) },
                            label = { Text(followUp) },
                            icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp)) },
                            shape = CircleShape,
                        )
                    }
                }
            }
            if (message.products.isNotEmpty()) {
                ProductResults(
                    title = resultsTitle(message.products.map { it.partNumber }, message.matches),
                    products = message.products,
                    defaultUnit = unit,
                    requirements = message.requirements,
                    // A match met everything. A model from a lookup met nothing in particular, as it was never checked.
                    unmetByModel =
                        message.products.map { product ->
                            if (product.partNumber in message.matches) emptyList() else null
                        },
                )
            }
            if (message.alternatives.isNotEmpty()) {
                ProductResults(
                    title = "Closest alternatives",
                    products = message.alternatives.map { it.actuator },
                    defaultUnit = unit,
                    requirements = message.requirements,
                    unmetByModel = message.alternatives.map { it.unmet },
                )
            }
            if (message.suggestions.isNotEmpty()) {
                SectionLabel("Did you mean")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.suggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { onSuggestion(suggestion) },
                            label = { Text(suggestion) },
                            icon = { Icon(Icons.Filled.Search, null, Modifier.size(16.dp)) },
                            shape = CircleShape,
                        )
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Copy answer") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_copy), contentDescription = null) },
                onClick = {
                    copy()
                    menuOpen = false
                },
            )
            DropdownMenuItem(
                text = { Text("Technical details") },
                leadingIcon = { Icon(Icons.Filled.Build, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onOpenDebug()
                },
            )
        }
    }
}

@Composable
private fun OutcomeNote(outcome: String?, hasResults: Boolean) {
    val note = outcomeNote(outcome, hasResults) ?: return
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

private const val ONE_SECOND_MILLIS = 1000L

@Composable
internal fun WorkingAnswer() {
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
internal fun WorkingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
