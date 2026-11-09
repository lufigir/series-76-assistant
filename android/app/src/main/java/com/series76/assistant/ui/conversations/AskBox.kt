package com.series76.assistant.ui.conversations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.series76.assistant.R
import com.series76.assistant.data.remote.CatalogEntry
import com.series76.assistant.data.remote.MAX_QUERY_LENGTH
import com.series76.assistant.ui.chat.PartNumberBuilderSheet

/**
 * The home screen opens on a question, the way an engineer opens a catalog on a lookup: typing here starts a new
 * conversation, so the screen needs no "new conversation" button. It shares the chat's bar shape, so asking looks the
 * same in both places.
 */
@Composable
internal fun AskBox(
    catalog: List<CatalogEntry>,
    onAsk: (String) -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var building by rememberSaveable { mutableStateOf(false) }
    if (building) {
        PartNumberBuilderSheet(
            catalog,
            onPick = { partNumber ->
                text = listOf(text.trim(), partNumber).filter { it.isNotEmpty() }.joinToString(" ") + " "
                building = false
            },
            onDismiss = { building = false },
        )
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // The keyboard belongs to this bar; left open, it would cover the chat that opens next.
    val leave = {
        focus.clearFocus()
        keyboard?.hide()
    }
    val submit = {
        val question = text.trim()
        if (question.isNotEmpty()) {
            leave()
            text = ""
            onAsk(question)
        }
    }
    val speak = {
        leave()
        onSpeak()
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            )
            TextField(
                value = text,
                onValueChange = { text = it.take(MAX_QUERY_LENGTH) },
                // One line at any font size: a placeholder that wraps makes the bar jump in height.
                placeholder = { Text("Ask about an actuator", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(visible = text.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                FilledIconButton(onClick = submit, shape = CircleShape) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask", modifier = Modifier.size(20.dp))
                }
            }
            if (text.isBlank()) {
                if (catalog.isNotEmpty()) {
                    IconButton(onClick = { building = true }) {
                        Icon(
                            painterResource(R.drawable.ic_build_number),
                            contentDescription = "Build a part number",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                // Filled: with nothing typed, speaking is the main action, as the send button is once there is text.
                FilledIconButton(onClick = speak, shape = CircleShape) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = "Ask by voice", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
