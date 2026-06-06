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

    private val FALLBACK_IPS = listOf(
        "192.168.1.92",   // ← Tu IP real de la PC con Docker
        "192.168.1.1",
        "192.168.1.100",
        "10.0.2.2"
    )

    @Volatile private var _api: ApiService? = null

    val api: ApiService
        get() = _api ?: buildApi("http://localhost:$PORT/")

    suspend fun discoverServer(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Iniciando discovery...")

        // Intentar primero la IP conocida directamente
        val knownIPs = listOf("192.168.1.92", "10.0.2.2")
        for (ip in knownIPs) {
            if (tryConnect("http://$ip:$PORT")) {
                Log.i(TAG, "✅ Servidor en IP conocida: $ip")
                buildApi("http://$ip:$PORT/")
                return@withContext true
            }
        }

        // Luego escanear la red automáticamente
        val localIp = getLocalIpAddress()
        Log.i(TAG, "IP local del celular: $localIp")

        if (localIp == null) return@withContext false

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

        val allIPs = (1..254).map { "$prefix.$it" }.filter { it != localIp && it !in priority }
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

        Log.w(TAG, "❌ No encontrado")
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
                .connectTimeout(800, TimeUnit.MILLISECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build()
            val response = client
                .newCall(Request.Builder().url("$baseUrl/health").build())
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