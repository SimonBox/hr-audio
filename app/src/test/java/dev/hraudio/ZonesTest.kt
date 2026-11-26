// SPDX-License-Identifier: MIT
package dev.hraudio

import org.junit.Assert.*
import org.junit.Test

class ZonesTest {
    private val options = RunOptions(listOf(90, 110, 130, 150, 170), seconds = 0)
    @Test fun classifiesInclusiveBoundsAndOpenEnds() {
        assertEquals(listOf(0, 1, 1, 2, 3, 4, 5, 5),
            listOf(89, 90, 109, 110, 130, 150, 170, 200).map(options::zone))
        assertNull(RunOptions().zone(150))
    }
    @Test fun rejectsOverlappingBounds() {
        assertThrows(IllegalArgumentException::class.java) { options.copy(bounds = listOf(90, 110, 130, 130, 170)) }
    }
    @Test fun jitterAndSkippedZonesRestartDwellInBothDirections() {
        val run = Readout(options)
        val samples = listOf(0 to 149, 1 to 150, 8 to 149, 9 to 150, 16 to 170, 25 to 170,
            26 to 170, 27 to 149, 34 to 149, 37 to 149, 38 to 89, 46 to 89, 48 to 89)
        assertEquals(listOf("Sensor connected. 149 beats per minute.", "Up to zone 5.",
            "Down to zone 3.", "Below zone 1."), samples.mapNotNull { (s,b) -> run.phrase(b,s*1000L) })
    }
    @Test fun combinesCuesAndKeepsCadenceWhenZonesAreMuted() {
        for (enabled in listOf(true, false)) {
            val run = Readout(options.copy(seconds = 15, zoneAlerts = enabled))
            run.phrase(149, 0)
            for (second in 5..14) assertNull(run.phrase(150, second * 1000L))
            assertEquals(if (enabled) "Up to zone 4, 150 beats per minute." else "150 beats per minute.",
                run.phrase(150, 15_000))
        }
    }
}
