package com.ejemplo.iot

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.UUID

class BluetoothService(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothService"
        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    // Usar BluetoothManager en lugar del deprecated getDefaultAdapter()
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothSocket: BluetoothSocket? = null
    private var isReading = false

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

    data class SensorDataBT(
        val temperatura: Float = 0f,
        val humedad: Float = 0f,
        val sonido: Int = 0,
        val presencia: Boolean = false,
        val timestamp: Long = 0
    )

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _sensorData = MutableStateFlow(SensorDataBT())
    val sensorData: StateFlow<SensorDataBT> = _sensorData.asStateFlow()

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    fun getPairedDevices(): List<BluetoothDevice> {
        if (ActivityCompat.checkSelfPermission(
                context, android.Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun connectToDevice(deviceAddress: String) {
        if (ActivityCompat.checkSelfPermission(
                context, android.Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        _connectionState.value = ConnectionState.CONNECTING

        Thread {
            try {
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                    ?: run {
                        _connectionState.value = ConnectionState.DISCONNECTED
                        return@Thread
                    }

                if (ActivityCompat.checkSelfPermission(
                        context, android.Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothAdapter?.cancelDiscovery()
                }

                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothSocket?.connect()
                _connectionState.value = ConnectionState.CONNECTED
                Log.d(TAG, "Conectado a: ${device.address}")
                startReading()

            } catch (e: IOException) {
                Log.e(TAG, "Error al conectar: ${e.message}")
                _connectionState.value = ConnectionState.DISCONNECTED
                closeSocket()
            }
        }.start()
    }

    private fun startReading() {
        isReading = true
        Thread {
            try {
                val inputStream = bluetoothSocket?.inputStream
                val buffer = ByteArray(1024)
                while (isReading && _connectionState.value == ConnectionState.CONNECTED) {
                    if ((inputStream?.available() ?: 0) > 0) {
                        val bytes = inputStream?.read(buffer) ?: 0
                        if (bytes > 0) parseSensorData(String(buffer, 0, bytes))
                    }
                    Thread.sleep(100)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error leyendo: ${e.message}")
                disconnect()
            }
        }.start()
    }

    private fun parseSensorData(data: String) {
        try {
            val temp = Regex("TEMP:([\\d.-]+)").find(data)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val hum = Regex("HUM:([\\d.-]+)").find(data)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val sound = Regex("SOUND:(\\d+)").find(data)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val presence = Regex("PIR:([01])").find(data)?.groupValues?.get(1) == "1"
            if (temp != 0f || sound > 0) {
                _sensorData.value = SensorDataBT(temp, hum, sound, presence, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando: ${e.message}")
        }
    }

    fun sendCommand(command: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        Thread {
            try {
                bluetoothSocket?.outputStream?.write("$command\n".toByteArray())
                bluetoothSocket?.outputStream?.flush()
            } catch (e: IOException) {
                disconnect()
            }
        }.start()
    }

    fun disconnect() {
        isReading = false
        closeSocket()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun closeSocket() {
        try { bluetoothSocket?.close() } catch (e: IOException) { /* ignorar */ }
        bluetoothSocket = null
    }
}