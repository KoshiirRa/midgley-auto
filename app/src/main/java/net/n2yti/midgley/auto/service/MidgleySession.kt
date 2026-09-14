package net.n2yti.midgley.auto.service

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import net.n2yti.midgley.auto.ui.MainCarScreen

/**
 * Manages the Android Auto head unit session lifecycle.
 */
class MidgleySession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return MainCarScreen(carContext)
    }
}
