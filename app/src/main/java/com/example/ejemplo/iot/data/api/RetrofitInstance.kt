package com.ejemplo.iot.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private const val TAG = "RetrofitInstance"
    private const val PORT = 8000

    // Nombre mDNS anunciado por el backend con zeroconf
    private const val MDNS_HOST = "iot-platform.local"

    private val FALLBACK_IPS = listOf(
        "192.168.1.75",   // ← cambia a la IP de tu PC/servidor
        "192.168.0.100",
        "192.168.1.1",
        "10.0.0.100",
        "10.0.2.2"         // emulador Android
    )

    @Volatile
    private var _api: ApiService? = null

    // Acceso directo al api ya construido (o construye con mDNS por defecto)
    val api: ApiService
        get() = _api ?: buildApi("http://$MDNS_HOST:$PORT/")

    /**
     * Intenta descubrir el servidor en la red local.
     * Primero prueba mDNS (iot-platform.local), luego las IPs de fallback.
     * Devuelve true si encontró el servidor.
     * Debe llamarse desde una coroutine (usa Dispatchers.IO internamente).
     */
    suspend fun discoverServer(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Buscando servidor IoT en la red local...")

        // 1. Intentar mDNS
        if (tryConnect("http://$MDNS_HOST:$PORT")) {
            Log.i(TAG, "✅ Servidor encontrado via mDNS: $MDNS_HOST")
            buildApi("http://$MDNS_HOST:$PORT/")
            return@withContext true
        }

        // 2. Intentar IPs de fallback
        for (ip in FALLBACK_IPS) {
            if (tryConnect("http://$ip:$PORT")) {
                Log.i(TAG, "✅ Servidor encontrado en IP: $ip")
                buildApi("http://$ip:$PORT/")
                return@withContext true
            }
        }

        Log.w(TAG, "❌ No se encontró el servidor en ninguna IP conocida")
        false
    }

    private fun tryConnect(baseUrl: String): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()
            val response = client
                .newCall(Request.Builder().url("$baseUrl/health").build())
                .execute()
            response.use { it.isSuccessful }  // .use() cierra el body automáticamente
        } catch (e: Exception) {
            false
        }
    }

    private fun buildApi(baseUrl: String): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
            .also { _api = it }
    }
}