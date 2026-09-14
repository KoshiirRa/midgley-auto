package net.n2yti.midgley.auto.data.preferences

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetroPreferenceManagerTest {

    private lateinit var preferenceManager: MetroPreferenceManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        preferenceManager = MetroPreferenceManager(context)
        // Reset to default
        preferenceManager.setAutoDetect(true)
        preferenceManager.setTankCapacityGallons(15.0)
        preferenceManager.setAlertThresholdCents(4)
        preferenceManager.setSevereWeatherAlertsEnabled(true)
    }

    @Test
    fun testDefaults_isAutoDetectTrueAndDefaultLocaleTulsa() {
        assertThat(preferenceManager.isAutoDetect()).isTrue()
        assertThat(preferenceManager.getSelectedLocale()).isEqualTo("tulsa")
    }

    @Test
    fun testSetSelectedLocale_disablesAutoDetectAndSavesLocale() {
        preferenceManager.setSelectedLocale("oakland")
        assertThat(preferenceManager.isAutoDetect()).isFalse()
        assertThat(preferenceManager.getSelectedLocale()).isEqualTo("oakland")
        assertThat(preferenceManager.getDisplayName("oakland")).contains("Oakland")
    }

    @Test
    fun testSetAutoDetect_reEnablesAutoDetect() {
        preferenceManager.setSelectedLocale("newark")
        assertThat(preferenceManager.isAutoDetect()).isFalse()

        preferenceManager.setAutoDetect(true)
        assertThat(preferenceManager.isAutoDetect()).isTrue()
    }

    @Test
    fun testTankCapacity_getAndSet() {
        preferenceManager.setTankCapacityGallons(24.0)
        assertThat(preferenceManager.getTankCapacityGallons()).isEqualTo(24.0)
    }

    @Test
    fun testAlertThreshold_getAndSet() {
        preferenceManager.setAlertThresholdCents(8)
        assertThat(preferenceManager.getAlertThresholdCents()).isEqualTo(8)
    }

    @Test
    fun testSevereWeatherAlerts_toggle() {
        preferenceManager.setSevereWeatherAlertsEnabled(false)
        assertThat(preferenceManager.isSevereWeatherAlertsEnabled()).isFalse()
    }

    @Test
    fun testApiBaseUrl_customEndpoint() {
        preferenceManager.setApiBaseUrl("http://10.42.42.54:8000/api/v1/")
        assertThat(preferenceManager.getApiBaseUrl()).contains("10.42.42.54")
    }
}
