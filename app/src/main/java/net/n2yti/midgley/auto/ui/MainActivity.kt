package net.n2yti.midgley.auto.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager
import net.n2yti.midgley.auto.ui.theme.MidgleyAutoTheme

/**
 * Mobile Companion Activity displayed on the handset display.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferenceManager = remember { MetroPreferenceManager(this@MainActivity) }
            var currentThemeMode by remember { mutableStateOf(preferenceManager.getThemeMode()) }

            MidgleyAutoTheme(themeMode = currentThemeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    CompanionSettingsScreen(
                        preferenceManager = preferenceManager,
                        currentThemeMode = currentThemeMode,
                        onThemeModeChanged = { newMode ->
                            currentThemeMode = newMode
                            preferenceManager.setThemeMode(newMode)
                        }
                    )
                }
            }
        }
    }
}
