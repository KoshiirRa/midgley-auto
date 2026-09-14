package net.n2yti.midgley.auto.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages sticky user preference for active metro refining hub vs GPS auto-detection.
 */
class MetroPreferenceManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "midgley_auto_prefs"
        private const val KEY_SELECTED_LOCALE = "selected_locale"
        private const val KEY_AUTO_DETECT = "auto_detect_enabled"
        const val DEFAULT_LOCALE = "tulsa"

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

    fun getDisplayName(localeId: String): String {
        return SUPPORTED_METROS.find { it.id.equals(localeId, ignoreCase = true) }?.name
            ?: localeId.replaceFirstChar { it.uppercase() }
    }

    fun getDescription(localeId: String): String {
        return SUPPORTED_METROS.find { it.id.equals(localeId, ignoreCase = true) }?.description
            ?: "Refining Hub"
    }
}
