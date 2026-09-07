package com.example.ui.home

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import com.example.BuildConfig
import com.example.data.security.TestKeyStoreProvider
import com.example.di.AppContainer
import com.example.ui.theme.MyNotesTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp", application = Application::class)
class VoluntarySupportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun donationIconOpensExistingDisclaimerAndPaymentOptions() {
        assertTrue(BuildConfig.EXTERNAL_SUPPORT_ENABLED)
        TestKeyStoreProvider.install()
        AppContainer.init(ApplicationProvider.getApplicationContext<Context>())
        compose.setContent {
            MyNotesTheme {
                HomeScreen(
                    viewModel = viewModel<HomeViewModel>(),
                    onNoteClick = {},
                    onCreateNote = { _, _ -> },
                    onEditTemplate = {},
                    onOpenSettings = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Buy me a coffee").assertIsDisplayed().performClick()
        compose.onNodeWithText("UPI").assertIsDisplayed()
        compose.onNodeWithText("Ko-fi").assertIsDisplayed()
        compose.onNodeWithText(
            "A voluntary, friendly gesture, nothing more. It does not " +
                "unlock any features, remove any limits, or change how the app works. " +
                "MyNotes+ is completely free and always will be, and there's no " +
                "obligation to contribute.",
        ).assertIsDisplayed()
    }
}