package com.series76.assistant.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.series76.assistant.R
import com.series76.assistant.data.remote.MAX_QUERY_LENGTH
import com.series76.assistant.ui.components.PulsingDot
import com.series76.assistant.ui.components.ErrorMessage

@Composable
internal fun Composer(
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

    var building by rememberSaveable { mutableStateOf(false) }
    if (building) {
        PartNumberBuilderSheet(
            uiState.catalog,
            onPick = { partNumber ->
                onDraftChange(listOf(draft.trim(), partNumber).filter { it.isNotEmpty() }.joinToString(" ") + " ")
                building = false
            },
            onDismiss = { building = false },
        )
    }

    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            uiState.voiceError?.let {
                ErrorMessage(it, modifier = Modifier.padding(horizontal = 8.dp))
            }
            AnimatedContent(
                targetState = uiState.voice,
                transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                label = "composer",
            ) { voice ->
                when (voice) {
                    VoiceState.RECORDING -> RecordingBar(onStop = onToggleRecording, onCancel = onCancelRecording)
                    VoiceState.TRANSCRIBING -> TranscribingBar()
                    VoiceState.IDLE ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val completions = remember(draft, uiState.catalog) { completions(draft, uiState.catalog) }
                            if (completions.isNotEmpty()) {
                                CompletionRow(completions, onPick = { onDraftChange(complete(draft, it)) })
                            }
                            TextComposer(
                                draft,
                                onDraftChange,
                                uiState.sending,
                                onSend,
                                onMic,
                                onBuild = if (uiState.catalog.isEmpty()) null else ({ building = true }),
                            )
                        }
                }
            }
        }
    }
}

private const val COUNTER_MARGIN = 200

// One shape for the three states of the bar, so typing, listening and transcribing read as the same control.
private val BAR_SHAPE = RoundedCornerShape(24.dp)
private val BAR_HEIGHT = 48.dp

@Composable
private fun Bar(content: @Composable RowScope.() -> Unit) {
    Surface(shape = BAR_SHAPE, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.heightIn(min = BAR_HEIGHT).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun TextComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onBuild: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Bar {
            onBuild?.let {
                IconButton(onClick = it) {
                    Icon(
                        painterResource(R.drawable.ic_build_number),
                        contentDescription = "Build a part number",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("Ask a question", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                maxLines = 5,
                modifier = Modifier.weight(1f).padding(start = if (onBuild == null) 8.dp else 0.dp),
            )
            // One slot for both: with nothing to send, the microphone is the only action that makes sense.
            AnimatedContent(
                targetState = draft.isBlank(),
                transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                label = "action",
            ) { empty ->
                if (empty) {
                    IconButton(onClick = onMic) {
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
                            // What comes back is a table to read, and the keyboard would cover most of it.
                            focus.clearFocus()
                            onSend()
                        },
                        enabled = !sending,
                        shape = CircleShape,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        // The count appears only near the limit, where it explains why typing stops.
        if (draft.length > MAX_QUERY_LENGTH - COUNTER_MARGIN) {
            Text(
                "${draft.length} / $MAX_QUERY_LENGTH",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End).padding(end = 16.dp),
            )
        }
    }
}

private const val ONE_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60

@Composable
private fun RecordingBar(onStop: () -> Unit, onCancel: () -> Unit) {
    val elapsed by
        produceState(0) {
            while (true) {
                delay(ONE_SECOND)
                value += 1
            }
        }
    Bar {
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Discard recording") }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PulsingDot(color = MaterialTheme.colorScheme.error)
                Text(
                    "%d:%02d".format(elapsed / SECONDS_PER_MINUTE, elapsed % SECONDS_PER_MINUTE),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            // Spoken digit by digit, a part number survives transcription; spoken as large numbers, it often does not.
            Text(
                "Say part numbers digit by digit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledIconButton(
            onClick = onStop,
            shape = CircleShape,
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

@Composable
private fun TranscribingBar() {
    Bar {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Transcribing", style = MaterialTheme.typography.titleSmall)
            Text(
                "You can edit it before sending",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
