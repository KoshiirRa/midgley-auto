package net.n2yti.midgley.auto.ui

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.n2yti.midgley.auto.data.models.SavingsAdvisorResponse
import net.n2yti.midgley.auto.data.obd.Obd2BleManager
import net.n2yti.midgley.auto.data.obd.Obd2Telemetry
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager
import net.n2yti.midgley.auto.data.repository.MidgleyRepository
import net.n2yti.midgley.auto.data.repository.Resource

/**
 * Primary In-Dash Automotive CarScreen displaying smart fill-up recommendations,
 * live OBD2 fuel tank telemetry, price projections, and navigation to detailed forecast & hub switcher.
 */
class MainCarScreen(
    carContext: CarContext,
    private val repository: MidgleyRepository = MidgleyRepository(),
    private val preferenceManager: MetroPreferenceManager = MetroPreferenceManager(carContext),
    private val obd2Manager: Obd2BleManager = Obd2BleManager(carContext)
) : Screen(carContext) {

    private var advisorData: SavingsAdvisorResponse? = null
    private var telemetryData: Obd2Telemetry? = null
    private var isLoading: Boolean = true
    private var errorMessage: String? = null
    private var fetchJob: Job? = null

    init {
        initObd2Telemetry()
        loadAdvisor()
    }

    private fun initObd2Telemetry() {
        val tankCapacity = preferenceManager.getTankCapacityGallons()
        if (preferenceManager.isObd2Enabled()) {
            if (preferenceManager.isSimulatedObd2()) {
                val simulatedPct = preferenceManager.getLastKnownFuelLevelPct()
                obd2Manager.injectSimulatedTelemetry(simulatedPct, tankCapacity)
            } else {
                obd2Manager.startScan(tankCapacity)
            }
        }

        obd2Manager.latestTelemetry
            .onEach { telemetry ->
                telemetryData = telemetry
                if (telemetry != null) {
                    preferenceManager.setLastKnownFuelLevelPct(telemetry.fuelLevelPercent)
                    loadAdvisor(telemetry.fuelLevelPercent)
                }
            }
            .launchIn(lifecycleScope)
    }

    fun loadAdvisor(dynamicFuelLevelPct: Double? = telemetryData?.fuelLevelPercent) {
        fetchJob?.cancel()
        val locale = preferenceManager.getSelectedLocale()
        val tankCapacity = preferenceManager.getTankCapacityGallons()

        fetchJob = repository.getUnifiedAdvisor(
            locale = locale,
            tankCapacity = tankCapacity,
            fuelLevelPct = dynamicFuelLevelPct
        )
            .onEach { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        isLoading = true
                        resource.data?.let { advisorData = it }
                        invalidate()
                    }
                    is Resource.Success -> {
                        isLoading = false
                        advisorData = resource.data
                        errorMessage = null
                        invalidate()
                    }
                    is Resource.Error -> {
                        isLoading = false
                        errorMessage = resource.message
                        advisorData = resource.data
                        invalidate()
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun onGetTemplate(): Template {
        val locale = preferenceManager.getSelectedLocale()
        val localeName = preferenceManager.getDisplayName(locale)
        val isAuto = preferenceManager.isAutoDetect()
        val modeTag = if (isAuto) "Auto-Detect (GPS)" else "Manual Lock"

        val data = advisorData
        val displaySignal = data?.displaySignal ?: "🟡 ANALYZING MARKET CONDITIONS..."
        val currentPrice = data?.currentPriceGal?.let { "$%.2f/gal".format(it) } ?: "$3.89/gal"
        val savingsText = if ((data?.savingsPerGal ?: 0.0) > 0.0) {
            " • Save $%.2f/gal (~$%.2f/fill)".format(data?.savingsPerGal, data?.netTankSavingsUsd)
        } else ""

        val optimalTiming = data?.optimalDate ?: "Optimal Timing: Day 3"
        val cacheTag = if (data?.isCached == true) " • (Cached)" else ""

        val rowSignal = Row.Builder()
            .setTitle(displaySignal)
            .addText("Current: $currentPrice$savingsText")
            .addText(optimalTiming)
            .build()

        val paneBuilder = Pane.Builder().addRow(rowSignal)

        // Telemetry Row (if active)
        val telemetry = telemetryData
        if (telemetry != null) {
            val tankCap = preferenceManager.getTankCapacityGallons()
            val telemetryRow = if (telemetry.isLowFuel) {
                Row.Builder()
                    .setTitle("⚠️ LOW FUEL WARNING: %.0f%%".format(telemetry.fuelLevelPercent))
                    .addText("Reserve alert: %.1f gal remaining (Needed: %.1f gal)".format(telemetry.fuelLevelGallons, telemetry.gallonsNeeded))
                    .build()
            } else {
                Row.Builder()
                    .setTitle("⛽ Fuel Tank: %.0f%% (%.1f / %.1f gal)".format(telemetry.fuelLevelPercent, telemetry.fuelLevelGallons, tankCap))
                    .addText("Shortfall to full: %.1f gal • OBD2 Live".format(telemetry.gallonsNeeded))
                    .build()
            }
            paneBuilder.addRow(telemetryRow)
        }

        val rowLocation = Row.Builder()
            .setTitle("Active Hub: $localeName")
            .addText("Mode: $modeTag$cacheTag")
            .build()

        paneBuilder.addRow(rowLocation)

        // Pane actions (max 2 per Android Auto constraints)
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("5D Trend")
                .setOnClickListener {
                    screenManager.push(
                        ForecastDetailScreen(
                            carContext = carContext,
                            localeId = locale,
                            localeName = localeName,
                            repository = repository
                        )
                    )
                }
                .build()
        )
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("Switch Hub")
                .setOnClickListener {
                    screenManager.push(
                        MetroSelectorScreen(
                            carContext = carContext,
                            preferenceManager = preferenceManager,
                            onSelectionChanged = { loadAdvisor() }
                        )
                    )
                }
                .build()
        )

        val header = Header.Builder()
            .setTitle("Midgley Fuel Advisor")
            .setStartHeaderAction(Action.APP_ICON)
            .addEndHeaderAction(
                Action.Builder()
                    .setTitle("Refresh")
                    .setOnClickListener {
                        if (preferenceManager.isObd2Enabled() && !preferenceManager.isSimulatedObd2()) {
                            obd2Manager.queryFuelLevel()
                        }
                        loadAdvisor()
                    }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeader(header)
            .build()
    }
}