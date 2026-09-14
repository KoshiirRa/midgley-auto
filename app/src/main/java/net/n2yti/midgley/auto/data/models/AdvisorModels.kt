package net.n2yti.midgley.auto.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RecommendationCode {
    @SerialName("FILL_NOW") FILL_NOW,
    @SerialName("WAIT_TO_FILL") WAIT_TO_FILL,
    @SerialName("STABLE") STABLE,
    @SerialName("UNKNOWN") UNKNOWN
}

@Serializable
data class SavingsAdvisorResponse(
    @SerialName("location_id") val locationId: String,
    @SerialName("recommendation_code") val recommendationCode: RecommendationCode = RecommendationCode.UNKNOWN,
    @SerialName("display_signal") val displaySignal: String,
    @SerialName("optimal_day") val optimalDay: Int = 0,
    @SerialName("optimal_date") val optimalDate: String = "",
    @SerialName("current_price_gal") val currentPriceGal: Double,
    @SerialName("target_price_gal") val targetPriceGal: Double,
    @SerialName("savings_per_gal") val savingsPerGal: Double = 0.0,
    @SerialName("net_tank_savings_usd") val netTankSavingsUsd: Double = 0.0,
    @SerialName("confidence_level") val confidenceLevel: String = "MEDIUM",
    @SerialName("is_cached") val isCached: Boolean = false,
    @SerialName("cache_age_hours") val cacheAgeHours: Double = 0.0
)
