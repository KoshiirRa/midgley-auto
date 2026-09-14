package net.n2yti.midgley.auto.data.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Intelligent Geodesic & Regional PADD Boundary Location Resolver.
 * Maps vehicle GPS coordinates to the closest logical refining hub.
 */
object MetroLocationResolver {

    const val MAX_REGIONAL_CUTOFF_KM = 650.0

    data class HubCoord(
        val id: String,
        val name: String,
        val padd: String,
        val lat: Double,
        val lon: Double
    )

    data class ResolvedMetro(
        val id: String,
        val name: String,
        val padd: String,
        val distanceKm: Double,
        val isExactMetro: Boolean
    )

    private val HUBS = listOf(
        HubCoord("tulsa", "Tulsa Metro Area", "PADD 2 (Midwest • Cushing WTI)", 36.1540, -95.9928),
        HubCoord("newark", "Newark Metro Area", "PADD 1B (Central Atlantic • Delaware City)", 40.7357, -74.1724),
        HubCoord("cincinnati", "Cincinnati Tri-State", "PADD 2 (Midwest • Ohio River)", 39.1031, -84.5120),
        HubCoord("greenville", "Greenville & Charlotte", "PADD 1C (Lower Atlantic • Colonial Pipeline)", 35.6127, -77.3664),
        HubCoord("oakland", "Oakland & SF Bay Area", "PADD 5 (West Coast • CARB)", 37.8044, -122.2712),
        HubCoord("port_st_lucie", "Port St. Lucie", "PADD 1C (Lower Atlantic • Waterborne)", 27.2730, -80.3582)
    )

    /**
     * Resolves given GPS coordinate to the closest logical refining hub or national baseline.
     */
    fun resolve(lat: Double, lon: Double): ResolvedMetro {
        // 1. California CARB Mandate Boundary Check (Statewide CA follows CARB-spec pricing)
        if (isInsideCalifornia(lat, lon)) {
            val dist = computeHaversineKm(lat, lon, 37.8044, -122.2712)
            return ResolvedMetro(
                id = "oakland",
                name = "Oakland & SF Bay Area (PADD 5 CARB)",
                padd = "PADD 5 (West Coast)",
                distanceKm = dist,
                isExactMetro = dist <= 120.0
            )
        }

        // 2. Florida Waterborne Delivery Zone Check
        if (isInsideFlorida(lat, lon)) {
            val dist = computeHaversineKm(lat, lon, 27.2730, -80.3582)
            return ResolvedMetro(
                id = "port_st_lucie",
                name = "Port St. Lucie (Florida Waterborne)",
                padd = "PADD 1C (Lower Atlantic)",
                distanceKm = dist,
                isExactMetro = dist <= 120.0
            )
        }

        // 3. Find closest hub by Haversine distance
        var nearestHub = HUBS.first()
        var minDistance = Double.MAX_VALUE

        for (hub in HUBS) {
            val dist = computeHaversineKm(lat, lon, hub.lat, hub.lon)
            if (dist < minDistance) {
                minDistance = dist
                nearestHub = hub
            }
        }

        // 4. If beyond 650 km from any modeled regional hub, fall back to US National Average
        return if (minDistance > MAX_REGIONAL_CUTOFF_KM) {
            ResolvedMetro(
                id = "national",
                name = "National Average Baseline",
                padd = "US Benchmark",
                distanceKm = minDistance,
                isExactMetro = false
            )
        } else {
            ResolvedMetro(
                id = nearestHub.id,
                name = nearestHub.name,
                padd = nearestHub.padd,
                distanceKm = minDistance,
                isExactMetro = minDistance <= 80.0
            )
        }
    }

    /**
     * Computes great-circle distance between two points on Earth in kilometers using Haversine formula.
     */
    fun computeHaversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    private fun isInsideCalifornia(lat: Double, lon: Double): Boolean {
        // Approximate California state bounding polygon
        return lat in 32.5..42.0 && lon in -124.5..-114.1
    }

    private fun isInsideFlorida(lat: Double, lon: Double): Boolean {
        // Approximate Florida peninsula bounding polygon
        return lat in 24.5..31.0 && lon in -87.6..-80.0
    }
}
