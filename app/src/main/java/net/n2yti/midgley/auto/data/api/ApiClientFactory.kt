package net.n2yti.midgley.auto.data.api

import kotlinx.serialization.json.Json
import net.n2yti.midgley.auto.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClientFactory {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun createOkHttpClient(enableLogging: Boolean = BuildConfig.DEBUG): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "MidgleyAuto/1.0 (Android Auto In-Dash)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }

        if (enableLogging) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    fun createApiService(
        baseUrl: String = BuildConfig.BASE_URL,
        client: OkHttpClient = createOkHttpClient()
    ): MidgleyApiService {
        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        return retrofit.create(MidgleyApiService::class.java)
    }
}
