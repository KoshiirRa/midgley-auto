package net.n2yti.midgley.auto.ui

import androidx.car.app.model.ListTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import net.n2yti.midgley.auto.data.repository.MidgleyRepository
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForecastDetailScreenTest {

    private lateinit var carContext: TestCarContext
    private lateinit var screen: ForecastDetailScreen
    private lateinit var screenController: ScreenController

    @Before
    fun setUp() {
        carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        screen = ForecastDetailScreen(carContext, localeId = "tulsa", localeName = "Tulsa Metro")
        screenController = ScreenController(screen)
    }

    @Test
    fun testOnGetTemplate_returnsListTemplateWithForecastRows() {
        screenController.moveToState(Lifecycle.State.RESUMED)

        val template = screen.onGetTemplate()
        assertThat(template).isInstanceOf(ListTemplate::class.java)

        val listTemplate = template as ListTemplate
        assertThat(listTemplate.header?.title?.toString()).contains("Tulsa Metro")
    }
}
