package com.series76.assistant.ui.debug

import com.series76.assistant.data.remote.ModelCallTrace

// Providers report the model they ran, sometimes with a dated suffix, so a prefix of the configured name still counts as it.
fun fallbackModels(configured: String, calls: List<ModelCallTrace>): List<String> =
    calls.mapNotNull { it.model }.filterNot { it.startsWith(configured) }.distinct()
