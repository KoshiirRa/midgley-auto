package net.n2yti.midgley.auto.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.n2yti.midgley.auto.data.models.RecommendationCode
import net.n2yti.midgley.auto.data.models.SavingsAdvisorResponse
import net.n2yti.midgley.auto.data.obd.Obd2BleManager
import net.n2yti.midgley.auto.data.obd.Obd2ConnectionState
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager
import net.n2yti.midgley.auto.data.preferences.ThemeMode
import net.n2yti.midgley.auto.data.repository.MidgleyRepository
import net.n2yti.midgley.auto.data.repository.Resource

/**
 * Jetpack Compose Phone Companion App Settings & Live Telemetry Dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CompanionSettingsScreen(
    preferenceManager: MetroPreferenceManager,
    repository: MidgleyRepository = remember { MidgleyRepository(preferenceManager = preferenceManager) },
    obd2BleManager: Obd2BleManager? = null,
    currentThemeMode: ThemeMode = preferenceManager.getThemeMode(),
    onThemeModeChanged: ((ThemeMode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bleManager = remember(context) { obd2BleManager ?: Obd2BleManager(context) }
    val connectionState by bleManager.connectionState.collectAsState()
    val liveTelemetry by bleManager.latestTelemetry.collectAsState()

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

    var activeThemeMode by remember { mutableStateOf(currentThemeMode) }
    var hasPermissions by remember { mutableStateOf(bleManager.hasRequiredBluetoothPermissions()) }
    var pairedDevices by remember { mutableStateOf(bleManager.getPairedDevices()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (hasPermissions) {
            pairedDevices = bleManager.getPairedDevices()
        }
    }

    var advisorState by remember { mutableStateOf<Resource<SavingsAdvisorResponse>>(Resource.Loading()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Sync live OBD2 fuel telemetry when received from hardware or simulation
    LaunchedEffect(liveTelemetry) {
        liveTelemetry?.fuelLevelPercent?.let { pct ->
            fuelLevelPct = pct
            preferenceManager.setLastKnownFuelLevelPct(pct)
        }
    }

    val remainingGallons = (fuelLevelPct / 100.0) * tankCapacity
    val gallonsNeeded = (tankCapacity - remainingGallons).coerceAtLeast(0.0)

    // Dynamic Live Price & Advisor Fetch on region / tank / fuel / endpoint change
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
                title = { Text("Midgley Gas Advisor", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        refreshTrigger++
                        hasPermissions = bleManager.hasRequiredBluetoothPermissions()
                        if (hasPermissions) {
                            pairedDevices = bleManager.getPairedDevices()
                        }
                    }) {
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

                            if (advisorState is Resource.Error && advisorState.message != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "⚠️ Offline: ${advisorState.message}",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            Text("Fetching live regional prices from Midgley Gateway...")
                        }
                    }
                }
            }

            // 2. Theme / Appearance Card (Issue #9)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🎨 Appearance & Theme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Choose light, dark, or automatic system dark mode sync:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = activeThemeMode == ThemeMode.SYSTEM,
                                onClick = {
                                    activeThemeMode = ThemeMode.SYSTEM
                                    preferenceManager.setThemeMode(ThemeMode.SYSTEM)
                                    onThemeModeChanged?.invoke(ThemeMode.SYSTEM)
                                },
                                label = { Text("🖥️ System") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = activeThemeMode == ThemeMode.LIGHT,
                                onClick = {
                                    activeThemeMode = ThemeMode.LIGHT
                                    preferenceManager.setThemeMode(ThemeMode.LIGHT)
                                    onThemeModeChanged?.invoke(ThemeMode.LIGHT)
                                },
                                label = { Text("☀️ Light") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = activeThemeMode == ThemeMode.DARK,
                                onClick = {
                                    activeThemeMode = ThemeMode.DARK
                                    preferenceManager.setThemeMode(ThemeMode.DARK)
                                    onThemeModeChanged?.invoke(ThemeMode.DARK)
                                },
                                label = { Text("🌙 Dark") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. OBD2 Live Fuel Telemetry & Bluetooth Connection Card
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
                                    text = "🚗 Passive OBD-II Telemetry",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Dual Bluetooth (Classic SPP + BLE) Mode 01 PID 2F",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = obd2Enabled,
                                onCheckedChange = {
                                    obd2Enabled = it
                                    preferenceManager.setObd2Enabled(it)
                                    if (it) {
                                        if (!bleManager.hasRequiredBluetoothPermissions()) {
                                            permissionLauncher.launch(bleManager.getRequiredBluetoothPermissions())
                                        } else {
                                            hasPermissions = true
                                            pairedDevices = bleManager.getPairedDevices()
                                        }
                                    } else {
                                        bleManager.disconnect()
                                    }
                                }
                            )
                        }

                        if (obd2Enabled) {
                            Spacer(modifier = Modifier.height(12.dp))

                            // Permission Warning Banner if permissions not granted
                            if (!hasPermissions) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "🔑 Bluetooth Permissions Required",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "Bluetooth and location permissions are required to scan for and connect to OBD-II adapters.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                permissionLauncher.launch(bleManager.getRequiredBluetoothPermissions())
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.tertiary,
                                                contentColor = MaterialTheme.colorScheme.onTertiary
                                            )
                                        ) {
                                            Text("Grant Bluetooth Permissions", fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Live Connection Status Banner
                            Surface(
                                color = when (connectionState) {
                                    is Obd2ConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer
                                    is Obd2ConnectionState.Connecting, is Obd2ConnectionState.Scanning -> MaterialTheme.colorScheme.secondaryContainer
                                    is Obd2ConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val statusText = when (val state = connectionState) {
                                            is Obd2ConnectionState.Connected -> "🟢 Connected: ${state.deviceName} (${state.protocol})"
                                            is Obd2ConnectionState.Connecting -> "🟡 Connecting to ${state.deviceName}..."
                                            is Obd2ConnectionState.Scanning -> "🟡 Scanning for nearby BLE dongles..."
                                            is Obd2ConnectionState.Error -> "🔴 Error: ${state.message}"
                                            Obd2ConnectionState.Disconnected -> "⚪ Disconnected"
                                        }
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (connectionState is Obd2ConnectionState.Connected) {
                                            val conn = connectionState as Obd2ConnectionState.Connected
                                            Text(
                                                text = "MAC: ${conn.address}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (connectionState is Obd2ConnectionState.Connected ||
                                        connectionState is Obd2ConnectionState.Connecting ||
                                        connectionState is Obd2ConnectionState.Scanning) {
                                        OutlinedButton(
                                            onClick = { bleManager.disconnect() },
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text("Disconnect", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
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

                            Spacer(modifier = Modifier.height(16.dp))

                            // Paired Bluetooth Devices Section
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Paired Bluetooth Adapters",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = {
                                        if (!bleManager.hasRequiredBluetoothPermissions()) {
                                            permissionLauncher.launch(bleManager.getRequiredBluetoothPermissions())
                                        } else {
                                            hasPermissions = true
                                            pairedDevices = bleManager.getPairedDevices()
                                        }
                                    }
                                ) {
                                    Text("Refresh", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (pairedDevices.isEmpty()) {
                                Text(
                                    text = "No paired Bluetooth devices detected. Pair your OBD-II adapter in Android Settings first, then tap Refresh.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (device in pairedDevices) {
                                        val isConnected = (connectionState as? Obd2ConnectionState.Connected)?.address == device.address
                                        Surface(
                                            color = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(device.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                    Text("${device.address} • ${device.type}", style = MaterialTheme.typography.bodySmall)
                                                }
                                                if (isConnected) {
                                                    FilledTonalButton(
                                                        onClick = { bleManager.disconnect() }
                                                    ) {
                                                        Text("Active", fontSize = 12.sp)
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = {
                                                            if (!bleManager.hasRequiredBluetoothPermissions()) {
                                                                permissionLauncher.launch(bleManager.getRequiredBluetoothPermissions())
                                                            } else {
                                                                hasPermissions = true
                                                                preferenceManager.setLastObd2Address(device.address)
                                                                bleManager.connectToPairedDevice(device.address, tankCapacity)
                                                            }
                                                        }
                                                    ) {
                                                        Text("Connect", fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // BLE Scanning Section
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("BLE Scanning", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Scan for unbonded BLE 4.0/5.0 adapters", style = MaterialTheme.typography.bodySmall)
                                }
                                if (connectionState is Obd2ConnectionState.Scanning) {
                                    OutlinedButton(onClick = { bleManager.stopScan() }) {
                                        Text("Stop Scan", fontSize = 12.sp)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            if (!bleManager.hasRequiredBluetoothPermissions()) {
                                                permissionLauncher.launch(bleManager.getRequiredBluetoothPermissions())
                                            } else {
                                                hasPermissions = true
                                                bleManager.startScan(tankCapacity)
                                            }
                                        }
                                    ) {
                                        Text("Scan BLE", fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Simulation Toggle
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
                                        if (it) {
                                            bleManager.injectSimulatedTelemetry(fuelLevelPct, tankCapacity)
                                        }
                                    }
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
                                        bleManager.injectSimulatedTelemetry(it.toDouble(), tankCapacity)
                                    },
                                    valueRange = 0f..100f
                                )
                            }
                        }
                    }
                }
            }

            // 4. Vehicle Fuel Tank Capacity Card
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
                            text = "Select your vehicle's fuel tank size for net savings calculations:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Preset Chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (preset in MetroPreferenceManager.TANK_PRESETS) {
                                FilterChip(
                                    selected = (tankCapacity == preset.gallons),
                                    onClick = {
                                        tankCapacity = preset.gallons
                                        preferenceManager.setTankCapacityGallons(preset.gallons)
                                        customTankText = ""
                                    },
                                    label = { Text("${preset.label} (${preset.gallons}g)") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Custom Capacity Field
                        OutlinedTextField(
                            value = customTankText,
                            onValueChange = { input ->
                                customTankText = input
                                input.toDoubleOrNull()?.let { validGallons ->
                                    if (validGallons in 5.0..60.0) {
                                        tankCapacity = validGallons
                                        preferenceManager.setTankCapacityGallons(validGallons)
                                    }
                                }
                            },
                            label = { Text("Custom Tank (Gallons)") },
                            placeholder = { Text("%.1f".format(tankCapacity)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // 5. Regional Refining Hub Selector Card
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

            // 6. Alert Threshold Sensitivity Card
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

            // 7. Backend Gateway Endpoint Card
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
                                selected = apiBaseUrl.contains("koshiirra.github.io"),
                                onClick = {
                                    apiBaseUrl = "https://koshiirra.github.io/midgley/"
                                    preferenceManager.setApiBaseUrl(apiBaseUrl)
                                }
                            )
                            Text("Production (GitHub Pages)")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = apiBaseUrl.contains("local-dev.dwarvenbard.com"),
                                onClick = {
                                    apiBaseUrl = "https://local-dev.dwarvenbard.com/api/v1/"
                                    preferenceManager.setApiBaseUrl(apiBaseUrl)
                                }
                            )
                            Text("Dwarvenbard Cloud Gateway")
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
