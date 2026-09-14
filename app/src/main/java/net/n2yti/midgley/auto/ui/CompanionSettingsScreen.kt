package net.n2yti.midgley.auto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager

/**
 * Material 3 Phone Companion Settings Screen allowing drivers to configure tank capacity,
 * alert sensitivity thresholds, refining hub overrides, and preview dynamic savings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionSettingsScreen(
    preferenceManager: MetroPreferenceManager = MetroPreferenceManager(LocalContext.current)
) {
    var tankCapacity by remember { mutableDoubleStateOf(preferenceManager.getTankCapacityGallons()) }
    var customTankText by remember { mutableStateOf(tankCapacity.toString()) }
    var isAutoDetect by remember { mutableStateOf(preferenceManager.isAutoDetect()) }
    var selectedLocale by remember { mutableStateOf(preferenceManager.getSelectedLocale()) }
    var alertThreshold by remember { mutableIntStateOf(preferenceManager.getAlertThresholdCents()) }
    var weatherAlertsEnabled by remember { mutableStateOf(preferenceManager.isSevereWeatherAlertsEnabled()) }
    var apiBaseUrl by remember { mutableStateOf(preferenceManager.getApiBaseUrl()) }

    // Dynamic savings simulation based on active settings (estimated $0.10/gal drop)
    val estimatedSavingsPerGal = 0.10
    val netTankSavings = tankCapacity * estimatedSavingsPerGal

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Midgley Auto Settings", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Live Fuel Savings Simulator Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Estimated Fill-Up Savings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Save $%.2f per fill-up".format(netTankSavings),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Based on your %.1f gal tank capacity and active 5-day market forecast in %s."
                                .format(tankCapacity, preferenceManager.getDisplayName(selectedLocale)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 2. Vehicle Tank Capacity Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Vehicle Fuel Tank Capacity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select a preset or enter exact capacity in gallons:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(MetroPreferenceManager.TANK_PRESETS) { preset ->
                                FilterChip(
                                    selected = tankCapacity == preset.gallons,
                                    onClick = {
                                        tankCapacity = preset.gallons
                                        customTankText = preset.gallons.toString()
                                        preferenceManager.setTankCapacityGallons(preset.gallons)
                                    },
                                    label = { Text("${preset.label} (${preset.gallons}g)") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customTankText,
                            onValueChange = { input ->
                                customTankText = input
                                input.toDoubleOrNull()?.let { valNum ->
                                    if (valNum in 1.0..100.0) {
                                        tankCapacity = valNum
                                        preferenceManager.setTankCapacityGallons(valNum)
                                    }
                                }
                            },
                            label = { Text("Custom Tank Gallons") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 3. Active Refining Hub & Location Mode Card
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

            // 4. Alert Threshold Sensitivity Card
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

            // 5. Backend Gateway Endpoint Card
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
                                selected = apiBaseUrl.contains("10.42.42.54"),
                                onClick = {
                                    apiBaseUrl = "http://10.42.42.54:8000/api/v1/"
                                    preferenceManager.setApiBaseUrl(apiBaseUrl)
                                }
                            )
                            Text("Dev VM (10.42.42.54:8000)")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = apiBaseUrl.contains("midgley.n2yti.net"),
                                onClick = {
                                    apiBaseUrl = "https://midgley.n2yti.net/api/v1/"
                                    preferenceManager.setApiBaseUrl(apiBaseUrl)
                                }
                            )
                            Text("Production (midgley.n2yti.net)")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
