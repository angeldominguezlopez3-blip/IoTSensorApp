package com.ejemplo.iot

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
        // UUID estándar para comunicación serial SPP
        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var connectedDevice: BluetoothDevice? = null
    private var isReading = false

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED
    }

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

    fun getPairedDevices(): List<BluetoothDevice> {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permiso BLUETOOTH_CONNECT no concedido")
            return emptyList()
        }

        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun connectToDevice(deviceAddress: String) {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permiso BLUETOOTH_CONNECT no concedido")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING

        Thread {
            try {
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                if (device == null) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@Thread
                }

                // Cancelar descubrimiento antes de conectar
                bluetoothAdapter?.cancelDiscovery()

                // Crear y conectar socket
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothSocket?.connect()

                connectedDevice = device
                _connectionState.value = ConnectionState.CONNECTED
                Log.d(TAG, "Conectado a: ${device.name} - ${device.address}")

                // Iniciar lectura de datos
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
                    if (inputStream?.available() ?: 0 > 0) {
                        val bytes = inputStream.read(buffer)
                        if (bytes > 0) {
                            val data = String(buffer, 0, bytes)
                            parseSensorData(data)
                            Log.d(TAG, "Datos recibidos: $data")
                        }
                    }
                    Thread.sleep(100)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error leyendo datos: ${e.message}")
                disconnect()
            }
        }.start()
    }

    private fun parseSensorData(data: String) {
        // Formato esperado: "TEMP:28.5;HUM:65.0;SOUND:45;PIR:1"
        try {
            val tempMatch = Regex("TEMP:([\\d.-]+)").find(data)
            val humMatch = Regex("HUM:([\\d.-]+)").find(data)
            val soundMatch = Regex("SOUND:(\\d+)").find(data)
            val pirMatch = Regex("PIR:([01])").find(data)

            val temp = tempMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val hum = humMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val sound = soundMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val presence = pirMatch?.groupValues?.get(1) == "1"

            if (temp != 0f || sound > 0) {
                _sensorData.value = SensorDataBT(
                    temperatura = temp,
                    humedad = hum,
                    sonido = sound,
                    presencia = presence,
                    timestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando datos: ${e.message}")
        }
    }

    fun sendCommand(command: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "No conectado, no se puede enviar comando")
            return
        }

        Thread {
            try {
                bluetoothSocket?.outputStream?.write("$command\n".toByteArray())
                bluetoothSocket?.outputStream?.flush()
                Log.d(TAG, "Comando enviado: $command")
            } catch (e: IOException) {
                Log.e(TAG, "Error enviando comando: ${e.message}")
                disconnect()
            }
        }.start()
    }

    fun disconnect() {
        isReading = false
        closeSocket()
        _connectionState.value = ConnectionState.DISCONNECTED
        connectedDevice = null
    }

    private fun closeSocket() {
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error cerrando socket: ${e.message}")
        }
        bluetoothSocket = null
    }
}