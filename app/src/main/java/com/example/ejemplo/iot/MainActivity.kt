package com.ejemplo.iot

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ejemplo.iot.databinding.ActivityMainBinding
import com.ejemplo.iot.ui.MainViewModel
import com.ejemplo.iot.ui.SensorAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var sensorAdapter: SensorAdapter
    private lateinit var bluetoothService: BluetoothService

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.all { it.value } -> {
                Toast.makeText(this, "Permisos Bluetooth concedidos", Toast.LENGTH_SHORT).show()
                showPairedDevicesDialog()
            }
            else -> {
                Toast.makeText(this, "Se necesitan permisos Bluetooth para conectar", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupRecyclerView()
        setupBluetooth()
        setupListeners()
        observeBluetoothState()

        viewModel.loadData()
    }

    private fun setupViewModel() {
        viewModel = MainViewModel()
        lifecycleScope.launch {
            viewModel.sensorReadings.collect { readings ->
                sensorAdapter.submitList(readings)
                updateUI()
            }
        }

        lifecycleScope.launch {
            viewModel.iaAdvice.collect { advice ->
                if (advice.isNotEmpty()) {
                    binding.textLatestAdvice.text = advice[0].mensaje
                }
            }
        }

        lifecycleScope.launch {
            viewModel.statistics.collect { stats ->
                stats?.let {
                    binding.textAvgTemp.text = "Prom: ${String.format("%.1f", it.avgTemperature)}°C"
                    binding.textMaxTemp.text = "Máx: ${String.format("%.1f", it.maxTemperature)}°C"
                    binding.textMinTemp.text = "Mín: ${String.format("%.1f", it.minTemperature)}°C"
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                    binding.textError.text = it
                    binding.textError.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private fun setupRecyclerView() {
        sensorAdapter = SensorAdapter()
        binding.recyclerViewSensors.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSensors.adapter = sensorAdapter
    }

    private fun setupBluetooth() {
        bluetoothService = BluetoothService(this)

        binding.buttonConnectBluetooth.setOnClickListener {
            checkBluetoothPermissions()
        }

        binding.buttonRequestData.setOnClickListener {
            bluetoothService.sendCommand("GET_DATA")
            Toast.makeText(this, "Solicitando datos al ESP32...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeBluetoothState() {
        lifecycleScope.launch {
            bluetoothService.connectionState.collect { state ->
                when (state) {
                    BluetoothService.ConnectionState.CONNECTED -> {
                        binding.textBluetoothStatus.text = "✅ ESP32 Conectado"
                        binding.textBluetoothStatus.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                        binding.buttonConnectBluetooth.text = "Desconectar"
                        Toast.makeText(this@MainActivity, "¡ESP32 conectado!", Toast.LENGTH_SHORT).show()
                        startBluetoothPolling()
                    }
                    BluetoothService.ConnectionState.CONNECTING -> {
                        binding.textBluetoothStatus.text = "🔄 Conectando..."
                        binding.textBluetoothStatus.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark))
                    }
                    BluetoothService.ConnectionState.DISCONNECTED -> {
                        binding.textBluetoothStatus.text = "❌ Desconectado"
                        binding.textBluetoothStatus.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                        binding.buttonConnectBluetooth.text = "Conectar ESP32"
                    }
                }
            }
        }

        lifecycleScope.launch {
            bluetoothService.sensorData.collect { data ->
                if (data.temperatura != 0f || data.sonido > 0) {
                    binding.textBluetoothTemp.text = "Temp BT: ${String.format("%.1f", data.temperatura)}°C"
                    binding.textBluetoothSound.text = "Ruido BT: ${data.sonido}%"
                    binding.textBluetoothPresence.text = if (data.presencia) "👤 Detectado" else "👤 Sin presencia"
                }
            }
        }
    }

    private fun startBluetoothPolling() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (bluetoothService.connectionState.value == BluetoothService.ConnectionState.CONNECTED) {
                    bluetoothService.sendCommand("GET_DATA")
                    handler.postDelayed(this, 5000)
                }
            }
        }
        handler.post(runnable)
    }

    private fun checkBluetoothPermissions() {
        when {
            bluetoothService.connectionState.value == BluetoothService.ConnectionState.CONNECTED -> {
                bluetoothService.disconnect()
                return
            }
        }

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            showPairedDevicesDialog()
        } else {
            bluetoothPermissionLauncher.launch(permissions)
        }
    }

    private fun showPairedDevicesDialog() {
        val devices = bluetoothService.getPairedDevices()
        if (devices.isEmpty()) {
            Toast.makeText(this, "No hay dispositivos emparejados. Empareja tu ESP32 en Configuración > Bluetooth", Toast.LENGTH_LONG).show()
            return
        }

        val esp32Devices = devices.filter {
            it.name?.contains("ESP32", ignoreCase = true) == true
        }

        val deviceList = if (esp32Devices.isNotEmpty()) esp32Devices else devices
        val deviceNames = deviceList.map { "${it.name} (${it.address})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Seleccionar ESP32")
            .setItems(deviceNames) { _, which ->
                val device = deviceList[which]
                bluetoothService.connectToDevice(device.address)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadData()
            binding.swipeRefresh.isRefreshing = false
        }

        binding.buttonAnalyze.setOnClickListener {
            viewModel.triggerAnalysis()
            Toast.makeText(this, "Analizando datos con IA...", Toast.LENGTH_SHORT).show()
        }

        binding.buttonRefreshApi.setOnClickListener {
            viewModel.loadData()
        }
    }

    private fun updateUI() {
        // UI updates adicionales
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothService.disconnect()
    }
}