package com.series76.assistant.ui.conversations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.series76.assistant.ui.components.SectionLabel
import com.series76.assistant.ui.components.keepPartNumbersWhole

private val NO_BREAK = Char(0xA0)

/** One question per thing the assistant does, each with a part number that is in the tables. */
internal val STARTER_QUESTIONS =
    listOf(
        "What is the torque of 761A00-11300000/A?",
        "Explosionproof, 220 V single phase, at least 500 Nm",
        "Compare 762A00-11300000/A and 762B00-11300000/A",
    )

/**
 * The home screen before the first conversation. It says the list is empty rather than broken, says what will fill it,
 * and offers questions that ask on a tap, following Nielsen Norman Group's three guidelines for empty states.
 */
@Composable
internal fun StarterState(onAsk: (String) -> Unit, contentPadding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("No conversations yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Your conversations will be listed here. Ask by part number, or describe the actuator you need.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionLabel("Try one", modifier = Modifier.padding(top = 16.dp))
        STARTER_QUESTIONS.forEach { question ->
            Surface(
                onClick = { onAsk(question) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    // A number never ends a line apart from its unit, so "500" and "Nm" stay together.
                    keepPartNumbersWhole(question).replace(" Nm", "${NO_BREAK}Nm").replace(" V ", "${NO_BREAK}V "),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}
