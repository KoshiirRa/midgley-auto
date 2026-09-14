package net.n2yti.midgley.auto.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocaleInfo(
    @SerialName("code") val code: String = "",
    @SerialName("region_id") val regionId: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("padd_region") val paddRegion: String = ""
)

@Serializable
data class KeyDriver(
    @SerialName("category") val category: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("impact_score") val impactScore: Double = 0.0
)

@Serializable
data class LiveLookup(
    @SerialName("current_price_per_gal") val currentPricePerGal: Double = 0.0,
    @SerialName("source") val source: String? = null,
    @SerialName("provenance") val provenance: String? = null,
    @SerialName("cache_hit") val cacheHit: Boolean = false,
    @SerialName("cache_age_seconds") val cacheAgeSeconds: Double = 0.0,
    @SerialName("carb_tax_regulatory_burden_per_gal") val carbTaxRegulatoryBurdenPerGal: Double = 0.0
)

@Serializable
data class ForecastPayload(
    @SerialName("model_version") val modelVersion: String? = null,
    @SerialName("forecast_horizon_days") val forecastHorizonDays: Int? = 5,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("current_base_price") val currentBasePrice: Double? = null,
    @SerialName("predicted_price_per_gal") val predictedPricePerGal: Double? = null,
    @SerialName("expected_change_dollars") val expectedChangeDollars: Double? = null,
    @SerialName("expected_change_percent") val expectedChangePercent: Double? = null,
    @SerialName("projected_direction") val projectedDirection: String? = null,
    @SerialName("directional_hit_rate_historical") val directionalHitRateHistorical: Double? = null,
    @SerialName("historical_mae_dollars") val historicalMaeDollars: Double? = null,
    @SerialName("day_1_price") val day1Price: Double? = null,
    @SerialName("day_2_price") val day2Price: Double? = null,
    @SerialName("day_3_price") val day3Price: Double? = null,
    @SerialName("day_4_price") val day4Price: Double? = null,
    @SerialName("day_5_price") val day5Price: Double? = null
)

@Serializable
data class CombinedApiResponse(
    @SerialName("status") val status: String = "success",
    @SerialName("timestamp") val timestamp: String = "",
    @SerialName("locale") val locale: LocaleInfo? = null,
    @SerialName("live_lookup") val liveLookup: LiveLookup? = null,
    @SerialName("forecast") val forecast: ForecastPayload? = null,
    @SerialName("key_drivers") val keyDrivers: List<KeyDriver> = emptyList()
)
