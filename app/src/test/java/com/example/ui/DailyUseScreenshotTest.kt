package com.example.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.local.ExpenseCodec
import com.example.domain.model.ExpAccount
import com.example.domain.model.ExpItem
import com.example.domain.model.ExpSection
import com.example.domain.model.ExpenseKind
import com.example.domain.model.ExpenseModel
import com.example.ui.editor.ExpenseEditor
import com.example.ui.home.ExpandableFab
import com.example.ui.theme.MyNotesTheme
import com.example.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp", application = Application::class)
class DailyUseScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun expenses(file: String, fontScale: Float = 1f) {
        val model = ExpenseModel(listOf(
            ExpAccount(id = "hdfc", name = "HDFC", balance = 48_000_00, sections = listOf(
                ExpSection(name = "Monthly savings", iconKey = "savings", kind = ExpenseKind.SAVINGS,
                    items = listOf(ExpItem(name = "Emergency fund", amount = 1_000_00, completedAt = 1788739200000))),
                ExpSection(name = "Everyday spending", iconKey = "other", kind = ExpenseKind.EXPENSE,
                    items = listOf(ExpItem(name = "Groceries", amount = 8_000_00))),
            )),
            ExpAccount(id = "sbi", name = "SBI", balance = 12_000_00),
        ))
        compose.setContent {
            MyNotesTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) { ExpenseEditor("screenshot", "My monthly plan", ExpenseCodec.encode(model), {}, {}) }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/reports/daily-use/$file.png")
    }

    @Test fun expensePhone() = expenses("expenses-phone")

    @Test
    @Config(qualifiers = "w840dp-h1100dp")
    fun expenseTabletLargeText() = expenses("expenses-tablet-large-text", 1.5f)

    @Test fun createMenuPhone() {
        compose.setContent {
            MyNotesTheme(themeMode = ThemeMode.LIGHT) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(20.dp)) {
                        ExpandableFab(true, {}, {}, {}, {}, {}, modifier = Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/reports/daily-use/create-menu-phone.png")
    }
}