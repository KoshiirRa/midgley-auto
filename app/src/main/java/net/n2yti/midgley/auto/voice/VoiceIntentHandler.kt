package net.n2yti.midgley.auto.voice

import android.content.Intent
import android.net.Uri

/**
 * Handles parsing of voice intents, App Action triggers, and deep-link routing.
 */
object VoiceIntentHandler {

    sealed class VoiceAction {
        data class OpenAdvisor(val targetLocale: String?) : VoiceAction()
        data class OpenForecast(val targetLocale: String?) : VoiceAction()
        object None : VoiceAction()
    }

    const val ACTION_GET_ADVISOR = "net.n2yti.midgley.auto.intent.GET_FUEL_ADVISOR"
    const val ACTION_GET_FORECAST = "net.n2yti.midgley.auto.intent.GET_FUEL_FORECAST"

    fun parseIntent(intent: Intent?): VoiceAction {
        if (intent == null) return VoiceAction.None

        // 1. Check Deep-Link URI (midgley://advisor?locale=tulsa or midgley://forecast?locale=oakland)
        val dataUri: Uri? = intent.data
        if (dataUri != null && dataUri.scheme.equals("midgley", ignoreCase = true)) {
            val host = dataUri.host?.lowercase() ?: ""
            val localeParam = dataUri.getQueryParameter("locale")
                ?: dataUri.getQueryParameter("target_locale")
                ?: dataUri.getQueryParameter("city")

            return when (host) {
                "advisor", "savings" -> VoiceAction.OpenAdvisor(normalizeLocale(localeParam))
                "forecast", "predict" -> VoiceAction.OpenForecast(normalizeLocale(localeParam))
                else -> VoiceAction.OpenAdvisor(normalizeLocale(localeParam))
            }
        }

        // 2. Check Action Intent
        val action = intent.action ?: ""
        val localeExtra = intent.getStringExtra("locale")
            ?: intent.getStringExtra("target_locale")
            ?: intent.getStringExtra("city")
            ?: intent.getStringExtra("metro")

        return when (action) {
            ACTION_GET_ADVISOR -> VoiceAction.OpenAdvisor(normalizeLocale(localeExtra))
            ACTION_GET_FORECAST -> VoiceAction.OpenForecast(normalizeLocale(localeExtra))
            Intent.ACTION_VIEW -> VoiceAction.OpenAdvisor(normalizeLocale(localeExtra))
            else -> VoiceAction.None
        }
    }

    private fun normalizeLocale(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val clean = input.trim().lowercase()
        return when {
            clean.contains("tulsa") -> "tulsa"
            clean.contains("newark") || clean.contains("jersey") || clean.contains("delaware") -> "newark"
            clean.contains("cincinnati") || clean.contains("ohio") -> "cincinnati"
            clean.contains("greenville") || clean.contains("charlotte") || clean.contains("carolina") -> "greenville"
            clean.contains("oakland") || clean.contains("bay") || clean.contains("francisco") || clean.contains("california") -> "oakland"
            clean.contains("lucie") || clean.contains("florida") || clean.contains("miami") -> "port_st_lucie"
            clean.contains("national") || clean.contains("us") || clean.contains("average") -> "national"
            else -> clean
        }
    }
}
