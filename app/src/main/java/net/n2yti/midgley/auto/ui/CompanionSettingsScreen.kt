package net.n2yti.midgley.auto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.n2yti.midgley.auto.data.models.RecommendationCode
import net.n2yti.midgley.auto.data.models.SavingsAdvisorResponse
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager
import net.n2yti.midgley.auto.data.repository.MidgleyRepository
import net.n2yti.midgley.auto.data.repository.Resource

/**
 * Jetpack Compose Phone Companion App Settings & Live Telemetry Dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionSettingsScreen(
    preferenceManager: MetroPreferenceManager,
    repository: MidgleyRepository = remember { MidgleyRepository(preferenceManager = preferenceManager) },
    modifier: Modifier = Modifier
) {
    var selectedLocale by remember { mutableStateOf(preferenceManager.getSelectedLocale()) }
    var isAutoDetect by remember { mutableStateOf(preferenceManager.isAutoDetect()) }
    var tankCapacity by remember { mutableDoubleStateOf(preferenceManager.getTankCapacityGallons()) }
    var customTankText by remember { mutableStateOf("") }
    var alertThreshold by remember { mutableIntStateOf(preferenceManager.getAlertThresholdCents()) }
    var weatherAlertsEnabled by remember { mutableStateOf(preferenceManager.isSevereWeatherAlertsEnabled()) }
    var apiBaseUrl by remember { mutableStateOf(preferenceManager.getApiBaseUrl()) }

    var obd2Enabled by remember { mutableStateOf(preferenceManager.isObd2Enabled()) }
    var simulatedObd2 by remember { mutableStateOf(preferenceManager.isSimulatedObd2()) }
    var fuelLevelPct by remember { mutableDoubleStateOf(preferenceManager.getLastKnownFuelLevelPct()) }

    var advisorState by remember { mutableStateOf<Resource<SavingsAdvisorResponse>>(Resource.Loading()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val remainingGallons = (fuelLevelPct / 100.0) * tankCapacity
    val gallonsNeeded = (tankCapacity - remainingGallons).coerceAtLeast(0.0)

    // Dynamic Live Price & Advisor Fetch on region / tank / fuel change
    LaunchedEffect(selectedLocale, tankCapacity, fuelLevelPct, obd2Enabled, apiBaseUrl, refreshTrigger) {
        val effectiveFuelPct = if (obd2Enabled) fuelLevelPct else null
        repository.getUnifiedAdvisor(
            locale = selectedLocale,
            tankCapacity = tankCapacity,
            fuelLevelPct = effectiveFuelPct
        ).collect { resource ->
            advisorState = resource
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Midgley Gas Advisor") },
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Text("🔄", style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Regional Gas Price & Advisor Card
            item {
                val advisorData = advisorState.data
                val isDataLoading = advisorState is Resource.Loading && advisorData == null
                val displayName = preferenceManager.getDisplayName(selectedLocale)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (advisorData?.recommendationCode) {
                            RecommendationCode.WAIT_TO_FILL -> MaterialTheme.colorScheme.primaryContainer
                            RecommendationCode.FILL_NOW -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "📍 $displayName",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isAutoDetect) "Mode: Auto-Detect (GPS)" else "Mode: Manual Lock",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isDataLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (advisorData != null) {
                            Text(
                                text = advisorData.displaySignal,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Current Price: $%.2f/gal • Target: $%.2f/gal".format(
                                    advisorData.currentPriceGal,
                                    advisorData.targetPriceGal
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = advisorData.optimalDate,
                                style = MaterialTheme.typography.bodySmall
                            )

                            if (advisorData.savingsPerGal > 0.0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Estimated Net Savings: $%.2f (~$%.2f/gal on %.1f gal shortfall)".format(
                                        advisorData.netTankSavingsUsd,
                                        advisorData.savingsPerGal,
                                        gallonsNeeded
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Text("Fetching live regional prices from Midgley Gateway...")
                        }
                    }
                }
            }

            // 2. OBD2 Live Fuel Telemetry Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🚗 Passive OBD2 Telemetry",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Reads PID 012F (Fuel Level %) via Bluetooth dongle",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = obd2Enabled,
                                onCheckedChange = {
                                    obd2Enabled = it
                                    preferenceManager.setObd2Enabled(it)
                                }
                            )
                        }

                        if (obd2Enabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Simulate OBD2 (Demo / Testing)")
                                Switch(
                                    checked = simulatedObd2,
                                    onCheckedChange = {
                                        simulatedObd2 = it
                                        preferenceManager.setSimulatedObd2(it)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Current Fuel Level: %.0f%% (%.1f / %.1f gal)".format(
                                    fuelLevelPct,
                                    remainingGallons,
                                    tankCapacity
                                ),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (fuelLevelPct / 100.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (fuelLevelPct < 15.0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⚠️ Reserve Warning: Fuel level below 15%!",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (simulatedObd2) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Adjust Simulated Tank Level:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = fuelLevelPct.toFloat(),
                                    onValueChange = {
                                        fuelLevelPct = it.toDouble()
                                        preferenceManager.setLastKnownFuelLevelPct(it.toDouble())
                                    },
                                    valueRange = 0f..100f
                                )
                            }
                        }
                    }
                }
            }

            // 3. Vehicle Fuel Tank Capacity Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Vehicle Tank Capacity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Current capacity: %.1f Gallons".format(tankCapacity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Presets
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (preset in MetroPreferenceManager.TANK_PRESETS) {
                                FilterChip(
                                    selected = tankCapacity == preset.gallons,
                                    onClick = {
                                        tankCapacity = preset.gallons
                                        customTankText = ""
                                        preferenceManager.setTankCapacityGallons(preset.gallons)
                                    },
                                    label = { Text("${preset.gallons.toInt()}g") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customTankText,
                            onValueChange = {
                                customTankText = it
                                val parsed = it.toDoubleOrNull()
                                if (parsed != null && parsed in 5.0..100.0) {
                                    tankCapacity = parsed
                                    preferenceManager.setTankCapacityGallons(parsed)
                                }
                            },
                            label = { Text("Custom Tank Gallons") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 4. Active Refining Hub & Location Mode Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Regional Refining Hub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Choose dynamic GPS tracking or lock to a specific refining hub:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Auto-Detect Option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = isAutoDetect,
                                onClick = {
                                    isAutoDetect = true
                                    preferenceManager.setAutoDetect(true)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("📡 Auto-Detect (Dynamic GPS)", fontWeight = FontWeight.SemiBold)
                                Text("Switches hubs as you drive across PADD boundaries", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // Manual Hub Options
                        for (hub in MetroPreferenceManager.SUPPORTED_METROS) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = !isAutoDetect && selectedLocale.equals(hub.id, ignoreCase = true),
                                    onClick = {
                                        isAutoDetect = false
                                        selectedLocale = hub.id
                                        preferenceManager.setSelectedLocale(hub.id)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(hub.name, fontWeight = FontWeight.SemiBold)
                                    Text(hub.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Alert Threshold Sensitivity Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Price Alert Threshold",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Alert when price drop exceeds $alertThreshold¢/gal:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = alertThreshold.toFloat(),
                            onValueChange = {
                                alertThreshold = it.toInt()
                                preferenceManager.setAlertThresholdCents(it.toInt())
                            },
                            valueRange = 2f..15f,
                            steps = 12
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Severe Weather & Outage Alerts", fontWeight = FontWeight.SemiBold)
                                Text("Alerts on refinery trips, tornadoes & hurricane tracks", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = weatherAlertsEnabled,
                                onCheckedChange = {
                                    weatherAlertsEnabled = it
                                    preferenceManager.setSevereWeatherAlertsEnabled(it)
                                }
                            )
                        }
                    }
                }
            }

            // 6. Backend Gateway Endpoint Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Backend Gateway Endpoint",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = apiBaseUrl.contains("local-dev.dwarvenbard.com"),
                                onClick = {
                                    apiBaseUrl = "https://local-dev.dwarvenbard.com/api/v1/"
                                    preferenceManager.setApiBaseUrl(apiBaseUrl)
                                }
                            )
                            Text("Production (local-dev.dwarvenbard.com)")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = apiBaseUrl.contains("10.42.42.54"),
                                onClick = {
                                    apiBaseUrl = "http://10.42.42.54:8000/api/v1/"
                                    preferenceManager.setApiBaseUrl(apiBaseUrl)
                                }
                            )
                            Text("Dev VM (10.42.42.54:8000)")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
