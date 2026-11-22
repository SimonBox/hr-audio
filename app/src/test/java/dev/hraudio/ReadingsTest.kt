package dev.hraudio

import org.junit.Assert.*
import org.junit.Test

class ReadingsTest {
    @Test fun decodesBothFormatsAndIgnoresTrailingFields() {
        assertEquals(150, decodeBpm(byteArrayOf(0, 150.toByte())))
        assertEquals(300, decodeBpm(byteArrayOf(17, 44, 1, 0, 0)))
    }

    @Test fun ignoresMalformedOrNoContactReadings() {
        listOf(byteArrayOf(), byteArrayOf(0), byteArrayOf(1, 9), byteArrayOf(0, 0),
            byteArrayOf(4, 100)).forEach { assertNull(decodeBpm(it)) }
        assertEquals(100, decodeBpm(byteArrayOf(6, 100)))
    }

    @Test fun announcesFreshReadingsAtMinuteBoundariesWithoutCatchUp() {
        val run = Readout()
        assertEquals("Sensor connected. 110 beats per minute.", run.phrase(110, 100))
        assertNull(run.phrase(119, 60_099))
        assertEquals("120 beats per minute.", run.phrase(120, 60_100))
        assertEquals("130 beats per minute.", run.phrase(130, 300_000))
        assertNull(run.phrase(131, 300_001))
    }
}
