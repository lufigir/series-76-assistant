package com.series76.assistant.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class BackStackTest {
    @Test
    fun `a repeated back from a screen pops only that screen`() {
        val stack = mutableListOf("list", "chat")
        stack.popIfOnTop("chat")
        stack.popIfOnTop("chat")
        assertEquals(listOf("list"), stack)
    }

    @Test
    fun `back from a screen that is not on top changes nothing`() {
        val stack = mutableListOf("list", "chat", "debug")
        stack.popIfOnTop("chat")
        assertEquals(listOf("list", "chat", "debug"), stack)
    }

    @Test
    fun `the last screen is never popped`() {
        val stack = mutableListOf("list")
        stack.popIfOnTop("list")
        assertEquals(listOf("list"), stack)
    }
}
