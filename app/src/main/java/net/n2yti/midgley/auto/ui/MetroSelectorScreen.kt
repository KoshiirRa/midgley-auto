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
 * Screen allowing drivers to switch active refining hubs or re-enable GPS auto-detection.
 */
class MetroSelectorScreen(
    carContext: CarContext,
    private val preferenceManager: MetroPreferenceManager = MetroPreferenceManager(carContext),
    private val onSelectionChanged: () -> Unit = {}
) : Screen(carContext) {

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

        // 2. Supported Metro Hub Entries (Respects 6-item max limit for safety)
        val selectedMetros = MetroPreferenceManager.SUPPORTED_METROS.take(5)
        for (hub in selectedMetros) {
            val isSelected = !isAuto && currentLocale.equals(hub.id, ignoreCase = true)
            val titlePrefix = if (isSelected) "✓ " else ""
            val row = Row.Builder()
                .setTitle("$titlePrefix${hub.name}")
                .addText(hub.description)
                .setOnClickListener {
                    preferenceManager.setSelectedLocale(hub.id)
                    onSelectionChanged()
                    screenManager.pop()
                }
                .build()
            listBuilder.addItem(row)
        }

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
