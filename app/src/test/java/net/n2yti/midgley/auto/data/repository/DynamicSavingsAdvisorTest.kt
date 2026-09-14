package net.n2yti.midgley.auto.data.repository

import net.n2yti.midgley.auto.data.models.CombinedApiResponse
import net.n2yti.midgley.auto.data.models.ForecastPayload
import net.n2yti.midgley.auto.data.models.KeyDriver
import net.n2yti.midgley.auto.data.models.LiveLookup
import net.n2yti.midgley.auto.data.models.LocaleInfo
import net.n2yti.midgley.auto.data.models.RecommendationCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicSavingsAdvisorTest {

    private val repository = MidgleyRepository()

    private fun createCombinedResponse(currentPrice: Double, day3Price: Double): CombinedApiResponse {
        return CombinedApiResponse(
            status = "success",
            timestamp = "2026-09-14T10:00:00Z",
            locale = LocaleInfo(code = "tulsa", name = "Tulsa Metro"),
            liveLookup = LiveLookup(
                currentPricePerGal = currentPrice,
                source = "LIVE_HUB_FEED"
            ),
            forecast = ForecastPayload(
                currentBasePrice = currentPrice,
                predictedPricePerGal = day3Price,
                projectedDirection = if (day3Price < currentPrice) "DOWN" else "UP",
                expectedChangeDollars = Math.abs(day3Price - currentPrice),
                day1Price = currentPrice - 0.05,
                day2Price = currentPrice - 0.10,
                day3Price = day3Price,
                day4Price = day3Price + 0.02,
                day5Price = day3Price + 0.05
            ),
            keyDrivers = listOf(KeyDriver(category = "Geopolitical", description = "OPEC supply surge", impactScore = 0.12))
        )
    }

    @Test
    fun testDynamicShortfallSavingsOnPriceDrop() {
        // Current: $3.50, Day 3: $3.30 (Drop: -$0.20/gal)
        val combined = createCombinedResponse(currentPrice = 3.50, day3Price = 3.30)

        // 15 gallon tank with 40% fuel remaining -> 6.0 gal remaining, 9.0 gal shortfall
        val advisor = repository.transformCombinedToAdvisor(
            combined = combined,
            locale = "tulsa",
            tankCapacity = 15.0,
            fuelLevelPct = 40.0
        )

        assertEquals(RecommendationCode.WAIT_TO_FILL, advisor.recommendationCode)
        assertEquals(0.20, advisor.savingsPerGal, 0.001)
        // 9.0 gal * $0.20 = $1.80 net savings
        assertEquals(1.80, advisor.netTankSavingsUsd, 0.01)
        assertTrue(advisor.displaySignal.contains("WAIT TO FILL UP"))
    }

    @Test
    fun testLowFuelReserveSafetyOverride() {
        // Even when huge price drop (-$0.50/gal) is predicted:
        val combined = createCombinedResponse(currentPrice = 3.50, day3Price = 3.00)

        // Fuel level at 10% (below 15% safety threshold)
        val advisor = repository.transformCombinedToAdvisor(
            combined = combined,
            locale = "tulsa",
            tankCapacity = 15.0,
            fuelLevelPct = 10.0
        )

        // Must override to FILL_NOW to prevent driver running out of fuel
        assertEquals(RecommendationCode.FILL_NOW, advisor.recommendationCode)
        assertTrue(advisor.displaySignal.contains("LOW FUEL (10%)"))
        assertTrue(advisor.optimalDate.contains("Reserve Alert"))
    }

    @Test
    fun testPriceSurgeRecommendationWithDynamicShortfall() {
        // Current: $3.50, Day 3: $3.75 (Surge: +$0.25/gal)
        val combined = createCombinedResponse(currentPrice = 3.50, day3Price = 3.75)

        // 20 gallon tank with 50% fuel remaining -> 10.0 gal shortfall
        val advisor = repository.transformCombinedToAdvisor(
            combined = combined,
            locale = "tulsa",
            tankCapacity = 20.0,
            fuelLevelPct = 50.0
        )

        assertEquals(RecommendationCode.FILL_NOW, advisor.recommendationCode)
        assertTrue(advisor.displaySignal.contains("FILL UP TODAY"))
        assertEquals(0.0, advisor.savingsPerGal, 0.001) // No trough savings
    }

    @Test
    fun testFallbackWhenFuelLevelNotAvailable() {
        // When OBD2 is disconnected (null fuel level) -> uses full tank capacity
        val combined = createCombinedResponse(currentPrice = 3.50, day3Price = 3.30)

        val advisor = repository.transformCombinedToAdvisor(
            combined = combined,
            locale = "tulsa",
            tankCapacity = 15.0,
            fuelLevelPct = null
        )

        assertEquals(RecommendationCode.WAIT_TO_FILL, advisor.recommendationCode)
        assertEquals(0.20, advisor.savingsPerGal, 0.001)
        // 15.0 gal * $0.20 = $3.00
        assertEquals(3.00, advisor.netTankSavingsUsd, 0.01)
    }
}
