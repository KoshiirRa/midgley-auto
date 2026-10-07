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
    fun testOnGetTemplate_returnsListTemplateWithAutoDetectAndRegionalGroupings() {
        screenController.moveToState(Lifecycle.State.RESUMED)

        val template = screen.onGetTemplate()
        assertThat(template).isInstanceOf(ListTemplate::class.java)

        val listTemplate = template as ListTemplate
        val list = listTemplate.singleList
        assertThat(list).isNotNull()
        assertThat(list?.items).hasSize(4)

        // First item is Auto-Detect
        val firstItem = list?.items?.get(0) as? Row
        assertThat(firstItem).isNotNull()
        assertThat(firstItem?.title?.toString()).contains("Auto-Detect")

        // Subsequent items are regional groups
        val atlanticItem = list?.items?.get(1) as? Row
        assertThat(atlanticItem?.title?.toString()).contains("Atlantic & Southeast")

        val midwestItem = list?.items?.get(2) as? Row
        assertThat(midwestItem?.title?.toString()).contains("Midwest & Central")

        val westItem = list?.items?.get(3) as? Row
        assertThat(westItem?.title?.toString()).contains("West Coast & National")
    }

    @Test
    fun testSubMetroSelectorScreen_rendersAllHubsInGroup() {
        val subScreen = SubMetroSelectorScreen(
            carContext = carContext,
            groupTitle = "Atlantic & Southeast",
            hubIds = MetroSelectorScreen.ATLANTIC_HUBS,
            preferenceManager = preferenceManager,
            onSelectionChanged = {}
        )
        val subController = ScreenController(subScreen)
        subController.moveToState(Lifecycle.State.RESUMED)

        val template = subScreen.onGetTemplate() as ListTemplate
        val items = template.singleList?.items
        assertThat(items).isNotNull()
        assertThat(items).hasSize(4) // Newark, Greenville, Charlotte, Port St. Lucie

        val titles = items?.mapNotNull { (it as? Row)?.title?.toString() } ?: emptyList()
        assertThat(titles.any { it.contains("Newark") }).isTrue()
        assertThat(titles.any { it.contains("Greenville") }).isTrue()
        assertThat(titles.any { it.contains("Charlotte") }).isTrue()
        assertThat(titles.any { it.contains("Port St. Lucie") }).isTrue()
    }
}
