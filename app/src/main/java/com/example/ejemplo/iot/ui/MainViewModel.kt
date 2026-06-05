package com.ejemplo.iot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.iot.data.api.RetrofitInstance
import com.ejemplo.iot.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val _sensorReadings = MutableStateFlow<List<SensorReading>>(emptyList())
    val sensorReadings: StateFlow<List<SensorReading>> = _sensorReadings.asStateFlow()

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

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Descubrir el servidor si aún no se ha encontrado
            if (_serverFound.value != true) {
                val found = RetrofitInstance.discoverServer()
                _serverFound.value = found
                if (!found) {
                    _error.value = "No se encontró el servidor en la red local. Verifica que el backend esté corriendo."
                    _isLoading.value = false
                    return@launch
                }
            }

            try {
                val api = RetrofitInstance.api
                val readings = api.getLatestReadings(100)
                val advice = api.getIAAdvice(10)
                val stats = api.getStatistics()

                _sensorReadings.value = readings
                _iaAdvice.value = advice
                _statistics.value = stats
            } catch (e: Exception) {
                _error.value = "Error al cargar datos: ${e.message}"
                // Resetear para reintentar discovery en el siguiente loadData()
                _serverFound.value = null
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadAdvice() {
        viewModelScope.launch {
            if (_serverFound.value != true) return@launch
            try {
                val advice = RetrofitInstance.api.getIAAdvice(10)
                _iaAdvice.value = advice
            } catch (e: Exception) {
                // Silencioso: no interrumpir la UI por un fallo de polling
            }
        }
    }

    fun triggerAnalysis(readingId: String? = null) {
        viewModelScope.launch {
            try {
                val result = RetrofitInstance.api.analyzeLatestData()
                if (result.esAnomalia) {
                    _error.value = "⚠️ ${result.consejo}"
                } else {
                    _error.value = "✅ Análisis completado: ${result.consejo}"
                }
            } catch (e: Exception) {
                _error.value = "Error en análisis: ${e.message}"
            }
        }
    }

    fun loadWeeklyHistory() {
        viewModelScope.launch {
            if (_serverFound.value != true) return@launch
            try {
                // Trae las últimas 100 lecturas para cubrir una semana
                val readings = RetrofitInstance.api.getLatestReadings(100)
                _sensorReadings.value = readings
            } catch (e: Exception) {
                // silencioso
            }
        }
    }

    fun triggerTraining() {
        viewModelScope.launch {
            try {
                // POST /api/ia/train — inicia entrenamiento en background en el servidor
                RetrofitInstance.api.trainModel()
                _error.value = "✅ Entrenamiento iniciado en el servidor"
            } catch (e: Exception) {
                _error.value = "Error al iniciar entrenamiento: ${e.message}"
            }
        }
    }
}