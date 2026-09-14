package net.n2yti.midgley.auto.ui

import android.Manifest
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import net.n2yti.midgley.auto.data.obd.Obd2BleManager
import net.n2yti.midgley.auto.data.obd.Obd2ConnectionState
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompanionSettingsScreenTest {

    private lateinit var context: android.content.Context
    private lateinit var preferenceManager: MetroPreferenceManager
    private lateinit var bleManager: Obd2BleManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<android.content.Context>()
        preferenceManager = MetroPreferenceManager(context)
        bleManager = Obd2BleManager(context)
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

    @Test
    @Config(sdk = [34])
    fun testRequiredPermissions_onAndroid12Plus() {
        val permissions = bleManager.getRequiredBluetoothPermissions()
        assertThat(permissions).asList().containsExactly(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }

    @Test
    @Config(sdk = [30])
    fun testRequiredPermissions_onLegacyAndroid() {
        val permissions = bleManager.getRequiredBluetoothPermissions()
        assertThat(permissions).asList().containsExactly(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    @Test
    fun testStartScan_withoutPermissions_transitionsToError() {
        // Clear all permissions
        val shadowApp = ShadowApplication.getInstance()
        shadowApp.denyPermissions(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        bleManager.startScan(15.0)
        val state = bleManager.connectionState.value
        assertThat(state).isInstanceOf(Obd2ConnectionState.Error::class.java)
        assertThat((state as Obd2ConnectionState.Error).message).contains("Bluetooth permissions required")
    }

    @Test
    fun testConnectToPairedDevice_withoutPermissions_transitionsToError() {
        val shadowApp = ShadowApplication.getInstance()
        shadowApp.denyPermissions(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        bleManager.connectToPairedDevice("00:11:22:33:44:55", 15.0)
        val state = bleManager.connectionState.value
        assertThat(state).isInstanceOf(Obd2ConnectionState.Error::class.java)
        assertThat((state as Obd2ConnectionState.Error).message).contains("Bluetooth permissions required")
    }

    @Test
    fun testGetPairedDevices_withoutPermissions_returnsEmptyList() {
        val shadowApp = ShadowApplication.getInstance()
        shadowApp.denyPermissions(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        val devices = bleManager.getPairedDevices()
        assertThat(devices).isEmpty()
    }
}
