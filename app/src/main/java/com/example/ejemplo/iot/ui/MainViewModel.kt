package com.ejemplo.iot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.iot.data.api.RetrofitInstance
import com.ejemplo.iot.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    private val api = RetrofitInstance.api

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

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            // Descubrir servidor en red local
            val found = withContext(Dispatchers.IO) {
                RetrofitInstance.discoverServer()
            }
            if (!found) {
                _error.value = "No se encontro el servidor en la red local"
                _isLoading.value = false
                return@launch
            }

            viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val readings = api.getLatestReadings(20)
                val advice = api.getIAAdvice(10)
                val stats = api.getStatistics()

                _sensorReadings.value = readings
                _iaAdvice.value = advice
                _statistics.value = stats
            } catch (e: Exception) {
                _error.value = "Error al cargar datos: ${e.message}"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun triggerAnalysis(readingId: String? = null) {
        viewModelScope.launch {
            try {
                val result = api.analyzeLatestData()
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
}