@file:Suppress("MissingPermission")
package dev.hraudio

import android.Manifest.permission.*
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class Sensor(val address: String, val label: String)

class MainActivity : ComponentActivity() {
    private val nearby = mutableStateListOf<Sensor>()
    private var scanning by mutableStateOf(false)
    private var message by mutableStateOf("")
    private var scan: ScanCallback? = null
    private var scanner: BluetoothLeScanner? = null
    private val timer = Handler(Looper.getMainLooper())
    private var pending: (() -> Unit)? = null
    private lateinit var preview: Voice
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action = pending
        pending = null
        if (hasBluetooth()) action?.invoke() else message = "Allow Nearby devices in app settings to use a sensor."
    }

    private fun hasBluetooth() = listOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT).all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
    private fun withBluetooth(action: () -> Unit) {
        if (hasBluetooth()) { action(); return }
        pending = action
        val required = mutableListOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) required.add(POST_NOTIFICATIONS)
        permissions.launch(required.toTypedArray())
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        preview = Voice(this)
        setContent {
            val prefs = sensorPrefs()
            val address = prefs.getString("address", null)
            val saved = address?.let { Sensor(it, prefs.getString("label", null) ?: "Heart-rate sensor") }
            val run = Session.state
            MaterialTheme {
                RunScreen(run, saved, scanning, nearby, message,
                    find = { withBluetooth { findSensors() } },
                    start = { sensor -> withBluetooth { start(sensor) } },
                    stop = { stopService(Intent(this, RunService::class.java)) },
                    test = {
                        if (preview.failed) message = "Install or enable an English text-to-speech voice in Android settings."
                        else preview.say("HR Audio voice test.")
                    })
            }
        }
    }

    private fun findSensors() {
        stopScan()
        nearby.clear()
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        scanner = adapter?.bluetoothLeScanner
        if (scanner == null) { message = "Turn on Bluetooth and try again."; return }
        val callback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                if (scan !== this) return
                val device = result.device
                if (nearby.none { it.address == device.address }) {
                    nearby.add(Sensor(device.address, result.scanRecord?.deviceName ?: device.name ?: "Heart-rate sensor"))
                }
            }
            override fun onScanFailed(code: Int) {
                if (scan === this) { stopScan(); message = "Scan failed ($code). Try again." }
            }
        }
        scan = callback
        scanning = true
        message = "Enable Heart Rate Broadcast on your strap."
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(heartService)).build()
        scanner?.startScan(listOf(filter), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        timer.postDelayed({
            stopScan()
            message = if (nearby.isEmpty()) "No sensors found. Check broadcast and scan again." else "Choose your sensor."
        }, 15_000)
    }

    private fun start(sensor: Sensor) {
        stopScan()
        nearby.clear()
        message = ""
        startForegroundService(Intent(this, RunService::class.java)
            .putExtra("address", sensor.address).putExtra("label", sensor.label))
    }
    private fun stopScan() {
        val callback = scan
        scan = null
        if (callback != null && hasBluetooth()) scanner?.stopScan(callback)
        scanning = false
        timer.removeCallbacksAndMessages(null)
    }
    override fun onStop() { stopScan(); super.onStop() }
    override fun onDestroy() { preview.close(); super.onDestroy() }
}

@Composable
internal fun RunScreen(run: RunState, saved: Sensor?, scanning: Boolean, devices: List<Sensor>,
                      message: String, find: () -> Unit, start: (Sensor) -> Unit, stop: () -> Unit, test: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("HR Audio", style = MaterialTheme.typography.headlineMedium)
            Text(run.bpm?.let { "$it BPM" } ?: "— BPM", style = MaterialTheme.typography.displayLarge)
            Text(if (run.active) run.status else saved?.let { "Saved sensor · ${it.label}" } ?: "Choose a heart-rate sensor to begin.")
            if (run.active) {
                Button(onClick = stop) { Text("Stop") }
            } else {
                if (saved != null) Button(onClick = { start(saved) }) { Text("Start") }
                OutlinedButton(onClick = find, enabled = !scanning) {
                    Text(if (scanning) "Scanning…" else if (saved == null) "Choose sensor" else "Change sensor")
                }
                devices.forEach { sensor ->
                    OutlinedButton(onClick = { start(sensor) }) { Text("${sensor.label} · ${sensor.address}") }
                }
                TextButton(onClick = test) { Text("Test voice") }
                if (message.isNotEmpty()) Text(message)
            }
        }
    }
}
