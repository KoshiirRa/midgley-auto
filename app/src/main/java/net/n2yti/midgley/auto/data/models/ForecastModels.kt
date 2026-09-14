package net.n2yti.midgley.auto.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecastDayPoint(
    @SerialName("day") val day: Int,
    @SerialName("date") val date: String,
    @SerialName("p50") val p50: Double,
    @SerialName("p10") val p10: Double? = null,
    @SerialName("p90") val p90: Double? = null
)

@Serializable
data class ForecastResponse(
    @SerialName("location_id") val locationId: String,
    @SerialName("as_of_timestamp") val asOfTimestamp: String,
    @SerialName("base_price") val basePrice: Double,
    @SerialName("forecast") val forecast: List<ForecastDayPoint> = emptyList(),
    @SerialName("directional_trend") val directionalTrend: String? = null
)
