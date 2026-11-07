package com.series76.assistant.ui.debug

import java.math.BigDecimal
import java.math.MathContext

// A turn costs a fraction of a cent, and Double.toString prints 2.05804E-4, which reads as two dollars.
fun formatCost(costUsd: Double?): String =
    costUsd?.let { "USD ${BigDecimal(it).round(MathContext(2)).toPlainString()}" } ?: "not reported"
