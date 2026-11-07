package com.series76.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.series76.assistant.R

/** An icon on a tonal disc. Decorative: the text beside it always says the same thing. */
@Composable
fun IconBadge(
    painter: Painter,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 40.dp,
) {
    Box(modifier = modifier.size(size).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painter, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 40.dp,
) = IconBadge(rememberVectorPainter(icon), modifier, container, content, size)

/** The app's mark: the actuator glyph on the petrol primary, as on the launcher icon. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    IconBadge(
        painterResource(R.drawable.ic_actuator),
        modifier = modifier,
        container = MaterialTheme.colorScheme.primary,
        content = MaterialTheme.colorScheme.onPrimary,
        size = size,
    )
}
