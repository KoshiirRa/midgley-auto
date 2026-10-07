package net.n2yti.midgley.auto.data.location

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MetroLocationResolverTest {

    @Test
    fun testResolve_tulsaExactCoordinates() {
        val resolved = MetroLocationResolver.resolve(36.1540, -95.9928)
        assertThat(resolved.id).isEqualTo("tulsa")
        assertThat(resolved.padd).contains("PADD 2")
        assertThat(resolved.isExactMetro).isTrue()
        assertThat(resolved.distanceKm).isLessThan(1.0)
    }

    @Test
    fun testResolve_newarkCoordinates() {
        val resolved = MetroLocationResolver.resolve(40.7357, -74.1724)
        assertThat(resolved.id).isEqualTo("newark")
        assertThat(resolved.padd).contains("PADD 1B")
        assertThat(resolved.isExactMetro).isTrue()
    }

    @Test
    fun testResolve_cincinnatiCoordinates() {
        val resolved = MetroLocationResolver.resolve(39.1031, -84.5120)
        assertThat(resolved.id).isEqualTo("cincinnati")
        assertThat(resolved.padd).contains("PADD 2")
    }

    @Test
    fun testResolve_greenvilleCoordinates() {
        val resolved = MetroLocationResolver.resolve(35.6127, -77.3664)
        assertThat(resolved.id).isEqualTo("greenville")
        assertThat(resolved.padd).contains("PADD 1C")
    }

    @Test
    fun testResolve_charlotteCoordinates() {
        val resolved = MetroLocationResolver.resolve(35.2271, -80.8431)
        assertThat(resolved.id).isEqualTo("charlotte")
        assertThat(resolved.padd).contains("PADD 1C")
        assertThat(resolved.isExactMetro).isTrue()
    }

    @Test
    fun testResolve_oaklandCoordinates() {
        val resolved = MetroLocationResolver.resolve(37.8044, -122.2712)
        assertThat(resolved.id).isEqualTo("oakland")
        assertThat(resolved.padd).contains("PADD 5")
        assertThat(resolved.isExactMetro).isTrue()
    }

    @Test
    fun testResolve_californiaEasternSierras_mapsToOaklandCARB() {
        // Bishop / Eastern Sierras, CA (37.3614, -118.3997)
        val resolved = MetroLocationResolver.resolve(37.3614, -118.3997)
        assertThat(resolved.id).isEqualTo("oakland")
        assertThat(resolved.name).contains("CARB")
        assertThat(resolved.padd).contains("PADD 5")
    }

    @Test
    fun testResolve_renoNevada_doesNotMapToCaliforniaCARB() {
        // Reno, NV (39.5296, -119.8138) must NOT be captured by California bounding box (Issue #16)
        val resolved = MetroLocationResolver.resolve(39.5296, -119.8138)
        assertThat(resolved.id).isNotEqualTo("oakland")
        assertThat(resolved.name).doesNotContain("CARB")
    }

    @Test
    fun testResolve_lasVegasNevada_doesNotMapToCaliforniaCARB() {
        // Las Vegas, NV (36.1699, -115.1398) must NOT be captured by California bounding box (Issue #16)
        val resolved = MetroLocationResolver.resolve(36.1699, -115.1398)
        assertThat(resolved.id).isNotEqualTo("oakland")
        assertThat(resolved.name).doesNotContain("CARB")
    }

    @Test
    fun testResolve_lakeHavasuArizona_doesNotMapToCaliforniaCARB() {
        // Lake Havasu City, AZ (34.4839, -114.3224) must NOT be captured by California bounding box (Issue #16)
        val resolved = MetroLocationResolver.resolve(34.4839, -114.3224)
        assertThat(resolved.id).isNotEqualTo("oakland")
        assertThat(resolved.name).doesNotContain("CARB")
    }

    @Test
    fun testResolve_floridaPeninsula_mapsToPortStLucie() {
        // Orlando, FL (28.5383, -81.3792)
        val resolved = MetroLocationResolver.resolve(28.5383, -81.3792)
        assertThat(resolved.id).isEqualTo("port_st_lucie")
        assertThat(resolved.padd).contains("PADD 1C")
    }

    @Test
    fun testResolve_farIntermountainWest_fallsBackToNational() {
        // Salt Lake City, UT (40.7608, -111.8910) > 650 km from all hubs
        val resolved = MetroLocationResolver.resolve(40.7608, -111.8910)
        assertThat(resolved.id).isEqualTo("national")
        assertThat(resolved.name).contains("National Average")
        assertThat(resolved.isExactMetro).isFalse()
    }

    @Test
    fun testComputeHaversineKm_calculatesAccurately() {
        // NYC to Philadelphia ~ 130 km
        val distance = MetroLocationResolver.computeHaversineKm(40.7128, -74.0060, 39.9526, -75.1652)
        assertThat(distance).isWithin(10.0).of(130.0)
    }
}
