package net.n2yti.midgley.auto.ui

import androidx.car.app.model.PaneTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainCarScreenTest {

    private lateinit var carContext: TestCarContext
    private lateinit var preferenceManager: MetroPreferenceManager
    private lateinit var screen: MainCarScreen
    private lateinit var screenController: ScreenController

    @Before
    fun setUp() {
        carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        preferenceManager = MetroPreferenceManager(carContext)
        screen = MainCarScreen(carContext, preferenceManager = preferenceManager)
        screenController = ScreenController(screen)
    }

    @Test
    fun testOnGetTemplate_returnsPaneTemplateWithAdvisorSignal() {
        screenController.moveToState(Lifecycle.State.RESUMED)

        val template = screen.onGetTemplate()
        assertThat(template).isInstanceOf(PaneTemplate::class.java)

        val paneTemplate = template as PaneTemplate
        val pane = paneTemplate.pane
        assertThat(pane).isNotNull()
        assertThat(pane?.rows?.size).isAtLeast(2)
    }

    @Test
    fun testOnGetTemplate_hasPaneAndHeaderActions() {
        screenController.moveToState(Lifecycle.State.RESUMED)

        val template = screen.onGetTemplate() as PaneTemplate
        val paneActions = template.pane?.actions
        assertThat(paneActions).isNotNull()
        assertThat(paneActions).hasSize(2)
        assertThat(paneActions?.get(0)?.title?.toString()).isEqualTo("5D Trend")
        assertThat(paneActions?.get(1)?.title?.toString()).isEqualTo("Switch Hub")

        val header = template.header
        assertThat(header).isNotNull()
        assertThat(header?.endHeaderActions).isNotEmpty()
        assertThat(header?.endHeaderActions?.get(0)?.title?.toString()).isEqualTo("Refresh")
    }
}
