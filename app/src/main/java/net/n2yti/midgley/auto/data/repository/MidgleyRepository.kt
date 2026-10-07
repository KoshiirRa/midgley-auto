package net.n2yti.midgley.auto.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.n2yti.midgley.auto.data.api.ApiClientFactory
import net.n2yti.midgley.auto.data.api.MidgleyApiService
import net.n2yti.midgley.auto.data.models.CombinedApiResponse
import net.n2yti.midgley.auto.data.models.ForecastDayPoint
import net.n2yti.midgley.auto.data.models.ForecastResponse
import net.n2yti.midgley.auto.data.models.LocationResolveResponse
import net.n2yti.midgley.auto.data.models.RecommendationCode
import net.n2yti.midgley.auto.data.models.SavingsAdvisorResponse
import net.n2yti.midgley.auto.data.obd.Obd2PidDecoder
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository orchestrating live fuel prices, ECM price trajectory models,
 * OBD-II telemetry shortfall metrics, and in-dash fuel advice.
 */
class MidgleyRepository(
    private val customApiService: MidgleyApiService? = null,
    private val preferenceManager: MetroPreferenceManager? = null
) {

    companion object {
        const val CACHE_EXPIRATION_HOURS = 6.0
        const val MILLIS_PER_HOUR = 3600000.0

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
                    val cleanLocale = if (locale.equals("bay_area", ignoreCase = true)) "oakland" else locale
                    val staticUrl = "${cleanBase}api/v1/combined_${cleanLocale}.json"
                    service.getCombinedByUrl(staticUrl)
                } else {
                    service.getCombined(locale = locale, zipCode = zipCode)
                }
            }

            val advisor = transformCombinedToAdvisor(
                combined = combined,
                locale = locale,
                tankCapacity = tankCapacity,
                fuelLevelPct = fuelLevelPct
            )

            advisorCache[cacheKey] = CachedEntry(advisor)
            emit(Resource.Success(advisor, isCached = false, cacheAgeHours = 0.0))
        } catch (e: Exception) {
            Log.e("MidgleyRepo", "Failed to fetch unified advisor for $locale: ${e.message}", e)
            if (cached != null) {
                emit(
                    Resource.Success(
                        data = cached.data.copy(isCached = true, cacheAgeHours = cached.ageHours),
                        isCached = true,
                        cacheAgeHours = cached.ageHours
                    )
                )
            } else {
                // Return honest offline fallback without fabricating false ground truth prices (Issue #15)
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
    }.flowOn(Dispatchers.IO)

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
            val baseUrl = getActiveBaseUrl()

            val forecast = if (customApiService != null && baseUrl == null) {
                // Direct mock / unit-test mode without preference manager override
                service.getForecast(locationId)
            } else {
                val effectiveUrl = baseUrl ?: MetroPreferenceManager.DEFAULT_PROD_URL
                if (isStaticHost(effectiveUrl)) {
                    val cleanBase = if (effectiveUrl.endsWith("/")) effectiveUrl else "$effectiveUrl/"
                    val cleanLocale = if (locationId.equals("bay_area", ignoreCase = true)) "oakland" else locationId
                    val staticUrl = "${cleanBase}api/v1/combined_${cleanLocale}.json"
                    val combined = service.getCombinedByUrl(staticUrl)
                    transformCombinedToForecast(combined, locationId)
                } else {
                    service.getForecast(locationId)
                }
            }

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
    }.flowOn(Dispatchers.IO)

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
            val baseUrl = getActiveBaseUrl()

            val resolved = if (customApiService != null && baseUrl == null) {
                service.resolveLocation(lat, lon)
            } else {
                val effectiveUrl = baseUrl ?: MetroPreferenceManager.DEFAULT_PROD_URL
                if (isStaticHost(effectiveUrl)) {
                    val local = net.n2yti.midgley.auto.data.location.MetroLocationResolver.resolve(lat, lon)
                    LocationResolveResponse(
                        locationId = local.id,
                        locationName = local.name,
                        state = local.padd,
                        padd = local.padd,
                        distanceKm = local.distanceKm
                    )
                } else {
                    service.resolveLocation(lat, lon)
                }
            }

            locationCache[cacheKey] = CachedEntry(resolved)
            emit(Resource.Success(resolved, isCached = false, cacheAgeHours = 0.0))
        } catch (e: Exception) {
            Log.e("MidgleyRepo", "Failed to resolve GPS coordinates: ${e.message}", e)
            if (cached != null) {
                emit(Resource.Success(cached.data, isCached = true, cacheAgeHours = cached.ageHours))
            } else {
                // Local geodesic fallback resolver
                val local = net.n2yti.midgley.auto.data.location.MetroLocationResolver.resolve(lat, lon)
                val fallback = LocationResolveResponse(
                    locationId = local.id,
                    locationName = local.name,
                    state = local.padd,
                    padd = local.padd,
                    distanceKm = local.distanceKm
                )
                emit(Resource.Success(fallback, isCached = true, cacheAgeHours = 0.0))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun transformCombinedToAdvisor(
        combined: CombinedApiResponse,
        locale: String,
        tankCapacity: Double,
        fuelLevelPct: Double? = null
    ): SavingsAdvisorResponse {
        val currentPrice = combined.liveLookup?.currentPricePerGal ?: combined.forecast?.currentBasePrice ?: 0.0
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

        val shortfallGallons = if (fuelLevelPct != null) {
            val clamped = fuelLevelPct.coerceIn(0.0, 100.0)
            ((100.0 - clamped) / 100.0) * tankCapacity
        } else {
            tankCapacity
        }

        val isLowFuel = fuelLevelPct != null && fuelLevelPct < Obd2PidDecoder.LOW_FUEL_THRESHOLD_PERCENT

        val code: RecommendationCode
        val signal: String
        val optimalDay: Int
        val optimalDate: String

        if (isLowFuel) {
            code = RecommendationCode.FILL_NOW
            signal = "🔴 LOW FUEL (${fuelLevelPct!!.toInt()}%) • FILL UP NOW"
            optimalDay = 0
            optimalDate = "Reserve Alert: Fill Up Now"
        } else if (delta <= -0.04) {
            code = RecommendationCode.WAIT_TO_FILL
            signal = "🟢 WAIT TO FILL UP • TROUGH AHEAD"
            optimalDay = combined.forecast?.forecastHorizonDays ?: 3
            optimalDate = combined.forecast?.targetDate ?: "Optimal Timing: Day $optimalDay"
        } else if (delta >= 0.04) {
            code = RecommendationCode.FILL_NOW
            signal = "🔴 FILL UP TODAY • PRICES RISING"
            optimalDay = 0
            optimalDate = "Price Spike Anticipated: Fill Now"
        } else {
            code = RecommendationCode.STABLE
            signal = "🟡 PRICES STABLE • NORMAL FILL"
            optimalDay = 0
            optimalDate = "Market Conditions Stable"
        }

        val netSavings = savingsPerGal * shortfallGallons

        val confidence = if (combined.forecast?.directionalHitRateHistorical != null) {
            val hitRate = combined.forecast.directionalHitRateHistorical
            if (hitRate >= 0.70) "HIGH" else if (hitRate >= 0.50) "MEDIUM" else "LOW"
        } else "MEDIUM"

        return SavingsAdvisorResponse(
            locationId = locale,
            recommendationCode = code,
            displaySignal = signal,
            optimalDay = optimalDay,
            optimalDate = optimalDate,
            currentPriceGal = currentPrice,
            targetPriceGal = targetPrice,
            savingsPerGal = savingsPerGal,
            netTankSavingsUsd = netSavings,
            confidenceLevel = confidence,
            isCached = false,
            cacheAgeHours = 0.0
        )
    }

    fun transformCombinedToForecast(combined: CombinedApiResponse, locale: String): ForecastResponse {
        val basePrice = combined.liveLookup?.currentPricePerGal ?: combined.forecast?.currentBasePrice ?: 0.0
        val fc = combined.forecast
        val delta = fc?.expectedChangeDollars ?: 0.0
        val p1 = fc?.day1Price ?: (if (basePrice > 0) basePrice + delta * 0.2 else 0.0)
        val p2 = fc?.day2Price ?: (if (basePrice > 0) basePrice + delta * 0.4 else 0.0)
        val p3 = fc?.day3Price ?: (if (basePrice > 0) basePrice + delta * 0.6 else 0.0)
        val p4 = fc?.day4Price ?: (if (basePrice > 0) basePrice + delta * 0.8 else 0.0)
        val p5 = fc?.day5Price ?: fc?.predictedPricePerGal ?: (if (basePrice > 0) basePrice + delta else 0.0)

        // Point-in-time integrity: do not fabricate synthetic CI bands (Issue #15)
        val points = listOf(
            ForecastDayPoint(0, "Today", basePrice, null, null),
            ForecastDayPoint(1, "Tomorrow", p1, null, null),
            ForecastDayPoint(2, "Day 2", p2, null, null),
            ForecastDayPoint(3, "Day 3", p3, null, null),
            ForecastDayPoint(4, "Day 4", p4, null, null),
            ForecastDayPoint(5, "Day 5", p5, null, null)
        )
        return ForecastResponse(
            locationId = locale,
            asOfTimestamp = combined.timestamp.ifEmpty { "Live Model Stream" },
            basePrice = basePrice,
            forecast = points,
            directionalTrend = fc?.projectedDirection ?: "FLAT"
        )
    }

    private fun generateOfflineFallbackAdvisor(
        locale: String,
        tankCapacity: Double,
        fuelLevelPct: Double? = null
    ): SavingsAdvisorResponse {
        val isLowFuel = fuelLevelPct != null && fuelLevelPct < Obd2PidDecoder.LOW_FUEL_THRESHOLD_PERCENT
        val signal = if (isLowFuel) {
            "🔴 LOW FUEL (${fuelLevelPct!!.toInt()}%) • FILL UP NOW (Offline)"
        } else {
            "🟡 OFFLINE • FEED UNAVAILABLE"
        }

        return SavingsAdvisorResponse(
            locationId = locale,
            recommendationCode = if (isLowFuel) RecommendationCode.FILL_NOW else RecommendationCode.UNKNOWN,
            displaySignal = signal,
            optimalDay = 0,
            optimalDate = if (isLowFuel) "Reserve Alert: Fill Up Now" else "Offline • Reconnect to sync",
            currentPriceGal = 0.0,
            targetPriceGal = 0.0,
            savingsPerGal = 0.0,
            netTankSavingsUsd = 0.0,
            confidenceLevel = "OFFLINE_ESTIMATE",
            isCached = true,
            cacheAgeHours = 0.0
        )
    }
}
