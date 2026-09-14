package net.n2yti.midgley.auto.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LiveLookup(
    @SerialName("current_price_per_gal") val currentPricePerGal: Double = 0.0,
    @SerialName("source") val source: String? = null,
    @SerialName("provenance") val provenance: String? = null,
    @SerialName("cache_hit") val cacheHit: Boolean = false,
    @SerialName("cache_age_seconds") val cacheAgeSeconds: Double = 0.0
)

@Serializable
data class ForecastPayload(
    @SerialName("current_base_price") val currentBasePrice: Double? = null,
    @SerialName("predicted_price_per_gal") val predictedPricePerGal: Double? = null,
    @SerialName("direction") val direction: String? = null,
    @SerialName("expected_change_cents") val expectedChangeCents: Double? = null,
    @SerialName("day_1_price") val day1Price: Double? = null,
    @SerialName("day_2_price") val day2Price: Double? = null,
    @SerialName("day_3_price") val day3Price: Double? = null,
    @SerialName("day_4_price") val day4Price: Double? = null,
    @SerialName("day_5_price") val day5Price: Double? = null
)

@Serializable
data class CombinedApiResponse(
    @SerialName("status") val status: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("locale") val locale: String,
    @SerialName("live_lookup") val liveLookup: LiveLookup? = null,
    @SerialName("forecast") val forecast: ForecastPayload? = null,
    @SerialName("key_drivers") val keyDrivers: List<String> = emptyList()
)
