package net.n2yti.midgley.auto.ui

import androidx.car.app.model.PaneTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainCarScreenTest {

    private lateinit var carContext: TestCarContext
    private lateinit var screen: MainCarScreen
    private lateinit var screenController: ScreenController

    @Before
    fun setUp() {
        carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        screen = MainCarScreen(carContext)
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

        val firstRow = pane?.rows?.get(0)
        assertThat(firstRow?.title?.toString()).contains("WAIT TO FILL")
    }

    @Test
    fun testOnGetTemplate_hasRefreshAction() {
        val template = screen.onGetTemplate() as PaneTemplate
        val actions = template.pane?.actions
        assertThat(actions).isNotNull()
        assertThat(actions).isNotEmpty()
        assertThat(actions?.get(0)?.title?.toString()).isEqualTo("Refresh")
    }
}
