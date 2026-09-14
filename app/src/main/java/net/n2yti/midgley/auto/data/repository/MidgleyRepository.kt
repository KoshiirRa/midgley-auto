package net.n2yti.midgley.auto.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.n2yti.midgley.auto.data.api.ApiClientFactory
import net.n2yti.midgley.auto.data.api.MidgleyApiService
import net.n2yti.midgley.auto.data.models.CombinedApiResponse
import net.n2yti.midgley.auto.data.models.ForecastDayPoint
import net.n2yti.midgley.auto.data.models.ForecastResponse
import net.n2yti.midgley.auto.data.models.LocationResolveResponse
import net.n2yti.midgley.auto.data.models.RecommendationCode
import net.n2yti.midgley.auto.data.models.SavingsAdvisorResponse
import java.util.concurrent.ConcurrentHashMap

/**
 * Resilient Repository coordinating unified API requests, 6-hour offline caching,
 * and graceful fallback synthesis for vehicle head unit displays.
 */
class MidgleyRepository(
    private val apiService: MidgleyApiService = ApiClientFactory.createApiService()
) {

    companion object {
        const val STALE_CACHE_THRESHOLD_HOURS = 6.0
        private const val MILLIS_PER_HOUR = 3600000.0
    }

    private data class CachedEntry<T>(
        val data: T,
        val timestampMillis: Long = System.currentTimeMillis()
    ) {
        val ageHours: Double
            get() = (System.currentTimeMillis() - timestampMillis) / MILLIS_PER_HOUR
    }

    private val advisorCache = ConcurrentHashMap<String, CachedEntry<SavingsAdvisorResponse>>()
    private val forecastCache = ConcurrentHashMap<String, CachedEntry<ForecastResponse>>()
    private val locationCache = ConcurrentHashMap<String, CachedEntry<LocationResolveResponse>>()

    /**
     * Fetches unified price & forecast context with offline 6-hour caching and fallback generation.
     */
    fun getUnifiedAdvisor(
        locale: String = "tulsa",
        zipCode: String? = null,
        tankCapacity: Double = 15.0
    ): Flow<Resource<SavingsAdvisorResponse>> = flow {
        val cacheKey = "${locale}_${zipCode ?: "default"}"
        val cached = advisorCache[cacheKey]

        if (cached != null) {
            emit(Resource.Loading(cached.data))
        } else {
            emit(Resource.Loading())
        }

        try {
            val combined = apiService.getCombined(locale = locale, zipCode = zipCode)
            val advisor = transformCombinedToAdvisor(combined, locale, tankCapacity)
            advisorCache[cacheKey] = CachedEntry(advisor)
            emit(Resource.Success(advisor, isCached = false, cacheAgeHours = 0.0))
        } catch (e: Exception) {
            if (cached != null) {
                val advisorWithFlag = cached.data.copy(
                    isCached = true,
                    cacheAgeHours = cached.ageHours
                )
                emit(
                    Resource.Success(
                        data = advisorWithFlag,
                        isCached = true,
                        cacheAgeHours = cached.ageHours
                    )
                )
            } else {
                // Synthesize graceful offline fallback recommendation so in-dash head unit never crashes
                val fallback = generateOfflineFallbackAdvisor(locale, tankCapacity)
                emit(
                    Resource.Error(
                        message = e.localizedMessage ?: "Network connection unavailable",
                        data = fallback,
                        isCached = true,
                        cacheAgeHours = 0.0
                    )
                )
            }
        }
    }

    /**
     * Fetches 5-day out-of-time retail forecast with offline caching.
     */
    fun get5DayForecast(locationId: String = "tulsa"): Flow<Resource<ForecastResponse>> = flow {
        val cached = forecastCache[locationId]
        if (cached != null) {
            emit(Resource.Loading(cached.data))
        } else {
            emit(Resource.Loading())
        }

        try {
            val forecast = apiService.getForecast(locationId)
            forecastCache[locationId] = CachedEntry(forecast)
            emit(Resource.Success(forecast, isCached = false, cacheAgeHours = 0.0))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Resource.Success(cached.data, isCached = true, cacheAgeHours = cached.ageHours))
            } else {
                emit(Resource.Error(e.localizedMessage ?: "Forecast lookup failed"))
            }
        }
    }

    /**
     * Resolves current GPS coordinates to nearest refining hub.
     */
    fun resolveLocation(lat: Double, lon: Double): Flow<Resource<LocationResolveResponse>> = flow {
        val cacheKey = "%.2f_%.2f".format(lat, lon)
        val cached = locationCache[cacheKey]
        if (cached != null) {
            emit(Resource.Loading(cached.data))
        }

        try {
            val resolved = apiService.resolveLocation(lat, lon)
            locationCache[cacheKey] = CachedEntry(resolved)
            emit(Resource.Success(resolved))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Resource.Success(cached.data, isCached = true, cacheAgeHours = cached.ageHours))
            } else {
                // Fallback to nearest deterministic metro
                val fallback = LocationResolveResponse(
                    locationId = "tulsa",
                    locationName = "Tulsa Metro Area (Default)",
                    state = "OK",
                    padd = "PADD 2 (Midwest)",
                    distanceKm = 0.0
                )
                emit(Resource.Success(fallback, isCached = true))
            }
        }
    }

    private fun transformCombinedToAdvisor(
        combined: CombinedApiResponse,
        locale: String,
        tankCapacity: Double
    ): SavingsAdvisorResponse {
        val currentPrice = combined.liveLookup?.currentPricePerGal ?: combined.forecast?.currentBasePrice ?: 3.89
        val targetPrice = combined.forecast?.day3Price ?: combined.forecast?.predictedPricePerGal ?: currentPrice
        val delta = targetPrice - currentPrice
        val savingsPerGal = if (delta < 0) -delta else 0.0
        val netSavings = savingsPerGal * tankCapacity

        val (code, signal) = when {
            delta <= -0.04 -> Pair(RecommendationCode.WAIT_TO_FILL, "🟢 WAIT TO FILL UP")
            delta >= 0.04 -> Pair(RecommendationCode.FILL_NOW, "🔴 FILL UP NOW")
            else -> Pair(RecommendationCode.STABLE, "🟡 PRICES STABLE")
        }

        return SavingsAdvisorResponse(
            locationId = locale,
            recommendationCode = code,
            displaySignal = signal,
            optimalDay = 3,
            optimalDate = "Projected Trough (Day 3)",
            currentPriceGal = currentPrice,
            targetPriceGal = targetPrice,
            savingsPerGal = savingsPerGal,
            netTankSavingsUsd = netSavings,
            confidenceLevel = "HIGH",
            isCached = false,
            cacheAgeHours = 0.0
        )
    }

    private fun generateOfflineFallbackAdvisor(
        locale: String,
        tankCapacity: Double
    ): SavingsAdvisorResponse {
        return SavingsAdvisorResponse(
            locationId = locale,
            recommendationCode = RecommendationCode.STABLE,
            displaySignal = "🟡 PRICES STABLE (Offline)",
            optimalDay = 0,
            optimalDate = "Current Baseline",
            currentPriceGal = 3.89,
            targetPriceGal = 3.89,
            savingsPerGal = 0.0,
            netTankSavingsUsd = 0.0,
            confidenceLevel = "OFFLINE_ESTIMATE",
            isCached = true,
            cacheAgeHours = 0.0
        )
    }
}
