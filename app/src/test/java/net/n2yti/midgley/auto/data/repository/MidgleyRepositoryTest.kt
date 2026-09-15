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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
                "locale": {
                    "code": "tulsa",
                    "region_id": "Tulsa_OK",
                    "name": "Tulsa Metro Area, OK"
                },
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

    @Test
    fun testGetUnifiedAdvisor_withNestedProvenanceObject_parsesSuccessfully() = runBlocking {
        val gitHubPagesJson = """
            {
              "status": "success",
              "timestamp": "2026-09-14T15:30:59.844548",
              "locale": {
                "code": "tulsa",
                "region_id": "Tulsa_OK",
                "name": "Tulsa Metro Area, OK",
                "padd_region": "PADD 2 Midwest"
              },
              "live_lookup": {
                "current_price_per_gal": 3.888,
                "source": "AAA Web Scraper (Tulsa, OK)",
                "provenance": {
                  "source": "AAA Web Scraper (Tulsa, OK)",
                  "region_id": "Tulsa_OK",
                  "padd": "PADD 2",
                  "requested_granularity": "METRO",
                  "served_granularity": "METRO",
                  "is_fallback_granularity": false,
                  "cache_status": "HIT_STALE",
                  "timestamp": "2026-09-14T15:30:59Z"
                },
                "cache_hit": false,
                "cache_age_seconds": 0.0,
                "carb_tax_regulatory_burden_per_gal": 0.0
              },
              "forecast": {
                "model_version": "v1.6 Ipatieff",
                "forecast_horizon_days": 5,
                "target_date": "2026-09-19",
                "current_base_price": 3.888,
                "predicted_price_per_gal": 3.933,
                "expected_change_dollars": 0.045,
                "expected_change_percent": 1.16,
                "projected_direction": "UP",
                "directional_hit_rate_historical": 0.6079,
                "historical_mae_dollars": 0.1069
              },
              "key_drivers": [
                {
                  "category": "Regional Logistics & Hub Delivery",
                  "description": "Delivery hub rack margin expansion",
                  "impact_dollars": 0.013,
                  "impact_pct": 0.33,
                  "share_pct": 30.0,
                  "direction": "UP"
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gitHubPagesJson)
                .addHeader("Content-Type", "application/json; charset=utf-8")
        )

        val emissions = repository.getUnifiedAdvisor(locale = "tulsa", tankCapacity = 15.0).toList()
        assertThat(emissions).hasSize(2)
        assertThat(emissions[1]).isInstanceOf(Resource.Success::class.java)

        val success = emissions[1] as Resource.Success
        assertThat(success.data).isNotNull()
        assertThat(success.data?.currentPriceGal).isEqualTo(3.888)
        assertThat(success.data?.targetPriceGal).isEqualTo(3.933)
        assertThat(success.data?.recommendationCode).isEqualTo(RecommendationCode.FILL_NOW)
    }
}
