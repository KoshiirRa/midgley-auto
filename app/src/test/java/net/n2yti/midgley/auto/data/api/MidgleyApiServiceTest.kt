package net.n2yti.midgley.auto.data.api

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import net.n2yti.midgley.auto.data.models.RecommendationCode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class MidgleyApiServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: MidgleyApiService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/api/v1/").toString()
        apiService = ApiClientFactory.createApiService(
            baseUrl = baseUrl,
            client = ApiClientFactory.createOkHttpClient(enableLogging = false)
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testGetCombined_deserializesSuccessfully() = runBlocking {
        val mockJson = """
            {
                "status": "success",
                "timestamp": "2026-09-14T05:00:00Z",
                "locale": "tulsa",
                "live_lookup": {
                    "current_price_per_gal": 3.89,
                    "source": "GasBuddy",
                    "provenance": "LIVE_SURVEY",
                    "cache_hit": true,
                    "cache_age_seconds": 120.0
                },
                "forecast": {
                    "current_base_price": 3.89,
                    "predicted_price_per_gal": 3.79,
                    "direction": "DOWN",
                    "expected_change_cents": -10.0,
                    "day_3_price": 3.79
                },
                "key_drivers": [
                    "West Tulsa Refinery Online",
                    "Cushing Crude Storage +1.2M bbl"
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(mockJson)
                .addHeader("Content-Type", "application/json")
        )

        val response = apiService.getCombined(locale = "tulsa")
        assertThat(response.status).isEqualTo("success")
        assertThat(response.locale).isEqualTo("tulsa")
        assertThat(response.liveLookup?.currentPricePerGal).isEqualTo(3.89)
        assertThat(response.forecast?.day3Price).isEqualTo(3.79)
        assertThat(response.keyDrivers).hasSize(2)
    }

    @Test
    fun testResolveLocation_deserializesSuccessfully() = runBlocking {
        val mockJson = """
            {
                "location_id": "tulsa",
                "location_name": "Tulsa Metro Area",
                "state": "OK",
                "padd": "PADD 2 (Midwest)",
                "distance_km": 4.2
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(mockJson)
                .addHeader("Content-Type", "application/json")
        )

        val response = apiService.resolveLocation(36.1540, -95.9928)
        assertThat(response.locationId).isEqualTo("tulsa")
        assertThat(response.state).isEqualTo("OK")
        assertThat(response.padd).contains("PADD 2")
        assertThat(response.distanceKm).isEqualTo(4.2)
    }

    @Test
    fun testGetForecast_deserializesSuccessfully() = runBlocking {
        val mockJson = """
            {
                "location_id": "tulsa",
                "as_of_timestamp": "2026-09-14T00:00:00Z",
                "base_price": 3.89,
                "forecast": [
                    {"day": 0, "date": "2026-09-14", "p50": 3.89, "p10": 3.85, "p90": 3.93},
                    {"day": 1, "date": "2026-09-15", "p50": 3.85, "p10": 3.80, "p90": 3.90},
                    {"day": 2, "date": "2026-09-16", "p50": 3.80, "p10": 3.75, "p90": 3.85},
                    {"day": 3, "date": "2026-09-17", "p50": 3.79, "p10": 3.73, "p90": 3.84}
                ],
                "directional_trend": "TROUGH_DAY_3"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(mockJson)
                .addHeader("Content-Type", "application/json")
        )

        val response = apiService.getForecast("tulsa")
        assertThat(response.locationId).isEqualTo("tulsa")
        assertThat(response.forecast).hasSize(4)
        assertThat(response.forecast[3].p50).isEqualTo(3.79)
    }

    @Test
    fun testGetSavingsAdvisor_deserializesSuccessfully() = runBlocking {
        val mockJson = """
            {
                "location_id": "tulsa",
                "recommendation_code": "WAIT_TO_FILL",
                "display_signal": "🟢 WAIT TO FILL UP",
                "optimal_day": 3,
                "optimal_date": "2026-09-17",
                "current_price_gal": 3.89,
                "target_price_gal": 3.79,
                "savings_per_gal": 0.10,
                "net_tank_savings_usd": 1.50,
                "confidence_level": "HIGH"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(mockJson)
                .addHeader("Content-Type", "application/json")
        )

        val response = apiService.getSavingsAdvisor("tulsa", 15.0)
        assertThat(response.recommendationCode).isEqualTo(RecommendationCode.WAIT_TO_FILL)
        assertThat(response.savingsPerGal).isEqualTo(0.10)
        assertThat(response.netTankSavingsUsd).isEqualTo(1.50)
    }
}
