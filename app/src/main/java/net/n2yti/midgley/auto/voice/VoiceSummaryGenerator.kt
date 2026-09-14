package net.n2yti.midgley.auto.voice

import net.n2yti.midgley.auto.data.models.ForecastResponse
import net.n2yti.midgley.auto.data.models.RecommendationCode
import net.n2yti.midgley.auto.data.models.SavingsAdvisorResponse
import kotlin.math.roundToInt

/**
 * Synthesizes concise, natural spoken English summaries for in-dash voice feedback
 * and Google Assistant hands-free App Actions responses.
 */
object VoiceSummaryGenerator {

    /**
     * Generates a conversational audio summary for the smart fill-up advisor.
     */
    fun generateSpokenAdvisorSummary(
        advisor: SavingsAdvisorResponse,
        metroName: String
    ): String {
        val currentPriceText = "$%.2f".format(advisor.currentPriceGal)
        val targetPriceText = "$%.2f".format(advisor.targetPriceGal)
        val centsSavings = (advisor.savingsPerGal * 100.0).roundToInt()
        val tankSavingsText = "$%.2f".format(advisor.netTankSavingsUsd)
        val timing = if (advisor.optimalDate.isNotBlank()) advisor.optimalDate else "Day 3"

        val baseSpeech = when (advisor.recommendationCode) {
            RecommendationCode.WAIT_TO_FILL -> {
                "In $metroName, regular unleaded is currently $currentPriceText per gallon. " +
                        "Prices are expected to drop $centsSavings cents by $timing to $targetPriceText. " +
                        "Recommendation: wait to fill up to save about $tankSavingsText on a full tank."
            }
            RecommendationCode.FILL_NOW -> {
                val surgeCents = ((advisor.targetPriceGal - advisor.currentPriceGal) * 100.0).roundToInt()
                val surgePhrase = if (surgeCents > 0) "A price surge of $surgeCents cents" else "A price increase"
                "In $metroName, regular unleaded is currently $currentPriceText per gallon. " +
                        "$surgePhrase is projected within 24 to 48 hours. " +
                        "Recommendation: fill up today before prices rise."
            }
            RecommendationCode.STABLE -> {
                "In $metroName, regular unleaded is steady at $currentPriceText per gallon. " +
                        "Prices are projected to remain stable over the next five days."
            }
            RecommendationCode.UNKNOWN -> {
                "In $metroName, regular unleaded is currently $currentPriceText per gallon. " +
                        "Market conditions are currently being evaluated."
            }
        }

        return if (advisor.isCached) {
            "$baseSpeech Note: using cached market data."
        } else {
            baseSpeech
        }
    }

    /**
     * Generates a conversational audio overview for 5-day price trajectories.
     */
    fun generateSpokenForecastSummary(
        forecast: ForecastResponse,
        metroName: String
    ): String {
        val basePriceText = "$%.2f".format(forecast.basePrice)
        if (forecast.forecast.isEmpty()) {
            return "In $metroName, regular unleaded baseline is $basePriceText per gallon."
        }

        val day3 = forecast.forecast.find { it.day == 3 } ?: forecast.forecast.last()
        val day3PriceText = "$%.2f".format(day3.p50)
        val deltaCents = ((day3.p50 - forecast.basePrice) * 100.0).roundToInt()

        val trendProse = when {
            deltaCents <= -4 -> "projected to trend downward to $day3PriceText by Day 3"
            deltaCents >= 4 -> "projected to surge upward to $day3PriceText by Day 3"
            else -> "expected to stay relatively flat near $day3PriceText"
        }

        return "In $metroName, gas prices start at $basePriceText today and are $trendProse."
    }
}
