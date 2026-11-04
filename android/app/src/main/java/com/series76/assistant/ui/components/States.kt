package com.series76.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.series76.assistant.ui.theme.CapsLabelStyle

// Both scroll, so that pull-to-refresh still works on a screen with nothing else to scroll.
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    Column(
        modifier =
            Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        CapsLabel("Something went wrong", color = MaterialTheme.colorScheme.error)
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        FilledTonalButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun EmptyState(title: String, detail: String, contentPadding: PaddingValues = PaddingValues()) {
    Column(
        modifier =
            Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(title, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
        Text(
            detail,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = CapsLabelStyle, color = color, modifier = modifier)
}

/** Says something is under way without pretending to know how far along it is. */
@Composable
fun PulsingDot(color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val opacity by
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
            label = "opacity",
        )
    Box(Modifier.size(8.dp).alpha(opacity).background(color, CircleShape))
}

/** Placeholder rows shaped like the list that is about to load, so the screen does not jump when it arrives. */
@Composable
fun ListPlaceholder(contentPadding: PaddingValues = PaddingValues()) {
    val tone = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        repeat(5) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(15.dp).background(tone, MaterialTheme.shapes.small))
                Box(Modifier.fillMaxWidth(0.3f).height(11.dp).background(tone, MaterialTheme.shapes.small))
            }
        }
    }
}
