@file:Suppress("MissingPermission", "DEPRECATION")
package dev.hraudio

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

internal val heartService: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val measurement = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val clientConfig = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

internal interface SensorLink {
    fun open(address: String)
    fun close()
}

internal class BleLink(context: Context, private val receive: (Int) -> Unit, private val lost: () -> Unit) : SensorLink {
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null

    override fun open(address: String) {
        if (adapter?.isEnabled == true) {
            gatt = adapter.getRemoteDevice(address).connectGatt(app, false, callbacks, BluetoothDevice.TRANSPORT_LE)
        }
    }

    override fun close() {
        gatt?.close()
        gatt = null
    }

    private fun onGatt(source: BluetoothGatt, block: () -> Unit) {
        handler.post { if (source === gatt) block() }
    }

    private val callbacks = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) = onGatt(g) {
            if (status == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED) {
                if (!g.discoverServices()) lost()
            } else if (state == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) lost()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = onGatt(g) {
            val c = g.getService(heartService)?.getCharacteristic(measurement)
            val descriptor = c?.getDescriptor(clientConfig)
            if (status != BluetoothGatt.GATT_SUCCESS || c == null || descriptor == null ||
                !g.setCharacteristicNotification(c, true)) {
                lost()
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (!g.writeDescriptor(descriptor)) lost()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) = onGatt(g) {
            if (status != BluetoothGatt.GATT_SUCCESS) lost()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (c.uuid != measurement) return
            val bpm = decodeBpm(c.value ?: return) ?: return
            onGatt(g) { receive(bpm) }
        }
    }

}
