package com.ejemplo.iot.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private const val TAG = "RetrofitInstance"
    private const val PORT = 8000

    // ★ Cambia esta URL cada vez que reinicies ngrok
    private const val NGROK_URL = "https://flaky-endowment-renounce.ngrok-free.dev"

    @Volatile private var _api: ApiService? = null

    val api: ApiService
        get() = _api ?: buildApi("http://localhost:$PORT/")

    suspend fun discoverServer(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Iniciando discovery...")

        // 1️⃣ Intentar ngrok primero (funciona en cualquier red)
        if (NGROK_URL.isNotBlank() && !NGROK_URL.contains("abc123")) {
            Log.i(TAG, "Probando ngrok: $NGROK_URL")
            if (tryConnect(NGROK_URL)) {
                Log.i(TAG, "✅ Servidor en ngrok: $NGROK_URL")
                buildApi("$NGROK_URL/")
                return@withContext true
            }
            Log.w(TAG, "ngrok no respondió, intentando red local...")
        }

        // 2️⃣ IP fija conocida (red de casa)
        val knownIPs = listOf("192.168.1.92", "10.0.2.2")
        for (ip in knownIPs) {
            if (tryConnect("http://$ip:$PORT")) {
                Log.i(TAG, "✅ Servidor en IP conocida: $ip")
                buildApi("http://$ip:$PORT/")
                return@withContext true
            }
        }

        // 3️⃣ Escaneo automático de la red local
        val localIp = getLocalIpAddress()
        Log.i(TAG, "IP local del celular: $localIp")

        if (localIp == null) {
            Log.w(TAG, "❌ No se pudo obtener IP local")
            return@withContext false
        }

        val prefix = localIp.substringBeforeLast(".")
        val priority = listOf(
            "$prefix.1", "$prefix.2", "$prefix.92", "$prefix.100",
            "$prefix.101", "$prefix.102", "$prefix.105", "$prefix.110",
            "$prefix.150", "$prefix.200", "$prefix.254"
        ).filter { it != localIp }

        for (ip in priority) {
            if (tryConnect("http://$ip:$PORT")) {
                Log.i(TAG, "✅ Servidor en: $ip")
                buildApi("http://$ip:$PORT/")
                return@withContext true
            }
        }

        val allIPs = (1..254).map { "$prefix.$it" }
            .filter { it != localIp && it !in priority }

        allIPs.chunked(30).forEach { chunk ->
            val results = chunk.map { ip ->
                async { if (tryConnect("http://$ip:$PORT")) ip else null }
            }.awaitAll()
            val found = results.filterNotNull().firstOrNull()
            if (found != null) {
                Log.i(TAG, "✅ Servidor en: $found")
                buildApi("http://$found:$PORT/")
                return@withContext true
            }
        }

        Log.w(TAG, "❌ Servidor no encontrado en ninguna red")
        false
    }

    private fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { addr ->
                    !addr.isLoopbackAddress &&
                            addr is Inet4Address &&
                            !addr.hostAddress.startsWith("169.254")
                }
                ?.hostAddress
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo IP: ${e.message}")
            null
        }
    }

    private fun tryConnect(baseUrl: String): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()
            val url = if (baseUrl.startsWith("https")) "$baseUrl/health"
            else "$baseUrl/health"
            val response = client
                .newCall(Request.Builder().url(url).build())
                .execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    private fun buildApi(baseUrl: String): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
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