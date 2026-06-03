package com.ejemplo.iot.data.models

import com.google.gson.annotations.SerializedName
import java.util.Date

data class SensorReading(
    @SerializedName("id") val id: String,
    @SerializedName("temperatura") val temperatura: Float,
    @SerializedName("sonido") val sonido: Int,
    @SerializedName("presencia") val presencia: Boolean,
    @SerializedName("timestamp") val timestamp: Date
)

data class IAAdvice(
    @SerializedName("id") val id: String? = null,
    @SerializedName("tipo") val tipo: String,
    @SerializedName("mensaje") val mensaje: String,
    @SerializedName("severidad") val severidad: String,
    @SerializedName("timestamp") val timestamp: Date,
    @SerializedName("leido") val leido: Boolean = false
)

data class IAAnalysis(
    @SerializedName("es_anomalia") val esAnomalia: Boolean,
    @SerializedName("confianza") val confianza: Float,
    @SerializedName("consejo") val consejo: String,
    @SerializedName("severidad") val severidad: String,
    @SerializedName("accion_sugerida") val accionSugerida: String
)

data class Statistics(
    @SerializedName("avg_temperature") val avgTemperature: Float,
    @SerializedName("max_temperature") val maxTemperature: Float,
    @SerializedName("min_temperature") val minTemperature: Float,
    @SerializedName("total_readings") val totalReadings: Int,
    @SerializedName("last_hour_readings") val lastHourReadings: Int,
    @SerializedName("anomalies_count") val anomaliesCount: Int
)

data class Alert(
    @SerializedName("id") val id: String,
    @SerializedName("mensaje") val mensaje: String,
    @SerializedName("severidad") val severidad: String,
    @SerializedName("timestamp") val timestamp: String
)