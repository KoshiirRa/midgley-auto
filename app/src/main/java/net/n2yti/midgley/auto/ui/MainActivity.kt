package net.n2yti.midgley.auto.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager

/**
 * Mobile Companion Activity displayed on the handset display.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val preferenceManager = remember { MetroPreferenceManager(this@MainActivity) }
                    CompanionSettingsScreen(preferenceManager = preferenceManager)
                }
            }
        }
    }
}