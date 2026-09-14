package net.n2yti.midgley.auto.data.preferences

import android.content.Context
import android.content.SharedPreferences
import net.n2yti.midgley.auto.BuildConfig

/**
 * Manages sticky user preference for active metro refining hub, vehicle tank capacity,
 * alert sensitivity thresholds, and backend API endpoints.
 */
class MetroPreferenceManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "midgley_auto_prefs"
        private const val KEY_SELECTED_LOCALE = "selected_locale"
        private const val KEY_AUTO_DETECT = "auto_detect_enabled"
        private const val KEY_TANK_CAPACITY = "tank_capacity_gallons"
        private const val KEY_ALERT_THRESHOLD = "alert_threshold_cents"
        private const val KEY_WEATHER_ALERTS = "weather_alerts_enabled"
        private const val KEY_API_BASE_URL = "api_base_url"

        const val DEFAULT_LOCALE = "tulsa"
        const val DEFAULT_TANK_CAPACITY = 15.0
        const val DEFAULT_ALERT_THRESHOLD = 4

        val TANK_PRESETS = listOf(
            TankPreset("Compact / Hatchback", 12.0),
            TankPreset("Sedan / Midsize", 15.0),
            TankPreset("SUV / Crossover", 18.5),
            TankPreset("Truck / Full-Size", 24.0)
        )

        val SUPPORTED_METROS = listOf(
            MetroHub("tulsa", "Tulsa Metro Area", "PADD 2 • Cushing WTI / West Tulsa"),
            MetroHub("newark", "Newark Metro Area", "PADD 1B • Delaware City Detour"),
            MetroHub("cincinnati", "Cincinnati Tri-State", "PADD 2 • Ohio/Miss River Barges"),
            MetroHub("greenville", "Greenville & Charlotte", "PADD 1C • Colonial Pipeline"),
            MetroHub("oakland", "Oakland & SF Bay Area", "PADD 5 • CARB / Richmond"),
            MetroHub("port_st_lucie", "Port St. Lucie", "PADD 1C • Waterborne Terminals"),
            MetroHub("national", "National Average", "US National Baseline")
        )
    }

    data class TankPreset(
        val label: String,
        val gallons: Double
    )

    data class MetroHub(
        val id: String,
        val name: String,
        val description: String
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoDetect(): Boolean {
        return prefs.getBoolean(KEY_AUTO_DETECT, true)
    }

    fun setAutoDetect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DETECT, enabled).apply()
    }

    fun getSelectedLocale(): String {
        return prefs.getString(KEY_SELECTED_LOCALE, DEFAULT_LOCALE) ?: DEFAULT_LOCALE
    }

    fun setSelectedLocale(locale: String) {
        prefs.edit()
            .putString(KEY_SELECTED_LOCALE, locale)
            .putBoolean(KEY_AUTO_DETECT, false)
            .apply()
    }

    fun getTankCapacityGallons(): Double {
        return prefs.getFloat(KEY_TANK_CAPACITY, DEFAULT_TANK_CAPACITY.toFloat()).toDouble()
    }

    fun setTankCapacityGallons(gallons: Double) {
        prefs.edit().putFloat(KEY_TANK_CAPACITY, gallons.toFloat()).apply()
    }

    fun getAlertThresholdCents(): Int {
        return prefs.getInt(KEY_ALERT_THRESHOLD, DEFAULT_ALERT_THRESHOLD)
    }

    fun setAlertThresholdCents(cents: Int) {
        prefs.edit().putInt(KEY_ALERT_THRESHOLD, cents).apply()
    }

    fun isSevereWeatherAlertsEnabled(): Boolean {
        return prefs.getBoolean(KEY_WEATHER_ALERTS, true)
    }

    fun setSevereWeatherAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEATHER_ALERTS, enabled).apply()
    }

    fun getApiBaseUrl(): String {
        return prefs.getString(KEY_API_BASE_URL, BuildConfig.BASE_URL) ?: BuildConfig.BASE_URL
    }

    fun setApiBaseUrl(url: String) {
        prefs.edit().putString(KEY_API_BASE_URL, url).apply()
    }

    fun getDisplayName(localeId: String): String {
        return SUPPORTED_METROS.find { it.id.equals(localeId, ignoreCase = true) }?.name
            ?: localeId.replaceFirstChar { it.uppercase() }
    }

    fun getDescription(localeId: String): String {
        return SUPPORTED_METROS.find { it.id.equals(localeId, ignoreCase = true) }?.description
            ?: "Refining Hub"
    }
}
