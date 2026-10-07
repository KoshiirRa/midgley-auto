package net.n2yti.midgley.auto.service

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import net.n2yti.midgley.auto.R

/**
 * Main Android Auto & Android Automotive OS entry point service.
 */
class MidgleyCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // Allows Android Auto Desktop Head Unit (DHU) and emulators to bind in debug mode
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            // Strict host validation in release builds (Issue #14)
            try {
                HostValidator.Builder(applicationContext)
                    .addAllowedHosts(R.array.hosts_allowlist)
                    .build()
            } catch (_: Exception) {
                HostValidator.Builder(applicationContext)
                    .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                    .build()
            }
        }
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return MidgleySession()
    }
}
