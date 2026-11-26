// SPDX-License-Identifier: MIT
@file:Suppress("MissingPermission", "DEPRECATION")
package dev.hraudio

import android.app.*
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.compose.runtime.*


internal data class RunState(val active: Boolean = false, val status: String = "Ready", val bpm: Int? = null, val zone: Int? = null)
internal object Session { var state by mutableStateOf(RunState()) }
internal fun Context.sensorPrefs() = getSharedPreferences("sensor", Context.MODE_PRIVATE)

// Instrumentation swaps only hardware boundaries; the actual activity and service still run.
internal object SessionHardware {
    var sensor: (Context, (Int) -> Unit, () -> Unit) -> SensorLink = ::BleLink
    var speaker: (Context) -> Speaker = ::Voice
    var now: () -> Long = SystemClock::elapsedRealtime
}

class RunService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var link: SensorLink
    private var address = ""
    private var label = ""
    private lateinit var voice: Speaker
    private lateinit var options: RunOptions
    private lateinit var cadence: Readout
    private var lastSample = 0L
    private var saved = false
    private var heardSignal = false

    override fun onCreate() {
        super.onCreate()
        options = loadOptions()
        cadence = Readout(options)
        link = SessionHardware.sensor(this, ::sample, ::retry)
        voice = SessionHardware.speaker(this)
        val channel = NotificationChannel("run", "Run session", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(this, 1, Intent(this, RunService::class.java).setAction("STOP"), flags)
        return Notification.Builder(this, "run").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("HR Audio").setContentText("Monitoring $label").setContentIntent(open)
            .setOnlyAlertOnce(true).setOngoing(true).addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (Session.state.active) return START_NOT_STICKY
        address = intent?.getStringExtra("address").orEmpty()
        label = intent?.getStringExtra("label") ?: "sensor"
        if (!BluetoothAdapter.checkBluetoothAddress(address)) { stopSelf(); return START_NOT_STICKY }
        startForeground(1, notification())
        connect()
        return START_NOT_STICKY
    }

    private fun connect() {
        Session.state = RunState(true, "Looking for $label")
        try {
            link.open(address)
            handler.postDelayed(timeout, 20_000)
        } catch (_: SecurityException) { stopSelf() }
    }

    private val timeout = Runnable { retry() }
    private fun retry() {
        handler.removeCallbacks(timeout)
        link.close()
        if (heardSignal) voice.say("Heart rate signal lost.")
        heardSignal = false
        cadence = Readout(options)
        Session.state = RunState(true, "Looking for $label")
        handler.postDelayed({ connect() }, 3_000)
    }

    private fun sample(bpm: Int) {
        val now = SessionHardware.now()
        if (heardSignal && now - lastSample >= 10_000) cadence = Readout(options)
        lastSample = now
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 10_000)
        if (!saved) {
            sensorPrefs().edit().putString("address", address).putString("label", label).apply()
            saved = true
        }
        heardSignal = true
        Session.state = RunState(true, if (voice.failed) "Connected · speech unavailable" else "Connected to $label", bpm, options.zone(bpm))
        cadence.phrase(bpm, now)?.let(voice::say)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        link.close()
        voice.close()
        Session.state = RunState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
