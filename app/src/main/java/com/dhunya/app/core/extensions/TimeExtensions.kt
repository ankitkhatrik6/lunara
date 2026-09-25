package com.dhunya.app.core.extensions

import java.util.Locale
import java.util.concurrent.TimeUnit

fun Long.formatDurationMs(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

fun Long.formatTimestampToLrc(): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) - TimeUnit.MINUTES.toSeconds(minutes)
    val hundredths = (this % 1000) / 10
    return String.format(Locale.getDefault(), "[%02d:%02d.%02d]", minutes, seconds, hundredths)
}
