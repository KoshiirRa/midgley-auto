package net.n2yti.midgley.auto.service

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarAppManifestRegistrationTest {

    @Test
    fun testAutomotiveMetadata_registeredInManifest() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )

        // Verify Android Auto head unit descriptor metadata is present
        assertThat(appInfo.metaData).isNotNull()
        val automotiveResId = appInfo.metaData.getInt("com.google.android.gms.car.application")
        assertThat(automotiveResId).isGreaterThan(0)

        // Verify automotive_app_desc.xml resource XML content
        val parser = context.resources.getXml(automotiveResId)
        var eventType = parser.eventType
        var hasAutomotiveAppTag = false
        var usesTemplate = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.name == "automotiveApp") {
                    hasAutomotiveAppTag = true
                }
                if (parser.name == "uses") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "name" && parser.getAttributeValue(i) == "template") {
                            usesTemplate = true
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        assertThat(hasAutomotiveAppTag).isTrue()
        assertThat(usesTemplate).isTrue()
    }

    @Test
    fun testCarAppService_declaredWithPoiCategory() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageManager = context.packageManager
        val componentName = ComponentName(context, MidgleyCarAppService::class.java)

        val serviceInfo = packageManager.getServiceInfo(componentName, PackageManager.GET_META_DATA)
        assertThat(serviceInfo).isNotNull()
        assertThat(serviceInfo.exported).isTrue()

        // Verify minCarApiLevel metadata
        val appInfo = packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        val minCarApiLevel = appInfo.metaData.getInt("androidx.car.app.minCarApiLevel")
        assertThat(minCarApiLevel).isAtLeast(1)
    }
}
