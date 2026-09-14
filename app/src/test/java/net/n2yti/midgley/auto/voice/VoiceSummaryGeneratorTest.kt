package net.n2yti.midgley.auto.voice

import com.google.common.truth.Truth.assertThat
import net.n2yti.midgley.auto.data.models.ForecastDayPoint
import net.n2yti.midgley.auto.data.models.ForecastResponse
import net.n2yti.midgley.auto.data.models.RecommendationCode
import net.n2yti.midgley.auto.data.models.SavingsAdvisorResponse
import org.junit.Test

class VoiceSummaryGeneratorTest {

    @Test
    fun testGenerateSpokenAdvisorSummary_waitToFill() {
        val advisor = SavingsAdvisorResponse(
            locationId = "tulsa",
            recommendationCode = RecommendationCode.WAIT_TO_FILL,
            displaySignal = "🟢 WAIT TO FILL UP",
            optimalDay = 3,
            optimalDate = "Friday",
            currentPriceGal = 3.89,
            targetPriceGal = 3.79,
            savingsPerGal = 0.10,
            netTankSavingsUsd = 1.50
        )

        val speech = VoiceSummaryGenerator.generateSpokenAdvisorSummary(advisor, "Tulsa Metro Area")
        assertThat(speech).contains("Tulsa Metro Area")
        assertThat(speech).contains("3.89")
        assertThat(speech).contains("drop 10 cents")
        assertThat(speech).contains("Friday")
        assertThat(speech).contains("wait to fill up to save about $1.50")
    }

    @Test
    fun testGenerateSpokenAdvisorSummary_fillNow() {
        val advisor = SavingsAdvisorResponse(
            locationId = "newark",
            recommendationCode = RecommendationCode.FILL_NOW,
            displaySignal = "🔴 FILL UP NOW",
            optimalDay = 1,
            optimalDate = "Tomorrow",
            currentPriceGal = 3.45,
            targetPriceGal = 3.59,
            savingsPerGal = 0.0,
            netTankSavingsUsd = 0.0
        )

        val speech = VoiceSummaryGenerator.generateSpokenAdvisorSummary(advisor, "Newark Metro")
        assertThat(speech).contains("Newark Metro")
        assertThat(speech).contains("3.45")
        assertThat(speech).contains("price surge")
        assertThat(speech).contains("fill up today")
    }

    @Test
    fun testGenerateSpokenAdvisorSummary_stable() {
        val advisor = SavingsAdvisorResponse(
            locationId = "national",
            recommendationCode = RecommendationCode.STABLE,
            displaySignal = "🟡 PRICES STABLE",
            currentPriceGal = 3.50,
            targetPriceGal = 3.50
        )

        val speech = VoiceSummaryGenerator.generateSpokenAdvisorSummary(advisor, "National Average")
        assertThat(speech).contains("National Average")
        assertThat(speech).contains("3.50")
        assertThat(speech).contains("remain stable over the next five days")
    }

    @Test
    fun testGenerateSpokenForecastSummary_downwardTrend() {
        val forecast = ForecastResponse(
            locationId = "tulsa",
            asOfTimestamp = "2026-09-14T00:00:00Z",
            basePrice = 3.89,
            forecast = listOf(
                ForecastDayPoint(0, "Today", 3.89),
                ForecastDayPoint(3, "Friday", 3.79)
            )
        )

        val speech = VoiceSummaryGenerator.generateSpokenForecastSummary(forecast, "Tulsa")
        assertThat(speech).contains("Tulsa")
        assertThat(speech).contains("3.89")
        assertThat(speech).contains("trend downward to $3.79 by Day 3")
    }
}
