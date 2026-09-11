package com.example.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.data.sync.DriveRecoveryMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class DriveRecoveryDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pinRequiresLengthConfirmationAndSecurityAcknowledgement() {
        var submitted: String? = null
        var selected: DriveRecoveryMethod? = null
        compose.setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) { CreatePassphraseDialog(false, null, { secret, method -> submitted = String(secret); selected = method; secret.fill('\u0000') }, {}) } }
        }
        compose.onNodeWithText("PIN", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Recovery PIN").performTextInput("001")
        compose.onNodeWithText("Confirm PIN").performTextInput("001")
        compose.onNodeWithText("Set PIN").assertIsNotEnabled()
        compose.onNodeWithText("Recovery PIN").performTextReplacement("0012")
        compose.onNodeWithText("Confirm PIN").performTextReplacement("0012")
        compose.onNodeWithText("Set PIN").assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Set PIN").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("0012", submitted); assertEquals(DriveRecoveryMethod.PIN, selected) }
    }

    @Test fun reconnectUsesNumericPinInputAndKeepsLeadingZeroes() {
        var submitted: String? = null
        compose.setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) { EnterPassphraseDialog(false, null, { submitted = String(it); it.fill('\u0000') }, {}, DriveRecoveryMethod.PIN) } }
        }
        compose.onNodeWithText("Recovery PIN").performTextInput("0001234567")
        compose.onNodeWithText("Unlock").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("0001234567", submitted) }
    }

    @Test fun passphraseRemainsTheDefaultAndDoesNotNeedPinAcknowledgement() {
        var submitted = false
        compose.setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) { CreatePassphraseDialog(false, null, { secret, method -> submitted = method == DriveRecoveryMethod.PASSPHRASE; secret.fill('\u0000') }, {}) } }
        }
        compose.onNodeWithText("Recovery passphrase").performTextInput("long recovery phrase")
        compose.onNodeWithText("Confirm passphrase").performTextInput("long recovery phrase")
        compose.onNodeWithText("Set passphrase").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(submitted) }
    }

    @Test fun restartRequiresExplicitApprovalAndCanBeCancelled() {
        var approved = false
        var dismissed = false
        compose.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { DriveRestartDialog({ approved = true }, {}, { dismissed = true }) } } }
        compose.runOnIdle { assertFalse(approved) }
        compose.onNodeWithText("Not now").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertFalse(approved) }
    }
}