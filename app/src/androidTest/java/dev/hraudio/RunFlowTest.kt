// SPDX-License-Identifier: MIT
package dev.hraudio

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain

class RunFlowTest {
    private val screen = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = RuleChain.outerRule(GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)).around(screen)

    @Test fun savedSensorRunAnnouncesReconnectsAndStops() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousOptions = context.loadOptions()
        val originalSensor = SessionHardware.sensor
        val originalSpeaker = SessionHardware.speaker
        val originalClock = SessionHardware.now
        val prefs = context.sensorPrefs()
        val formerAddress = prefs.getString("address", null)
        val formerLabel = prefs.getString("label", null)
        val phrases = mutableListOf<String>()
        var time = 1000L
        lateinit var sample: (Int) -> Unit
        lateinit var lost: () -> Unit
        var opens = 0
        var open = false
        try {
            screen.runOnIdle {
                context.saveOptions(RunOptions())
                prefs.edit().putString("address", "02:00:00:00:00:01").putString("label", "Test strap").commit()
                SessionHardware.now = { time }
                SessionHardware.sensor = { _, receive, disconnect ->
                    sample = receive
                    lost = disconnect
                    object : SensorLink {
                        override fun open(address: String) {
                            assertEquals("02:00:00:00:00:01", address)
                            opens++
                            open = true
                        }
                        override fun close() { open = false }
                    }
                }
                SessionHardware.speaker = { object : Speaker {
                    override val failed = false
                    override fun say(text: String) { phrases.add(text) }
                    override fun close() {}
                } }
            }
            screen.activityRule.scenario.recreate()
            screen.onNodeWithText("Settings").performClick()
            listOf(90, 110, 130, 150, 170).forEachIndexed { index, bpm ->
                screen.onNode(hasSetTextAction() and hasText("Zone ${index + 1} start"))
                    .performScrollTo().performTextReplacement(bpm.toString())
            }
            screen.onNode(hasSetTextAction() and hasText("BPM readout interval (seconds)"))
                .performScrollTo().performTextReplacement("15")
            screen.onNodeWithText("Done").performScrollTo().performClick()
            screen.activityRule.scenario.recreate()
            screen.runOnIdle {
                assertEquals(RunOptions(listOf(90, 110, 130, 150, 170), 15), context.loadOptions())
            }
            screen.onNodeWithText("Start").performClick()
            screen.waitUntil(5_000) { opens == 1 }
            screen.runOnIdle {
                sample(118)
                repeat(4) { time += 1_000; sample(118) }
                repeat(11) { time += 1_000; sample(134) }
            }
            screen.onNodeWithText("Zone 3").assertIsDisplayed()
            screen.onNodeWithText("134 BPM").assertIsDisplayed()
            screen.runOnIdle {
                assertEquals(listOf("Sensor connected. 118 beats per minute.", "Up to zone 3, 134 beats per minute."), phrases)
                time += 1_000
                sample(150)
                lost()
            }
            screen.onNodeWithText("— BPM").assertIsDisplayed()
            screen.waitUntil(8_000) { opens == 2 }
            screen.runOnIdle {
                time += 15_000
                sample(129)
                assertEquals(listOf("Heart rate signal lost.", "Sensor connected. 129 beats per minute."), phrases.takeLast(2))
            }
            screen.onNodeWithText("Stop").performClick()
            screen.onNodeWithText("Start").assertIsDisplayed()
            screen.runOnIdle { assertFalse(open) }
        } finally {
            screen.runOnIdle {
                context.stopService(android.content.Intent(context, RunService::class.java))
                context.saveOptions(previousOptions)
                SessionHardware.sensor = originalSensor
                SessionHardware.speaker = originalSpeaker
                SessionHardware.now = originalClock
                prefs.edit().putString("address", formerAddress).putString("label", formerLabel).commit()
            }
        }
    }
}
