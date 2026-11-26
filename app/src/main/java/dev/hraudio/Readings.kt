// SPDX-License-Identifier: MIT
package dev.hraudio

/** Bluetooth SIG Heart Rate Measurement. Bit 0 selects the width of the LE value. */
fun decodeBpm(packet: ByteArray): Int? {
    if (packet.size < 2) return null
    val flags = packet[0].toInt()
    if (flags and 6 == 4) return null // Contact detection supported, contact absent.
    val low = packet[1].toInt() and 255
    val value = if (flags and 1 == 0) low else {
        if (packet.size < 3) return null
        low or ((packet[2].toInt() and 255) shl 8)
    }
    return value.takeIf { it > 0 }
}

internal data class RunOptions(
    val bounds: List<Int> = emptyList(),
    val seconds: Int = 60,
    val zoneAlerts: Boolean = true
) {
    init {
        require(bounds.isEmpty() || (bounds.size == 5 && bounds.first() > 0 &&
            bounds.zipWithNext().all { (lower, upper) -> lower < upper }))
        require(seconds >= 0)
    }

    fun zone(bpm: Int): Int? = if (bounds.isEmpty()) null else bounds.count { bpm >= it }
}

internal fun zoneName(zone: Int) = if (zone == 0) "Below zone 1" else "Zone $zone"

/** Only fresh samples advance the timers. Create a new instance after signal loss. */
internal class Readout(private val options: RunOptions = RunOptions()) {
    private var lastPeriodic: Long? = null
    private var confirmed: Int? = null
    private var candidate: Int? = null
    private var since = 0L

    fun phrase(bpm: Int, elapsedMs: Long): String? {
        val transition = options.zone(bpm)?.let { transition(it, elapsedMs) }
        val previous = lastPeriodic
        val periodic = previous == null || (options.seconds > 0 && elapsedMs - previous >= options.seconds * 1000L)
        val cue = transition.takeIf { options.zoneAlerts }
        if (!periodic) return cue?.let { "$it." }
        lastPeriodic = elapsedMs
        val prefix = when {
            previous == null -> "Sensor connected. "
            cue != null -> "$cue, "
            else -> ""
        }
        return "${prefix}$bpm beats per minute."
    }

    private fun transition(zone: Int, now: Long): String? {
        val old = confirmed
        if (old == null || zone == old) {
            confirmed = zone
            candidate = null
            return null
        }
        if (candidate != zone) {
            candidate = zone
            since = now
            return null
        }
        if (now - since < 10_000) return null
        confirmed = zone
        candidate = null
        return when {
            zone == 0 -> "Below zone 1"
            zone > old -> "Up to zone $zone"
            else -> "Down to zone $zone"
        }
    }
}
