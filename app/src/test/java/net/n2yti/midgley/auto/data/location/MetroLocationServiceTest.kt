package net.n2yti.midgley.auto.data.location

import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetroLocationServiceTest {

    private lateinit var preferenceManager: MetroPreferenceManager
    private lateinit var locationService: MetroLocationService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        preferenceManager = MetroPreferenceManager(context)
        preferenceManager.setAutoDetect(true)
        locationService = MetroLocationService(context, preferenceManager)
    }

    @Test
    fun testHandleNewLocation_triggersCallbackWhenAutoDetectEnabled() {
        var callbackTriggered = false
        var resolvedHubId = ""

        val location = Location("test_provider").apply {
            latitude = 40.7357
            longitude = -74.1724
        }

        locationService.handleNewLocation(location) { resolved ->
            callbackTriggered = true
            resolvedHubId = resolved.id
        }

        assertThat(callbackTriggered).isTrue()
        assertThat(resolvedHubId).isEqualTo("newark")
        assertThat(locationService.currentResolvedMetro.value?.id).isEqualTo("newark")
    }

    @Test
    fun testHandleNewLocation_doesNotTriggerCallbackWhenManualModeSelected() {
        preferenceManager.setSelectedLocale("oakland")
        assertThat(preferenceManager.isAutoDetect()).isFalse()

        var callbackTriggered = false
        val location = Location("test_provider").apply {
            latitude = 40.7357
            longitude = -74.1724
        }

        locationService.handleNewLocation(location) {
            callbackTriggered = true
        }

        assertThat(callbackTriggered).isFalse()
        assertThat(preferenceManager.getSelectedLocale()).isEqualTo("oakland")
    }
}
