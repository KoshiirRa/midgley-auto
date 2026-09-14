package net.n2yti.midgley.auto.service

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator

/**
 * Main Android Auto & Android Automotive OS entry point service.
 */
class MidgleyCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // Allows Android Auto Desktop Head Unit (DHU) and emulators to bind in debug mode
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return MidgleySession()
    }
}
