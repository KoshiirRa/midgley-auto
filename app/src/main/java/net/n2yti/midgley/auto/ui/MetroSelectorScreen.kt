package net.n2yti.midgley.auto.ui

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager

/**
 * Screen allowing drivers to switch active refining hubs via regional groupings
 * or re-enable GPS auto-detection without exceeding Car App Library template constraints (Issue #18).
 */
class MetroSelectorScreen(
    carContext: CarContext,
    private val preferenceManager: MetroPreferenceManager = MetroPreferenceManager(carContext),
    private val onSelectionChanged: () -> Unit = {}
) : Screen(carContext) {

    companion object {
        val ATLANTIC_HUBS = listOf("newark", "greenville", "charlotte", "port_st_lucie")
        val MIDWEST_HUBS = listOf("tulsa", "cincinnati")
        val WEST_NATIONAL_HUBS = listOf("oakland", "national")
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        val isAuto = preferenceManager.isAutoDetect()
        val currentLocale = preferenceManager.getSelectedLocale()

        // 1. Auto-Detect Entry
        val autoRow = Row.Builder()
            .setTitle(if (isAuto) "✓ 📡 Auto-Detect (Active)" else "📡 Auto-Detect (Current Location)")
            .addText("Dynamically switches hubs based on vehicle GPS")
            .setOnClickListener {
                preferenceManager.setAutoDetect(true)
                onSelectionChanged()
                screenManager.pop()
            }
            .build()
        listBuilder.addItem(autoRow)

        // 2. Regional Groupings (Respects Android Auto list template constraints)
        val atlanticActive = !isAuto && ATLANTIC_HUBS.contains(currentLocale.lowercase())
        val midwestActive = !isAuto && MIDWEST_HUBS.contains(currentLocale.lowercase())
        val westActive = !isAuto && (WEST_NATIONAL_HUBS.contains(currentLocale.lowercase()) || currentLocale.equals("bay_area", ignoreCase = true))

        // Atlantic & Southeast Hubs
        val atlanticRow = Row.Builder()
            .setTitle(if (atlanticActive) "✓ Atlantic & Southeast (${preferenceManager.getDisplayName(currentLocale)})" else "Atlantic & Southeast Hubs")
            .addText("Newark DE, Greenville NC, Charlotte NC, Port St. Lucie FL")
            .setOnClickListener {
                screenManager.push(
                    SubMetroSelectorScreen(
                        carContext = carContext,
                        groupTitle = "Atlantic & Southeast",
                        hubIds = ATLANTIC_HUBS,
                        preferenceManager = preferenceManager,
                        onSelectionChanged = onSelectionChanged
                    )
                )
            }
            .build()
        listBuilder.addItem(atlanticRow)

        // Midwest & Central Hubs
        val midwestRow = Row.Builder()
            .setTitle(if (midwestActive) "✓ Midwest & Central (${preferenceManager.getDisplayName(currentLocale)})" else "Midwest & Central Hubs")
            .addText("Tulsa OK (PADD 2 WTI), Cincinnati OH/KY (Ohio River)")
            .setOnClickListener {
                screenManager.push(
                    SubMetroSelectorScreen(
                        carContext = carContext,
                        groupTitle = "Midwest & Central",
                        hubIds = MIDWEST_HUBS,
                        preferenceManager = preferenceManager,
                        onSelectionChanged = onSelectionChanged
                    )
                )
            }
            .build()
        listBuilder.addItem(midwestRow)

        // West Coast & National Hubs
        val westRow = Row.Builder()
            .setTitle(if (westActive) "✓ West Coast & National (${preferenceManager.getDisplayName(currentLocale)})" else "West Coast & National Baseline")
            .addText("SF Bay Area & Oakland (CARB), US National Baseline")
            .setOnClickListener {
                screenManager.push(
                    SubMetroSelectorScreen(
                        carContext = carContext,
                        groupTitle = "West Coast & National",
                        hubIds = WEST_NATIONAL_HUBS,
                        preferenceManager = preferenceManager,
                        onSelectionChanged = onSelectionChanged
                    )
                )
            }
            .build()
        listBuilder.addItem(westRow)

        val header = Header.Builder()
            .setTitle("Select Refining Hub")
            .setStartHeaderAction(Action.BACK)
            .build()

        return ListTemplate.Builder()
            .setHeader(header)
            .setSingleList(listBuilder.build())
            .build()
    }
}

/**
 * Sub-screen rendering the individual hubs within a regional grouping.
 */
class SubMetroSelectorScreen(
    carContext: CarContext,
    private val groupTitle: String,
    private val hubIds: List<String>,
    private val preferenceManager: MetroPreferenceManager,
    private val onSelectionChanged: () -> Unit
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        val isAuto = preferenceManager.isAutoDetect()
        val currentLocale = preferenceManager.getSelectedLocale()

        val matchingHubs = MetroPreferenceManager.SUPPORTED_METROS.filter { hubIds.contains(it.id) }
        for (hub in matchingHubs) {
            val isSelected = !isAuto && (currentLocale.equals(hub.id, ignoreCase = true) ||
                    (hub.id == "oakland" && currentLocale.equals("bay_area", ignoreCase = true)))
            val titlePrefix = if (isSelected) "✓ " else ""

            val row = Row.Builder()
                .setTitle("$titlePrefix${hub.name}")
                .addText(hub.description)
                .setOnClickListener {
                    preferenceManager.setSelectedLocale(hub.id)
                    onSelectionChanged()
                    screenManager.popToRoot()
                }
                .build()
            listBuilder.addItem(row)
        }

        val header = Header.Builder()
            .setTitle(groupTitle)
            .setStartHeaderAction(Action.BACK)
            .build()

        return ListTemplate.Builder()
            .setHeader(header)
            .setSingleList(listBuilder.build())
            .build()
    }
}
