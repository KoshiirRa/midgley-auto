package net.n2yti.midgley.auto.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.n2yti.midgley.auto.data.api.ApiClientFactory
import net.n2yti.midgley.auto.data.models.RecommendationCode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class MidgleyRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: MidgleyRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/api/v1/").toString()
        val apiService = ApiClientFactory.createApiService(
            baseUrl = baseUrl,
            client = ApiClientFactory.createOkHttpClient(enableLogging = false)
        )
        repository = MidgleyRepository(apiService)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testGetUnifiedAdvisor_emitsLoadingThenSuccess() = runBlocking {
        val mockJson = """
            {
                "status": "success",
                "timestamp": "2026-09-14T05:00:00Z",
                "locale": "tulsa",
                "live_lookup": {
                    "current_price_per_gal": 3.89
                },
                "forecast": {
                    "current_base_price": 3.89,
                    "day_3_price": 3.79
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(mockJson)
                .addHeader("Content-Type", "application/json")
        )

        val emissions = repository.getUnifiedAdvisor(locale = "tulsa", tankCapacity = 15.0).toList()
        assertThat(emissions).hasSize(2)
        assertThat(emissions[0]).isInstanceOf(Resource.Loading::class.java)
        assertThat(emissions[1]).isInstanceOf(Resource.Success::class.java)

        val success = emissions[1] as Resource.Success
        assertThat(success.data).isNotNull()
        assertThat(success.data?.recommendationCode).isEqualTo(RecommendationCode.WAIT_TO_FILL)
        assertThat(success.data?.displaySignal).contains("WAIT TO FILL")
        assertThat(success.data?.savingsPerGal).isWithin(0.001).of(0.10)
        assertThat(success.data?.netTankSavingsUsd).isWithin(0.01).of(1.50)
    }

    @Test
    fun testGetUnifiedAdvisor_offlineFallbackOnNetworkFailure() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val emissions = repository.getUnifiedAdvisor(locale = "tulsa", tankCapacity = 15.0).toList()
        assertThat(emissions).isNotEmpty()

        val lastEmission = emissions.last()
        assertThat(lastEmission).isInstanceOf(Resource.Error::class.java)
        assertThat(lastEmission.data).isNotNull()
        assertThat(lastEmission.data?.isCached).isTrue()
        assertThat(lastEmission.data?.confidenceLevel).isEqualTo("OFFLINE_ESTIMATE")
    }
}
