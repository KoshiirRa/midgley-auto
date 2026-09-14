package net.n2yti.midgley.auto.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocationResolveResponse(
    @SerialName("location_id") val locationId: String,
    @SerialName("location_name") val locationName: String,
    @SerialName("state") val state: String,
    @SerialName("padd") val padd: String,
    @SerialName("distance_km") val distanceKm: Double = 0.0
)
