package net.n2yti.midgley.auto.ui

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

/**
 * Primary In-Dash Automotive CarScreen displaying smart fill-up recommendations.
 */
class MainCarScreen(carContext: CarContext) : Screen(carContext) {

    private var currentMetroHub: String = "Tulsa Metro Area (PADD 2)"
    private var advisorSignal: String = "🟢 WAIT TO FILL UP"
    private var projectedSavings: String = "Projected 5-day drop: -$0.10/gal (~$1.50/tank)"
    private var currentRetailPrice: String = "$3.89/gal"
    private var optimalDate: String = "Projected trough: Day 3 (Friday)"

    override fun onGetTemplate(): Template {
        val rowSignal = Row.Builder()
            .setTitle(advisorSignal)
            .addText("Current: $currentRetailPrice | $projectedSavings")
            .addText(optimalDate)
            .build()

        val rowLocation = Row.Builder()
            .setTitle("Active Refining Hub")
            .addText(currentMetroHub)
            .build()

        val pane = Pane.Builder()
            .addRow(rowSignal)
            .addRow(rowLocation)
            .addAction(
                Action.Builder()
                    .setTitle("Refresh")
                    .setOnClickListener {
                        invalidate()
                    }
                    .build()
            )
            .build()

        val header = Header.Builder()
            .setTitle("Midgley Fuel Advisor")
            .setStartHeaderAction(Action.APP_ICON)
            .build()

        return PaneTemplate.Builder(pane)
            .setHeader(header)
            .build()
    }
}
