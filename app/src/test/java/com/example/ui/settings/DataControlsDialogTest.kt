package com.example.ui.settings

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MyNotesTheme
import com.example.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp", application = Application::class)
class DataControlsDialogTest {
    @get:Rule val compose = createComposeRule()

    private fun show(theme: ThemeMode = ThemeMode.LIGHT, fontScale: Float = 1f, onDismiss: () -> Unit = {}) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            MyNotesTheme(themeMode = theme) {
                Surface(Modifier.fillMaxSize()) { DataControlsDialog(onDismiss) }
            }
        }
    }

    @Test fun allThreeThemedButtonsOpenTheirExistingDestinations() {
        show()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val application = shadowOf(context)
        val targets = listOf(
            Triple("Device storage", android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}"),
            Triple("Manage Drive files", Intent.ACTION_VIEW, "https://drive.google.com/drive/my-drive"),
            Triple("Google account permissions", Intent.ACTION_VIEW, "https://myaccount.google.com/connections"),
        )
        targets.forEach { (label, action, uri) ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
            compose.runOnIdle {
                val intent = requireNotNull(application.nextStartedActivity)
                assertEquals(action, intent.action)
                assertEquals(uri, intent.dataString)
            }
        }
    }

    @Test fun brandedPopupHasAFixedCloseControlAndScrollableBody() {
        var closed = false
        show(onDismiss = { closed = true })
        compose.onNodeWithText("Data controls").assertIsDisplayed()
        compose.onNodeWithText("Storage & permissions").assertIsDisplayed()
        val closeBefore = compose.onNodeWithContentDescription("Close data controls").fetchSemanticsNode().boundsInRoot
        compose.onNode(isDialog()).captureRoboImage("build/reports/daily-use/data-controls-light.png")
        compose.onNodeWithText("Google account permissions").performScrollTo().assertIsDisplayed()
        val closeAfter = compose.onNodeWithContentDescription("Close data controls").fetchSemanticsNode().boundsInRoot
        assertEquals(closeBefore, closeAfter)
        compose.onNodeWithContentDescription("Close data controls").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun buttonsRemainReadableOnASmallScreenWithLargeTextAndDarkTheme() {
        show(theme = ThemeMode.DARK, fontScale = 1.5f)
        listOf("Device storage", "Manage Drive files", "Google account permissions").forEach { label ->
            val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val button = node.fetchSemanticsNode().boundsInRoot
            val labelBounds = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(labelBounds.left >= button.left && labelBounds.right <= button.right)
            assertTrue(labelBounds.top >= button.top && labelBounds.bottom <= button.bottom)
        }
        compose.onNodeWithContentDescription("Close data controls").assertIsDisplayed()
        compose.onNode(isDialog()).captureRoboImage("build/reports/daily-use/data-controls-dark-large-text.png")
    }
}