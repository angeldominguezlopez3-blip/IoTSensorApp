package com.ejemplo.iot.data.api

import retrofit2.http.*
import com.ejemplo.iot.data.models.*

interface ApiService {

    @GET("api/sensors/latest")
    suspend fun getLatestReadings(
        @Query("limit") limit: Int = 20
    ): List<SensorReading>

    @GET("api/ia/advice")
    suspend fun getIAAdvice(
        @Query("limit") limit: Int = 10
    ): List<IAAdvice>

    @POST("api/ia/analyze/latest")
    suspend fun analyzeLatestData(): IAAnalysis

    @GET("api/android/stats")
    suspend fun getStatistics(): Statistics

    @GET("api/android/alerts/unread")
    suspend fun getUnreadAlerts(): List<Alert>

    @PUT("api/ia/advice/{id}/read")
    suspend fun markAdviceAsRead(@Path("id") id: String): Map<String, String>
}