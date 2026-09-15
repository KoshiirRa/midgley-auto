package net.n2yti.midgley.auto.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.n2yti.midgley.auto.data.api.ApiClientFactory
import net.n2yti.midgley.auto.data.api.MidgleyApiService
import net.n2yti.midgley.auto.data.models.CombinedApiResponse
import net.n2yti.midgley.auto.data.models.ForecastResponse
import net.n2yti.midgley.auto.data.models.LocationResolveResponse
import net.n2yti.midgley.auto.data.models.RecommendationCode
import net.n2yti.midgley.auto.data.models.SavingsAdvisorResponse
import net.n2yti.midgley.auto.data.obd.Obd2PidDecoder
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Resilient Repository coordinating unified API requests across GitHub Pages static feeds
 * and dynamic API gateways, with 6-hour offline caching and low-fuel overrides.
 */
class MidgleyRepository(
    private val customApiService: MidgleyApiService? = null,
    private val preferenceManager: MetroPreferenceManager? = null
) {

    companion object {
        const val STALE_CACHE_THRESHOLD_HOURS = 6.0
        private const val MILLIS_PER_HOUR = 3600000.0

        fun isStaticHost(url: String): Boolean {
            return url.contains("github.io") || url.contains("github.com") || url.contains("raw.githubusercontent.com")
        }
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

    private fun getService(): MidgleyApiService {
        if (customApiService != null) return customApiService
        val baseUrl = preferenceManager?.getApiBaseUrl() ?: MetroPreferenceManager.DEFAULT_PROD_URL
        return ApiClientFactory.createApiService(baseUrl = baseUrl)
    }

    private fun getActiveBaseUrl(): String? {
        return preferenceManager?.getApiBaseUrl()
    }

    /**
     * Fetches unified price & forecast context from GitHub Pages static feeds or dynamic API gateways.
     */
    fun getUnifiedAdvisor(
        locale: String = "tulsa",
        zipCode: String? = null,
        tankCapacity: Double = 15.0,
        fuelLevelPct: Double? = null
    ): Flow<Resource<SavingsAdvisorResponse>> = flow {
        val fuelKey = fuelLevelPct?.toInt() ?: -1
        val cacheKey = "${locale}_${zipCode ?: "default"}_$fuelKey"
        val cached = advisorCache[cacheKey]

        if (cached != null) {
            emit(Resource.Loading(cached.data))
        } else {
            emit(Resource.Loading())
        }

        try {
            val service = getService()
            val baseUrl = getActiveBaseUrl()

            val combined = if (customApiService != null && baseUrl == null) {
                // Direct mock / unit-test mode without preference manager override
                service.getCombined(locale = locale, zipCode = zipCode)
            } else {
                val effectiveUrl = baseUrl ?: MetroPreferenceManager.DEFAULT_PROD_URL
                if (isStaticHost(effectiveUrl)) {
                    val cleanBase = if (effectiveUrl.endsWith("/")) effectiveUrl else "$effectiveUrl/"
                    val staticUrl = "${cleanBase}api/v1/combined_${locale}.json"
                    service.getCombinedByUrl(staticUrl)
                } else {
                    service.getCombined(locale = locale, zipCode = zipCode)
                }
            }

            val advisor = transformCombinedToAdvisor(combined, locale, tankCapacity, fuelLevelPct)
            advisorCache[cacheKey] = CachedEntry(advisor)
            emit(Resource.Success(advisor, isCached = false, cacheAgeHours = 0.0))
        } catch (e: Exception) {
            Log.e("MidgleyRepo", "Failed to fetch unified advisor for $locale: ${e.message}", e)
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
                // Synthesize graceful offline fallback recommendation based on known regional baseline
                val fallback = generateOfflineFallbackAdvisor(locale, tankCapacity, fuelLevelPct)
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
            val service = getService()
            val forecast = service.getForecast(locationId)
            forecastCache[locationId] = CachedEntry(forecast)
            emit(Resource.Success(forecast, isCached = false, cacheAgeHours = 0.0))
        } catch (e: Exception) {
            Log.e("MidgleyRepo", "Failed to fetch 5-day forecast for $locationId: ${e.message}", e)
            if (cached != null) {
                emit(Resource.Success(cached.data, isCached = true, cacheAgeHours = cached.ageHours))
            } else {
                emit(Resource.Error(e.localizedMessage ?: "Forecast service unavailable"))
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
        } else {
            emit(Resource.Loading())
        }

        try {
            val service = getService()
            val resolved = service.resolveLocation(lat, lon)
            locationCache[cacheKey] = CachedEntry(resolved)
            emit(Resource.Success(resolved))
        } catch (e: Exception) {
            Log.e("MidgleyRepo", "Failed to resolve location ($lat, $lon): ${e.message}", e)
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

    fun transformCombinedToAdvisor(
        combined: CombinedApiResponse,
        locale: String,
        tankCapacity: Double,
        fuelLevelPct: Double? = null
    ): SavingsAdvisorResponse {
        val currentPrice = combined.liveLookup?.currentPricePerGal ?: combined.forecast?.currentBasePrice ?: 3.89
        val targetPrice = combined.forecast?.predictedPricePerGal ?: combined.forecast?.day3Price ?: currentPrice

        val delta = if (combined.forecast?.expectedChangeDollars != null) {
            if (combined.forecast.projectedDirection?.equals("DOWN", ignoreCase = true) == true) {
                -Math.abs(combined.forecast.expectedChangeDollars)
            } else if (combined.forecast.projectedDirection?.equals("UP", ignoreCase = true) == true) {
                Math.abs(combined.forecast.expectedChangeDollars)
            } else {
                combined.forecast.expectedChangeDollars
            }
        } else {
            targetPrice - currentPrice
        }

        val savingsPerGal = if (delta < 0) -delta else 0.0

        // Calculate dynamic shortfall gallons needed to fill up
        val effectiveShortfallGallons = if (fuelLevelPct != null) {
            val clampedPct = fuelLevelPct.coerceIn(0.0, 100.0)
            val fuelGallons = (clampedPct / 100.0) * tankCapacity
            (tankCapacity - fuelGallons).coerceAtLeast(0.0)
        } else {
            tankCapacity
        }

        val netSavings = savingsPerGal * effectiveShortfallGallons

        // Safety override if tank is in low-fuel reserve (< 15%)
        val isLowFuel = fuelLevelPct != null && fuelLevelPct < Obd2PidDecoder.LOW_FUEL_THRESHOLD_PERCENT

        val (code, signal, timingText) = when {
            isLowFuel -> Triple(
                RecommendationCode.FILL_NOW,
                "🔴 LOW FUEL (${fuelLevelPct!!.toInt()}%) • FILL UP NOW",
                "Reserve Alert: Fill Up Immediately (< 15%)"
            )
            delta <= -0.04 -> Triple(
                RecommendationCode.WAIT_TO_FILL,
                "🟢 WAIT TO FILL UP",
                "Projected Trough (Day 3)"
            )
            delta >= 0.04 -> Triple(
                RecommendationCode.FILL_NOW,
                "🔴 FILL UP TODAY",
                "Spike Imminent: Fill Up Now"
            )
            else -> Triple(
                RecommendationCode.STABLE,
                "🟡 PRICES STABLE",
                "Stable Window: Refuel as needed"
            )
        }

        return SavingsAdvisorResponse(
            locationId = locale,
            recommendationCode = code,
            displaySignal = signal,
            optimalDay = if (isLowFuel) 0 else 3,
            optimalDate = timingText,
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
        tankCapacity: Double,
        fuelLevelPct: Double? = null
    ): SavingsAdvisorResponse {
        val basePrice = when (locale.lowercase()) {
            "oakland" -> 4.89
            "port_st_lucie" -> 3.49
            "newark" -> 3.39
            "cincinnati" -> 3.45
            "greenville", "charlotte" -> 3.35
            "tulsa" -> 3.89
            else -> 3.65
        }

        val isLowFuel = fuelLevelPct != null && fuelLevelPct < Obd2PidDecoder.LOW_FUEL_THRESHOLD_PERCENT
        val signal = if (isLowFuel) {
            "🔴 LOW FUEL (${fuelLevelPct!!.toInt()}%) • FILL UP (Offline)"
        } else {
            "🟡 PRICES STABLE (Offline)"
        }

        return SavingsAdvisorResponse(
            locationId = locale,
            recommendationCode = if (isLowFuel) RecommendationCode.FILL_NOW else RecommendationCode.STABLE,
            displaySignal = signal,
            optimalDay = 0,
            optimalDate = if (isLowFuel) "Reserve Alert: Fill Up Now" else "Current Baseline",
            currentPriceGal = basePrice,
            targetPriceGal = basePrice,
            savingsPerGal = 0.0,
            netTankSavingsUsd = 0.0,
            confidenceLevel = "OFFLINE_ESTIMATE",
            isCached = true,
            cacheAgeHours = 0.0
        )
    }
}
