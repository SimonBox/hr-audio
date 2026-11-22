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

/** Driven only by newly received measurements. Never repeats a cached BPM on a timer. */
class Readout(private val intervalMs: Long = 60_000) {
    private var lastAnnouncement: Long? = null

    fun phrase(bpm: Int, elapsedMs: Long): String? {
        val previous = lastAnnouncement
        if (previous != null && elapsedMs - previous < intervalMs) return null
        lastAnnouncement = elapsedMs
        val prefix = if (previous == null) "Sensor connected. " else ""
        return "${prefix}$bpm beats per minute."
    }
}
