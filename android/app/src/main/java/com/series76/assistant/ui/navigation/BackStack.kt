package com.series76.assistant.ui.navigation

/**
 * Leaves [key]'s screen, and only if it is still on top. A screen stays tappable while it slides away, so a second tap
 * on its back arrow would otherwise pop the screen beneath it, and popping the last one crashes NavDisplay.
 */
fun <T> MutableList<T>.popIfOnTop(key: T) {
    if (size > 1 && lastOrNull() == key) removeAt(lastIndex)
}
