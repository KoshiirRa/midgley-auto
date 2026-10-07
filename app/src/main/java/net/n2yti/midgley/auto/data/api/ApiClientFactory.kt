package net.n2yti.midgley.auto.data.api

import kotlinx.serialization.json.Json
import net.n2yti.midgley.auto.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ApiClientFactory {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val clientCache = ConcurrentHashMap<Boolean, OkHttpClient>()
    private val serviceCache = ConcurrentHashMap<String, MidgleyApiService>()

    fun createOkHttpClient(enableLogging: Boolean = BuildConfig.DEBUG): OkHttpClient {
        return clientCache.computeIfAbsent(enableLogging) { logging ->
            val builder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "MidgleyAuto/1.2.0 (Android Auto In-Dash)")
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(request)
                }

            if (logging) {
                val loggingInterceptor = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                builder.addInterceptor(loggingInterceptor)
            }

            builder.build()
        }
    }

    fun createApiService(
        baseUrl: String = BuildConfig.BASE_URL,
        client: OkHttpClient = createOkHttpClient()
    ): MidgleyApiService {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return serviceCache.computeIfAbsent(cleanUrl) { url ->
            val contentType = "application/json".toMediaType()
            val retrofit = Retrofit.Builder()
                .baseUrl(url)
                .client(client)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()

            retrofit.create(MidgleyApiService::class.java)
        }
    }

    /**
     * Clears cached clients and services (primarily for testing with MockWebServer).
     */
    fun clearCache() {
        serviceCache.clear()
        clientCache.clear()
    }
}
