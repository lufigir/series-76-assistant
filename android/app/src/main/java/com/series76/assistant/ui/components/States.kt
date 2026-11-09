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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.series76.assistant.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row

// Both scroll, so that pull-to-refresh still works on a screen with nothing else to scroll.
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    Column(
        modifier =
            Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painterResource(R.drawable.ic_error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp),
        )
        SectionLabel("Something went wrong", color = MaterialTheme.colorScheme.error)
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
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, modifier = modifier)
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

/**
 * Whether a placeholder for [loading] should be on screen. A load shorter than [delayMillis] never shows one, and one
 * that does appear stays at least [minShowMillis], so a fast answer does not flash a skeleton for a frame or two. The
 * same rule as AndroidX's ContentLoadingProgressBar, with a shorter delay because the list loads from a nearby server.
 */
@Composable
fun rememberPlaceholderVisible(loading: Boolean, delayMillis: Long = 300, minShowMillis: Long = 500): Boolean {
    var visible by remember { mutableStateOf(false) }
    var shownAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(loading) {
        if (loading && !visible) {
            delay(delayMillis)
            visible = true
            shownAt = System.currentTimeMillis()
        } else if (!loading && visible) {
            delay((shownAt + minShowMillis - System.currentTimeMillis()).coerceAtLeast(0))
            visible = false
        }
    }
    return visible
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

/**
 * A failure in words with the error mark beside it. The rust primary sits close to the error red, so the mark is what
 * tells a failure apart from a button or a label at a glance, and it helps anyone who cannot tell the two reds apart.
 */
@Composable
fun ErrorMessage(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodySmall) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        val size = with(LocalDensity.current) { style.lineHeight.toDp() }
        Icon(
            painterResource(R.drawable.ic_error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 6.dp).size(size),
        )
        Text(text, color = MaterialTheme.colorScheme.error, style = style)
    }
}
