package net.n2yti.midgley.auto.ui

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.n2yti.midgley.auto.data.models.ForecastResponse
import net.n2yti.midgley.auto.data.repository.MidgleyRepository
import net.n2yti.midgley.auto.data.repository.Resource

/**
 * Screen displaying the 5-day predictive trajectory with point-in-time calibrated intervals.
 */
class ForecastDetailScreen(
    carContext: CarContext,
    private val localeId: String,
    private val localeName: String,
    private val repository: MidgleyRepository = MidgleyRepository()
) : Screen(carContext), DefaultLifecycleObserver {

    private var forecastData: ForecastResponse? = null
    private var isLoading: Boolean = true
    private var errorMessage: String? = null
    private var fetchJob: Job? = null

    init {
        lifecycle.addObserver(this)
        loadForecast()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        fetchJob?.cancel()
        fetchJob = null
        super.onDestroy(owner)
    }

    private fun loadForecast() {
        fetchJob?.cancel()
        fetchJob = repository.get5DayForecast(localeId)
            .onEach { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        isLoading = true
                        resource.data?.let { forecastData = it }
                        invalidate()
                    }
                    is Resource.Success -> {
                        isLoading = false
                        forecastData = resource.data
                        errorMessage = null
                        invalidate()
                    }
                    is Resource.Error -> {
                        isLoading = false
                        errorMessage = resource.message
                        invalidate()
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        val data = forecastData

        if (data != null && data.forecast.isNotEmpty()) {
            // Display up to 6 forecast days (complying with Android Auto list limit)
            val points = data.forecast.take(6)
            for (point in points) {
                val dayLabel = if (point.day == 0) "Today (Baseline)" else "Day ${point.day} • ${point.date}"
                val priceText = if (point.p50 > 0.0) "$%.2f/gal".format(point.p50) else "Pending"
                val spreadText = if (point.p10 != null && point.p90 != null) {
                    " (90% CI: $%.2f - $%.2f)".format(point.p10, point.p90)
                } else ""

                val deltaText = if (point.day > 0 && data.basePrice > 0.0 && point.p50 > 0.0) {
                    val delta = point.p50 - data.basePrice
                    val sign = if (delta >= 0) "+" else ""
                    " | %s$%.2f vs today".format(sign, delta)
                } else ""

                val row = Row.Builder()
                    .setTitle("$dayLabel: $priceText")
                    .addText("${point.date}$spreadText$deltaText")
                    .build()

                listBuilder.addItem(row)
            }
        } else {
            val errorTitle = if (isLoading) {
                "Loading 5-Day Forecast..."
            } else {
                "Forecast Unavailable Offline"
            }
            val errorSubtitle = if (isLoading) {
                "Connecting to Midgley Multi-Agent Engine..."
            } else {
                errorMessage ?: "Connect to network to sync 5-day market trajectory"
            }
            val loadingRow = Row.Builder()
                .setTitle(errorTitle)
                .addText(errorSubtitle)
                .build()
            listBuilder.addItem(loadingRow)
        }

        val header = Header.Builder()
            .setTitle("$localeName 5D Forecast")
            .setStartHeaderAction(Action.BACK)
            .build()

        return ListTemplate.Builder()
            .setHeader(header)
            .setSingleList(listBuilder.build())
            .build()
    }
}
