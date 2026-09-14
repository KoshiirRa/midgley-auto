package net.n2yti.midgley.auto.ui

import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
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
class MetroSelectorScreenTest {

    private lateinit var carContext: TestCarContext
    private lateinit var preferenceManager: MetroPreferenceManager
    private lateinit var screen: MetroSelectorScreen
    private lateinit var screenController: ScreenController

    @Before
    fun setUp() {
        carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        preferenceManager = MetroPreferenceManager(carContext)
        screen = MetroSelectorScreen(carContext, preferenceManager)
        screenController = ScreenController(screen)
    }

    @Test
    fun testOnGetTemplate_returnsListTemplateWithAutoDetectAndMetros() {
        screenController.moveToState(Lifecycle.State.RESUMED)

        val template = screen.onGetTemplate()
        assertThat(template).isInstanceOf(ListTemplate::class.java)

        val listTemplate = template as ListTemplate
        val list = listTemplate.singleList
        assertThat(list).isNotNull()
        assertThat(list?.items).isNotEmpty()

        // First item is Auto-Detect
        val firstItem = list?.items?.get(0) as? Row
        assertThat(firstItem).isNotNull()
        assertThat(firstItem?.title?.toString()).contains("Auto-Detect")
    }
}
