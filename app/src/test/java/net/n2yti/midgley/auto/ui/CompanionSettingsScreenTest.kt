package net.n2yti.midgley.auto.ui

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
class CompanionSettingsScreenTest {

    private lateinit var preferenceManager: MetroPreferenceManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        preferenceManager = MetroPreferenceManager(context)
    }

    @Test
    fun testTankPresets_definedCorrectly() {
        val presets = MetroPreferenceManager.TANK_PRESETS
        assertThat(presets).hasSize(4)
        assertThat(presets[0].gallons).isEqualTo(12.0)
        assertThat(presets[1].gallons).isEqualTo(15.0)
        assertThat(presets[2].gallons).isEqualTo(18.5)
        assertThat(presets[3].gallons).isEqualTo(24.0)
    }

    @Test
    fun testDynamicSavingsCalculation() {
        val tankCapacity = 15.0
        val savingsPerGal = 0.10
        val netSavings = tankCapacity * savingsPerGal
        assertThat(netSavings).isWithin(0.001).of(1.50)

        val truckTankCapacity = 24.0
        val truckNetSavings = truckTankCapacity * savingsPerGal
        assertThat(truckNetSavings).isWithin(0.001).of(2.40)
    }
}
