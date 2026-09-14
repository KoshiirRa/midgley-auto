package net.n2yti.midgley.auto.voice

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceIntentHandlerTest {

    @Test
    fun testParseIntent_deepLinkAdvisorWithLocale() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("midgley://advisor?locale=tulsa"))
        val action = VoiceIntentHandler.parseIntent(intent)

        assertThat(action).isInstanceOf(VoiceIntentHandler.VoiceAction.OpenAdvisor::class.java)
        val advisorAction = action as VoiceIntentHandler.VoiceAction.OpenAdvisor
        assertThat(advisorAction.targetLocale).isEqualTo("tulsa")
    }

    @Test
    fun testParseIntent_deepLinkForecastWithCityName() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("midgley://forecast?city=San%20Francisco"))
        val action = VoiceIntentHandler.parseIntent(intent)

        assertThat(action).isInstanceOf(VoiceIntentHandler.VoiceAction.OpenForecast::class.java)
        val forecastAction = action as VoiceIntentHandler.VoiceAction.OpenForecast
        assertThat(forecastAction.targetLocale).isEqualTo("oakland")
    }

    @Test
    fun testParseIntent_actionIntentWithExtra() {
        val intent = Intent(VoiceIntentHandler.ACTION_GET_ADVISOR).apply {
            putExtra("locale", "Newark")
        }
        val action = VoiceIntentHandler.parseIntent(intent)

        assertThat(action).isInstanceOf(VoiceIntentHandler.VoiceAction.OpenAdvisor::class.java)
        val advisorAction = action as VoiceIntentHandler.VoiceAction.OpenAdvisor
        assertThat(advisorAction.targetLocale).isEqualTo("newark")
    }

    @Test
    fun testParseIntent_nullIntent_returnsNone() {
        val action = VoiceIntentHandler.parseIntent(null)
        assertThat(action).isEqualTo(VoiceIntentHandler.VoiceAction.None)
    }
}
