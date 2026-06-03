package com.ejemplo.iot.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object RetrofitInstance {
    fun discoverServer() {
        TODO("Not yet implemented")
    }

    // Para emulador Android: 10.0.2.2
    // Para dispositivo físico: tu IP local (ej: 192.168.1.100)
    private const val BASE_URL = "http://10.0.2.2:8000/"
    // private const val BASE_URL = "http://192.168.1.100:8000/"

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
    object RetrofitInstance {
        // Nombre mDNS del servidor (anunciado por zeroconf en el backend)
        private const val MDNS_HOST = "iot-platform.local"
        private const val PORT = 8000

        // IPs de fallback si mDNS no funciona
        // Agrega aqui las IPs mas comunes de tu red
        private val FALLBACK_IPS = listOf(
            "192.168.1.100",  // Cambia segun tu router
            "192.168.0.100",
            "10.0.0.100",
            "10.0.2.2"        // Emulador Android
        )

        @Volatile private var _api: ApiService? = null
        @Volatile private var resolvedBaseUrl: String? = null

        val api: ApiService
            get() = _api ?: createApi("http://$MDNS_HOST:$PORT/")

        private fun createApi(baseUrl: String): ApiService {
            val client = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
                .also { _api = it }
        }

        // Llamar esto desde ViewModel antes de la primera peticion
        suspend fun discoverServer(): Boolean {
            // Intentar mDNS primero
            return try {
                val url = "http://$MDNS_HOST:$PORT/health"
                val result = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.SECONDS).build()
                    .newCall(okhttp3.Request.Builder().url(url).build())
                    .execute()
                if (result.isSuccessful) {
                    createApi("http://$MDNS_HOST:$PORT/")
                    true
                } else tryFallbacks()
            } catch (e: Exception) { tryFallbacks() }
        }

        private fun tryFallbacks(): Boolean {
            for (ip in FALLBACK_IPS) {
                try {
                    val result = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(2, TimeUnit.SECONDS).build()
                        .newCall(okhttp3.Request.Builder()
                            .url("http://$ip:$PORT/health").build())
                        .execute()
                    if (result.isSuccessful) {
                        createApi("http://$ip:$PORT/")
                        return true
                    }
                } catch (e: Exception) { /* continuar */ }
            }
            return false
        }
    }

}