package net.n2yti.midgley.auto.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActiveAlertItem(
    @SerialName("event_id") val eventId: String,
    @SerialName("category") val category: String,
    @SerialName("title") val title: String,
    @SerialName("severity") val severity: String = "INFO",
    @SerialName("price_impact_score") val priceImpactScore: Double = 0.0,
    @SerialName("issued_at") val issuedAt: String? = null
)

@Serializable
data class ActiveAlertsResponse(
    @SerialName("location_id") val locationId: String,
    @SerialName("active_alerts") val activeAlerts: List<ActiveAlertItem> = emptyList()
)
