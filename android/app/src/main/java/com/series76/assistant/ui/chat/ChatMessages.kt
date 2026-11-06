package com.series76.assistant.ui.chat

import android.content.ClipData
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

@Composable
internal fun UserMessage(message: ChatMessage, onRetry: () -> Unit) {
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
                    SuggestionChip(onClick = { onSuggestion(followUp) }, label = { Text(followUp) })
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
