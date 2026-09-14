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
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager
import net.n2yti.midgley.auto.data.repository.MidgleyRepository
import net.n2yti.midgley.auto.data.repository.Resource

/**
 * Primary In-Dash Automotive CarScreen displaying smart fill-up recommendations,
 * price projections, and navigation to detailed forecast & hub switcher.
 */
class MainCarScreen(
    carContext: CarContext,
    private val repository: MidgleyRepository = MidgleyRepository(),
    private val preferenceManager: MetroPreferenceManager = MetroPreferenceManager(carContext)
) : Screen(carContext) {

    private var advisorData: SavingsAdvisorResponse? = null
    private var isLoading: Boolean = true
    private var errorMessage: String? = null
    private var fetchJob: Job? = null

    init {
        loadAdvisor()
    }

    fun loadAdvisor() {
        fetchJob?.cancel()
        val locale = preferenceManager.getSelectedLocale()
        fetchJob = repository.getUnifiedAdvisor(locale = locale)
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
            " • Save $%.2f/gal (~$%.2f/tank)".format(data?.savingsPerGal, data?.netTankSavingsUsd)
        } else ""

        val optimalTiming = data?.optimalDate ?: "Optimal Timing: Day 3"
        val cacheTag = if (data?.isCached == true) " • (Cached)" else ""

        val rowSignal = Row.Builder()
            .setTitle(displaySignal)
            .addText("Current: $currentPrice$savingsText")
            .addText(optimalTiming)
            .build()

        val rowLocation = Row.Builder()
            .setTitle("Active Hub: $localeName")
            .addText("Mode: $modeTag$cacheTag")
            .build()

        // Pane actions (max 2 per Android Auto constraints)
        val paneBuilder = Pane.Builder()
            .addRow(rowSignal)
            .addRow(rowLocation)
            .addAction(
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
            .addAction(
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
