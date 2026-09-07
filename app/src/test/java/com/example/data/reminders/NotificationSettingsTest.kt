package com.example.data.reminders

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationSettingsTest {
    @Test fun modernDevicesOpenNotificationSettingsForThisApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = NotificationHelper.settingsIntent(context)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    @Config(sdk = [24])
    fun androidSevenUsesTheSupportedAppSettingsScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = NotificationHelper.settingsIntent(context)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }
}