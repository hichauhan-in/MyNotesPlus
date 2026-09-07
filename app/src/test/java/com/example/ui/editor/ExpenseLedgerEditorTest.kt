package com.example.ui.editor

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.local.ExpenseCodec
import com.example.domain.model.ExpAccount
import com.example.domain.model.ExpItem
import com.example.domain.model.ExpSection
import com.example.domain.model.ExpenseKind
import com.example.domain.model.ExpenseModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35], application = Application::class)
class ExpenseLedgerEditorTest {
    @get:Rule val compose = createComposeRule()
    private var committed: String? = null

    private fun show(kind: ExpenseKind, amount: Long = 10_000_00, readOnly: Boolean = false) {
        val model = ExpenseModel(accounts = listOf(ExpAccount(
            id = "bank", name = "HDFC", balance = 50_000_00,
            sections = listOf(ExpSection(
                id = "section", name = "Salary", iconKey = "salary", kind = kind,
                items = listOf(ExpItem(id = "item", name = "September", amount = amount)),
            )),
        )))
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalReadOnly provides readOnly) {
                    ExpenseEditor("tracker", "Expenses", ExpenseCodec.encode(model), {}, {}, onCommitContent = { committed = it })
                }
            }
        }
    }

    @Test fun completionRequiresConfirmationAndCommitsImmediately() {
        show(ExpenseKind.CREDIT)
        compose.onNodeWithText("Complete").performScrollTo().performClick()
        compose.runOnIdle { assertNull(committed) }
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle {
            val saved = ExpenseCodec.decode(requireNotNull(committed))
            assertEquals(60_000_00L, saved.accounts.single().balance)
            assertEquals(1, saved.history.size)
        }
        compose.onNodeWithText("Complete").assertDoesNotExist()
    }

    @Test fun insufficientFundsDoNotCommit() {
        show(ExpenseKind.EXPENSE, amount = 60_000_00)
        compose.onNodeWithText("Complete").performScrollTo().performClick()
        compose.onNodeWithText("Not enough balance in HDFC").assertIsDisplayed()
        compose.runOnIdle { assertNull(committed) }
    }

    @Test fun readOnlyTrackerHasNoTransactionOrBalanceMutationControls() {
        show(ExpenseKind.CREDIT, readOnly = true)
        compose.onNodeWithText("Complete").assertDoesNotExist()
        compose.onNodeWithContentDescription("Set or correct balance").assertDoesNotExist()
    }
}