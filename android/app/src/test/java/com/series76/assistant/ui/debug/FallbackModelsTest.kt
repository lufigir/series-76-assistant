package com.series76.assistant.ui.debug

import com.series76.assistant.data.remote.ModelCallTrace
import org.junit.Assert.assertEquals
import org.junit.Test

class FallbackModelsTest {
    private fun call(model: String?) = ModelCallTrace(model = model, latencyMs = 1)

    @Test
    fun `calls the configured model answered name no fallback, even with a dated suffix`() {
        val calls = listOf(call("deepseek/deepseek-v4.1-flash"), call("deepseek/deepseek-v4.1-flash-20261020"))
        assertEquals(emptyList<String>(), fallbackModels("deepseek/deepseek-v4.1-flash", calls))
    }

    @Test
    fun `a call another model answered names that model once`() {
        val calls = listOf(call("google/gemini-3.5-flash-lite"), call("google/gemini-3.5-flash-lite"))
        assertEquals(
            listOf("google/gemini-3.5-flash-lite"),
            fallbackModels("deepseek/deepseek-v4.1-flash", calls),
        )
    }

    @Test
    fun `a call that reported no model is not a fallback`() {
        assertEquals(emptyList<String>(), fallbackModels("deepseek/deepseek-v4.1-flash", listOf(call(null))))
    }
}
