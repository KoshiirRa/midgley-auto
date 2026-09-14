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
}
