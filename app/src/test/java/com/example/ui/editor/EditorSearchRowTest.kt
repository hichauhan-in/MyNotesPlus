package com.example.ui.editor

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyNotesTheme
import com.example.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp", application = Application::class)
class EditorSearchRowTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        count: MutableState<Int> = mutableStateOf(0),
        width: Dp = 328.dp,
        fontScale: Float = 1f,
        direction: LayoutDirection = LayoutDirection.Ltr,
        onAction: (Int) -> Unit = {},
    ) {
        val searching = mutableStateOf(false)
        compose.setContent {
            MyNotesTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    LocalLayoutDirection provides direction,
                ) {
                    val actions: (@Composable RowScope.() -> Unit)? = if (count.value == 0) null else {
                        {
                            repeat(count.value) { index ->
                                IconButton(onClick = { onAction(index) }) {
                                    Icon(Icons.AutoMirrored.Rounded.Undo, "Action $index")
                                }
                            }
                        }
                    }
                    Surface {
                        Column(Modifier.width(width)) {
                            EditorSearchRow(
                                onSearch = { searching.value = true },
                                modifier = Modifier.testTag("search-row"),
                                actions = actions,
                            )
                        }
                    }
                    if (searching.value) FindInNote("A note to search", "TEXT", onDismiss = { searching.value = false })
                }
            }
        }
    }

    @Test fun searchBarFillsTheRowWhenThereAreNoActions() {
        show()
        val row = compose.onNodeWithTag("search-row").fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithText("Find in note").fetchSemanticsNode().boundsInRoot
        assertEquals(row.left, search.left, 0.5f)
        assertEquals(row.right, search.right, 0.5f)
        compose.onRoot().captureRoboImage("build/reports/daily-use/editor-search-full-width.png")
    }

    @Test fun searchShrinksAndExpandsAsActionsChange() {
        val count = mutableStateOf(0)
        show(count)
        val fullWidth = compose.onNodeWithText("Find in note").fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle { count.value = 3 }
        val search = compose.onNodeWithText("Find in note").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithContentDescription("Action 0").fetchSemanticsNode().boundsInRoot
        assertTrue(search.width < fullWidth)
        assertTrue(search.right < first.left)
        repeat(3) { compose.onNodeWithContentDescription("Action $it").assertIsDisplayed() }
        compose.onRoot().captureRoboImage("build/reports/daily-use/editor-search-with-actions.png")
        compose.runOnIdle { count.value = 1 }
        assertTrue(compose.onNodeWithText("Find in note").fetchSemanticsNode().boundsInRoot.width > search.width)
        compose.runOnIdle { count.value = 0 }
        assertEquals(fullWidth, compose.onNodeWithText("Find in note").fetchSemanticsNode().boundsInRoot.width, 0.5f)
    }

    @Test fun extraActionsStayReachableWithoutCollapsingSearch() {
        var clicked = -1
        show(mutableStateOf(6), width = 288.dp, fontScale = 1.5f, onAction = { clicked = it })
        val before = compose.onNodeWithText("Find in note").fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("search-row").fetchSemanticsNode().boundsInRoot
        assertTrue(before.width >= row.width / 2)
        compose.onNodeWithContentDescription("Action 5").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(5, clicked) }
        compose.onNodeWithText("Find in note").assertIsDisplayed()
        assertEquals(before.width, compose.onNodeWithText("Find in note").fetchSemanticsNode().boundsInRoot.width, 0.5f)
        compose.onRoot().captureRoboImage("build/reports/daily-use/editor-search-narrow-large-text.png")
    }

    @Test fun tappingTheBarOpensExistingNoteSearch() {
        show()
        compose.onNodeWithText("Find in note").performClick()
        compose.onNodeWithContentDescription("Close search").assertIsDisplayed()
        compose.onNodeWithText("Find").assertIsDisplayed()
    }

    @Test fun trailingActionsFollowRightToLeftLayout() {
        show(mutableStateOf(2), direction = LayoutDirection.Rtl)
        val search = compose.onNodeWithText("Find in note").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithContentDescription("Action 0").fetchSemanticsNode().boundsInRoot
        assertTrue(action.right < search.left)
    }
}