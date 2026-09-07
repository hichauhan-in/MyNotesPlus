package com.example.data.ml

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 25], application = Application::class)
class SmartTextCompatibilityTest {
    @Test fun unsupportedDevicesDoNotInitializeEntityExtraction() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(SmartText.analyze(context, "Pay the bill tomorrow at 9 AM").isEmpty())
    }
}