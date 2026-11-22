@file:Suppress("MissingPermission", "DEPRECATION")
package dev.hraudio

import android.app.*
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.compose.runtime.*
import java.util.UUID

internal val heartService: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val measurement = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val clientConfig = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
internal data class RunState(val active: Boolean = false, val status: String = "Ready", val bpm: Int? = null)
internal object Session { var state by mutableStateOf(RunState()) }
internal fun Context.sensorPrefs() = getSharedPreferences("sensor", Context.MODE_PRIVATE)

class RunService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var address = ""
    private var label = ""
    private lateinit var voice: Voice
    private var cadence = Readout()
    private var lastSample = 0L
    private var saved = false
    private var heardSignal = false

    override fun onCreate() {
        super.onCreate()
        voice = Voice(this)
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
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        try {
            if (adapter?.isEnabled == true) {
                gatt = adapter.getRemoteDevice(address).connectGatt(this, false, callbacks, BluetoothDevice.TRANSPORT_LE)
            }
            handler.postDelayed(timeout, if (gatt == null) 5_000 else 20_000)
        } catch (_: SecurityException) { stopSelf() }
    }

    private val timeout = Runnable { retry() }
    private fun retry() {
        handler.removeCallbacks(timeout)
        gatt?.close()
        gatt = null
        if (heardSignal) voice.say("Heart rate signal lost.")
        heardSignal = false
        cadence = Readout()
        Session.state = RunState(true, "Looking for $label")
        handler.postDelayed({ connect() }, 3_000)
    }

    private fun onGatt(source: BluetoothGatt, block: () -> Unit) {
        handler.post { if (source === gatt) block() }
    }

    private val callbacks = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) = onGatt(g) {
            if (status == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED) {
                if (!g.discoverServices()) retry()
            } else if (state == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) retry()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = onGatt(g) {
            val c = g.getService(heartService)?.getCharacteristic(measurement)
            val descriptor = c?.getDescriptor(clientConfig)
            if (status != BluetoothGatt.GATT_SUCCESS || c == null || descriptor == null ||
                !g.setCharacteristicNotification(c, true)) {
                retry()
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (!g.writeDescriptor(descriptor)) retry()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) = onGatt(g) {
            if (status != BluetoothGatt.GATT_SUCCESS) retry()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (c.uuid != measurement) return
            val bpm = decodeBpm(c.value ?: return) ?: return
            onGatt(g) { sample(bpm) }
        }
    }

    private fun sample(bpm: Int) {
        val now = SystemClock.elapsedRealtime()
        if (heardSignal && now - lastSample >= 10_000) cadence = Readout()
        lastSample = now
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 10_000)
        if (!saved) {
            sensorPrefs().edit().putString("address", address).putString("label", label).apply()
            saved = true
        }
        heardSignal = true
        Session.state = RunState(true, if (voice.failed) "Connected · speech unavailable" else "Connected to $label", bpm)
        cadence.phrase(bpm, now)?.let(voice::say)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        gatt?.close()
        gatt = null
        voice.close()
        Session.state = RunState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
