package dev.hraudio

import android.Manifest
import android.content.Context
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
            screen.onNodeWithText("Start").performClick()
            screen.waitUntil(5_000) { opens == 1 }
            screen.runOnIdle {
                sample(118)
                repeat(60) { time += 1_000; sample(134) }
            }
            screen.onNodeWithText("134 BPM").assertIsDisplayed()
            screen.runOnIdle {
                assertEquals(listOf("Sensor connected. 118 beats per minute.", "134 beats per minute."), phrases)
                lost()
            }
            screen.onNodeWithText("— BPM").assertIsDisplayed()
            screen.waitUntil(8_000) { opens == 2 }
            screen.runOnIdle {
                sample(129)
                assertEquals(listOf("Heart rate signal lost.", "Sensor connected. 129 beats per minute."), phrases.takeLast(2))
            }
            screen.onNodeWithText("Stop").performClick()
            screen.onNodeWithText("Start").assertIsDisplayed()
            screen.runOnIdle { assertFalse(open) }
        } finally {
            screen.runOnIdle {
                context.stopService(android.content.Intent(context, RunService::class.java))
                SessionHardware.sensor = originalSensor
                SessionHardware.speaker = originalSpeaker
                SessionHardware.now = originalClock
                prefs.edit().putString("address", formerAddress).putString("label", formerLabel).commit()
            }
        }
    }
}
