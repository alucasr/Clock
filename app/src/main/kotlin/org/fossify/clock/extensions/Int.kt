package org.fossify.clock.extensions

import java.util.concurrent.TimeUnit

val Int.secondsToMillis get() = TimeUnit.SECONDS.toMillis(this.toLong())
val Int.millisToSeconds get() = TimeUnit.MILLISECONDS.toSeconds(this.toLong())

fun Int.isBitSet(bit: Int) = (this shr bit and 1) > 0

/**
 * Compact human-readable duration for the Routines list, e.g. 5400 -> "1h 30m", 90 -> "1m 30s",
 * 1800 -> "30m". Omits any unit (h/m/s) whose value is 0, unlike the hh:mm:ss format used
 * elsewhere (timer/routine edit dialog). Falls back to "0s" if the whole duration is 0.
 */
fun Int.toCompactDurationString(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60

    val parts = mutableListOf<String>()
    if (hours > 0) parts.add("${hours}h")
    if (minutes > 0) parts.add("${minutes}m")
    if (seconds > 0) parts.add("${seconds}s")

    return if (parts.isEmpty()) "0s" else parts.joinToString(" ")
}
