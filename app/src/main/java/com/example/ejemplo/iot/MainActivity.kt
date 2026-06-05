package com.ejemplo.iot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
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

    private val adviceHandler = Handler(Looper.getMainLooper())
    private val adviceRunnable = object : Runnable {
        override fun run() {
            viewModel.loadAdvice()
            adviceHandler.postDelayed(this, 30_000)
        }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            Toast.makeText(this, "Permisos Bluetooth concedidos", Toast.LENGTH_SHORT).show()
            showPairedDevicesDialog()
        } else {
            Toast.makeText(this, "Se necesitan permisos Bluetooth para conectar", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textError.text = "🔍 Buscando servidor en la red..."
        binding.textError.visibility = android.view.View.VISIBLE

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupRecyclerView()
        setupBluetooth()
        setupListeners()
        observeViewModel()
        observeBluetoothState()

        viewModel.loadData()
        adviceHandler.postDelayed(adviceRunnable, 30_000)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.sensorReadings.collect { readings ->
                sensorAdapter.submitList(readings)
                if (readings.isNotEmpty()) {
                    val latest = readings[0]
                    val dateFormat = java.text.SimpleDateFormat(
                        "HH:mm:ss dd/MM/yyyy", java.util.Locale.getDefault()
                    )
                    binding.textCurrentTemp.text = "${String.format("%.1f", latest.temperatura)}°C"
                    binding.textCurrentSound.text = "${latest.sonido}%"
                    binding.textCurrentPresence.text = if (latest.presencia) "👤 Sí" else "👤 No"
                    binding.textCurrentTime.text = "Última act: ${dateFormat.format(latest.timestamp)}"
                    binding.textCurrentTemp.setTextColor(
                        when {
                            latest.temperatura > 30 -> android.graphics.Color.rgb(200, 50, 50)
                            latest.temperatura < 15 -> android.graphics.Color.rgb(50, 100, 200)
                            else -> android.graphics.Color.rgb(20, 100, 20)
                        }
                    )
                }
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
                binding.progressBar.visibility =
                    if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
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

        lifecycleScope.launch {
            viewModel.serverFound.collect { found ->
                when (found) {
                    true -> binding.textError.visibility = android.view.View.GONE
                    false -> {
                        binding.textError.text = "⚠️ Servidor no encontrado en la red local"
                        binding.textError.visibility = android.view.View.VISIBLE
                    }
                    null -> { /* pendiente */ }
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
                        binding.textBluetoothStatus.setTextColor(
                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark)
                        )
                        binding.buttonConnectBluetooth.text = "Desconectar"
                        Toast.makeText(this@MainActivity, "¡ESP32 conectado!", Toast.LENGTH_SHORT).show()
                        startBluetoothPolling()
                    }
                    BluetoothService.ConnectionState.CONNECTING -> {
                        binding.textBluetoothStatus.text = "🔄 Conectando..."
                        binding.textBluetoothStatus.setTextColor(
                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark)
                        )
                    }
                    BluetoothService.ConnectionState.DISCONNECTED -> {
                        binding.textBluetoothStatus.text = "❌ Desconectado"
                        binding.textBluetoothStatus.setTextColor(
                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark)
                        )
                        binding.buttonConnectBluetooth.text = "Conectar ESP32"
                    }
                }
            }
        }

        lifecycleScope.launch {
            bluetoothService.sensorData.collect { data ->
                if (data.temperatura != 0f || data.sonido > 0) {
                    binding.textBluetoothTemp.text =
                        "Temp BT: ${String.format("%.1f", data.temperatura)}°C"
                    binding.textBluetoothSound.text = "Ruido BT: ${data.sonido}%"
                    binding.textBluetoothPresence.text =
                        if (data.presencia) "👤 Detectado" else "👤 Sin presencia"
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
        if (bluetoothService.connectionState.value == BluetoothService.ConnectionState.CONNECTED) {
            bluetoothService.disconnect()
            return
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

        if (permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            showPairedDevicesDialog()
        } else {
            bluetoothPermissionLauncher.launch(permissions)
        }
    }

    private fun showPairedDevicesDialog() {
        // Verificar permiso BLUETOOTH_CONNECT antes de llamar .name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Permiso Bluetooth no concedido", Toast.LENGTH_SHORT).show()
            return
        }

        val devices = bluetoothService.getPairedDevices()
        if (devices.isEmpty()) {
            Toast.makeText(
                this,
                "No hay dispositivos emparejados. Empareja tu ESP32 en Configuración > Bluetooth",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Filtrar ESP32 con verificación de permiso segura
        val esp32Devices = devices.filter { device ->
            val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) device.name else null
            } else {
                device.name
            }
            name?.contains("ESP32", ignoreCase = true) == true
        }

        val deviceList = if (esp32Devices.isNotEmpty()) esp32Devices else devices

        // Construir nombres con verificación de permiso
        val deviceNames = deviceList.map { device ->
            val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) device.name ?: "Dispositivo" else "Dispositivo"
            } else {
                device.name ?: "Dispositivo"
            }
            "$name (${device.address})"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Seleccionar ESP32")
            .setItems(deviceNames) { _, which ->
                bluetoothService.connectToDevice(deviceList[which].address)
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

    override fun onDestroy() {
        super.onDestroy()
        bluetoothService.disconnect()
        adviceHandler.removeCallbacks(adviceRunnable)
    }
}