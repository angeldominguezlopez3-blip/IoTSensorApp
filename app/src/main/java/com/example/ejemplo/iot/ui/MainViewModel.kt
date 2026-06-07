package com.ejemplo.iot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.iot.data.api.RetrofitInstance
import com.ejemplo.iot.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel : ViewModel() {

    private val _sensorReadings = MutableStateFlow<List<SensorReading>>(emptyList())
    val sensorReadings: StateFlow<List<SensorReading>> = _sensorReadings.asStateFlow()

    private val _weeklyStats = MutableStateFlow<WeeklyStats?>(null)
    val weeklyStats: StateFlow<WeeklyStats?> = _weeklyStats.asStateFlow()

    private val _iaAdvice = MutableStateFlow<List<IAAdvice>>(emptyList())
    val iaAdvice: StateFlow<List<IAAdvice>> = _iaAdvice.asStateFlow()

    private val _statistics = MutableStateFlow<Statistics?>(null)
    val statistics: StateFlow<Statistics?> = _statistics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _serverFound = MutableStateFlow<Boolean?>(null)
    val serverFound: StateFlow<Boolean?> = _serverFound.asStateFlow()

    // Niveles de estrés
    enum class NivelEstres { BAJO, MEDIO, CRITICO }

    data class WeeklyStats(
        val nivelMayoritario: NivelEstres,
        val countBajo: Int,
        val countMedio: Int,
        val countCritico: Int,
        val totalReadings: Int
    )

    fun calcularNivel(temp: Float, sonido: Int): NivelEstres = when {
        temp >= 30 || sonido >= 75 -> NivelEstres.CRITICO
        temp >= 26 || sonido >= 55 -> NivelEstres.MEDIO
        else -> NivelEstres.BAJO
    }

    fun sendBluetoothDataToBackend(temperatura: Float, sonido: Int, presencia: Boolean) {
        viewModelScope.launch {
            if (_serverFound.value != true) return@launch
            try {
                RetrofitInstance.api.createSensorReading(
                    mapOf(
                        "temperatura" to temperatura,
                        "sonido" to sonido,
                        "presencia" to presencia
                    )
                )
                // Recargar datos para que aparezca en historial
                loadData()
            } catch (e: Exception) {
                // Silencioso — no interrumpir la UI por un fallo de envío
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            if (_serverFound.value != true) {
                val found = RetrofitInstance.discoverServer()
                _serverFound.value = found
                if (!found) {
                    _error.value = "No se encontró el servidor. Verifica que Docker esté corriendo."
                    _isLoading.value = false
                    return@launch
                }
            }

            try {
                val api = RetrofitInstance.api
                val readings = api.getLatestReadings(100)
                val advice = api.getIAAdvice(10)
                val stats = api.getStatistics()

                // Filtrar últimos 7 días
                val hace7dias = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -7)
                }.time

                val readingsSemana = readings.filter {
                    it.timestamp.after(hace7dias)
                }

                _sensorReadings.value = readingsSemana
                _iaAdvice.value = advice
                _statistics.value = stats

                // Calcular estadísticas semanales
                calcularEstadisticasSemanales(readingsSemana)

            } catch (e: Exception) {
                _error.value = "Error al cargar datos: ${e.message}"
                _serverFound.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun calcularEstadisticasSemanales(readings: List<SensorReading>) {
        if (readings.isEmpty()) return

        var bajo = 0; var medio = 0; var critico = 0

        readings.forEach { r ->
            when (calcularNivel(r.temperatura, r.sonido)) {
                NivelEstres.BAJO -> bajo++
                NivelEstres.MEDIO -> medio++
                NivelEstres.CRITICO -> critico++
            }
        }

        val mayoritario = when {
            critico >= medio && critico >= bajo -> NivelEstres.CRITICO
            medio >= bajo -> NivelEstres.MEDIO
            else -> NivelEstres.BAJO
        }

        _weeklyStats.value = WeeklyStats(
            nivelMayoritario = mayoritario,
            countBajo = bajo,
            countMedio = medio,
            countCritico = critico,
            totalReadings = readings.size
        )
    }

    fun loadAdvice() {
        viewModelScope.launch {
            if (_serverFound.value != true) return@launch
            try {
                _iaAdvice.value = RetrofitInstance.api.getIAAdvice(10)
            } catch (e: Exception) { }
        }
    }

    fun triggerAnalysis() {
        viewModelScope.launch {
            try {
                val result = RetrofitInstance.api.analyzeLatestData()
                _error.value = if (result.esAnomalia) "⚠️ ${result.consejo}" else "✅ ${result.consejo}"
            } catch (e: Exception) {
                _error.value = "Error en análisis: ${e.message}"
            }
        }
    }

    fun triggerTraining() {
        viewModelScope.launch {
            try {
                RetrofitInstance.api.trainModel()
                _error.value = "✅ Entrenamiento iniciado"
            } catch (e: Exception) {
                _error.value = "Error al entrenar: ${e.message}"
            }
        }
    }
}