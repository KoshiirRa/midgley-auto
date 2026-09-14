package net.n2yti.midgley.auto.data.api

import net.n2yti.midgley.auto.data.models.ActiveAlertsResponse
import net.n2yti.midgley.auto.data.models.CombinedApiResponse
import net.n2yti.midgley.auto.data.models.ForecastResponse
import net.n2yti.midgley.auto.data.models.LocationResolveResponse
import net.n2yti.midgley.auto.data.models.SavingsAdvisorResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface MidgleyApiService {

    /**
     * Unified single round-trip endpoint for live price, 5-day forecast, and key market drivers.
     */
    @GET("combined")
    suspend fun getCombined(
        @Query("locale") locale: String = "national",
        @Query("zip_code") zipCode: String? = null
    ): CombinedApiResponse

    /**
     * Direct URL fetch for static JSON files on GitHub Pages (e.g. https://koshiirra.github.io/midgley/api/v1/combined_tulsa.json).
     */
    @GET
    suspend fun getCombinedByUrl(
        @Url url: String
    ): CombinedApiResponse

    /**
     * Resolves GPS latitude & longitude coordinates to nearest Metropolitan Statistical Area (MSA).
     */
    @GET("locations/resolve")
    suspend fun resolveLocation(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): LocationResolveResponse

    /**
     * Retrieves 5-day out-of-time price forecasts for specific location.
     */
    @GET("forecasts/{location_id}")
    suspend fun getForecast(
        @Path("location_id") locationId: String
    ): ForecastResponse

    /**
     * Retrieves smart fill-up advisor timing recommendation and dollar savings.
     */
    @GET("savings")
    suspend fun getSavingsAdvisor(
        @Query("location_id") locationId: String,
        @Query("tank_capacity") tankCapacity: Double = 15.0
    ): SavingsAdvisorResponse

    /**
     * Retrieves active severe weather and refinery supply disruption alerts.
     */
    @GET("events/active")
    suspend fun getActiveAlerts(
        @Query("location_id") locationId: String
    ): ActiveAlertsResponse
}
