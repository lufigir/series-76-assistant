package com.series76.assistant.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

/** What a screen needs to show times as the reader's phone does: the local day, the time zone and 12 or 24 hours. */
data class LocalClock(val today: LocalDate, val zone: ZoneId, val use24Hour: Boolean)

/**
 * The [LocalClock], kept current while the screen shows: past midnight, after a change of time zone or clock, and on
 * coming back to the app, which also picks up the 12/24-hour switch. Without this, "Today" stays on yesterday for
 * anyone who leaves the app open overnight or lands in another time zone.
 */
@Composable
fun rememberLocalClock(): LocalClock {
    val context = LocalContext.current
    var clock by remember { mutableStateOf(readClock(context)) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    clock = readClock(context)
                }
            }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // System broadcasts reach a receiver that is not exported.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    // A timer instead of ACTION_DATE_CHANGED, which Android does not send reliably at midnight.
    LaunchedEffect(clock) {
        val now = ZonedDateTime.now(clock.zone)
        delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(clock.zone)).toMillis() + 1)
        clock = readClock(context)
    }
    LifecycleResumeEffect(context) {
        clock = readClock(context)
        onPauseOrDispose {}
    }
    return clock
}

private fun readClock(context: Context): LocalClock {
    val zone = ZoneId.systemDefault()
    return LocalClock(LocalDate.now(zone), zone, DateFormat.is24HourFormat(context))
}
