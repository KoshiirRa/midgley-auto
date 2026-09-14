package net.n2yti.midgley.auto.service

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager
import net.n2yti.midgley.auto.data.repository.MidgleyRepository
import net.n2yti.midgley.auto.ui.ForecastDetailScreen
import net.n2yti.midgley.auto.ui.MainCarScreen
import net.n2yti.midgley.auto.voice.VoiceIntentHandler

/**
 * Manages the Android Auto head unit session lifecycle and dynamic voice intent dispatching.
 */
class MidgleySession : Session() {

    private lateinit var preferenceManager: MetroPreferenceManager
    private val repository = MidgleyRepository()

    override fun onCreateScreen(intent: Intent): Screen {
        preferenceManager = MetroPreferenceManager(carContext)
        handleVoiceIntent(intent)
        return MainCarScreen(carContext, repository, preferenceManager)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoiceIntent(intent)
    }

    private fun handleVoiceIntent(intent: Intent?) {
        val action = VoiceIntentHandler.parseIntent(intent)
        when (action) {
            is VoiceIntentHandler.VoiceAction.OpenAdvisor -> {
                action.targetLocale?.let { locale ->
                    if (::preferenceManager.isInitialized) {
                        preferenceManager.setSelectedLocale(locale)
                    }
                }
            }
            is VoiceIntentHandler.VoiceAction.OpenForecast -> {
                val targetLocale = action.targetLocale ?: if (::preferenceManager.isInitialized) preferenceManager.getSelectedLocale() else "tulsa"
                val localeName = if (::preferenceManager.isInitialized) preferenceManager.getDisplayName(targetLocale) else "Tulsa Metro"
                carContext.getCarService(ScreenManager::class.java).push(
                    ForecastDetailScreen(carContext, targetLocale, localeName, repository)
                )
            }
            is VoiceIntentHandler.VoiceAction.None -> {}
        }
    }
}
