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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.series76.assistant.R
import com.series76.assistant.data.remote.MAX_QUERY_LENGTH
import com.series76.assistant.ui.components.PulsingDot
import kotlinx.coroutines.launch

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

private const val COUNTER_MARGIN = 200

@Composable
private fun TextComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text("Ask about an actuator") },
            // The count appears only near the limit, where it explains why typing stops.
            supportingText =
                if (draft.length > MAX_QUERY_LENGTH - COUNTER_MARGIN) {
                    { Text("${draft.length} / $MAX_QUERY_LENGTH") }
                } else {
                    null
                },
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
                        // What comes back is a table to read, and the keyboard would cover most of it.
                        focus.clearFocus()
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
