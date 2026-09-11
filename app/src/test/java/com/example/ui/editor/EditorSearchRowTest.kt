package com.example.ui.editor

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyNotesTheme
import com.example.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
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

    @Test fun boardExposesUndoAndRedoOnlyInTheTopToolbar() {
        compose.setContent {
            MyNotesTheme {
                Surface {
                    Column(Modifier.fillMaxSize()) {
                        EditorTopBar(SaveStatus.Saved, {}, false, true, onBack = {}, onToggleEdit = {},
                            onShare = {}, onSearch = {}, onVersions = {}, onExport = {}, onRemind = {}, onDelete = {})
                        ScribbleEditor("board", "Board", "", {}, {}, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Undo edit").assertIsDisplayed()
        compose.onNodeWithContentDescription("Redo edit").assertIsDisplayed()
        compose.onNodeWithContentDescription("Undo").assertDoesNotExist()
        compose.onNodeWithContentDescription("Redo").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/reports/daily-use/editor-board-clean-toolbar.png")
    }

    @Test fun readOnlyToolbarKeepsSearchAndVersionsInTheMenu() {
        var searched = false
        compose.setContent {
            MyNotesTheme {
                EditorTopBar(SaveStatus.Saved, {}, false, false, onBack = {}, onToggleEdit = {},
                    onShare = {}, onSearch = { searched = true }, onVersions = {}, onExport = {}, onRemind = {}, onDelete = {})
            }
        }
        compose.onNodeWithText("Find in note").assertDoesNotExist()
        compose.onNodeWithContentDescription("Undo edit").assertDoesNotExist()
        compose.onNodeWithContentDescription("Redo edit").assertDoesNotExist()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Recent versions").assertIsDisplayed()
        val search = compose.onNodeWithText("Search").fetchSemanticsNode().boundsInRoot
        val export = compose.onNodeWithText("Export").fetchSemanticsNode().boundsInRoot
        assertTrue(search.top < export.top && search.bottom <= export.top + 0.5f)
        compose.onNodeWithText("Search").performClick()
        compose.runOnIdle { assertTrue(searched) }
    }

    @Test fun undoAndRedoSitBeforeDoneOnlyInEditMode() {
        val editing = mutableStateOf(true)
        var undone = false
        var redone = false
        compose.setContent {
            MyNotesTheme {
                EditorTopBar(SaveStatus.Saved, {}, false, editing.value, canUndo = true, canRedo = true,
                    onUndo = { undone = true }, onRedo = { redone = true }, onBack = {}, onToggleEdit = { editing.value = false },
                    onShare = {}, onSearch = {}, onVersions = {}, onExport = {}, onRemind = {}, onDelete = {})
            }
        }
        val undo = compose.onNodeWithContentDescription("Undo edit").fetchSemanticsNode().boundsInRoot
        val redo = compose.onNodeWithContentDescription("Redo edit").fetchSemanticsNode().boundsInRoot
        val done = compose.onNodeWithContentDescription("Done editing").fetchSemanticsNode().boundsInRoot
        assertTrue(undo.right <= redo.left && redo.right <= done.left)
        compose.onNodeWithContentDescription("Undo edit").performClick()
        compose.onNodeWithContentDescription("Redo edit").performClick()
        compose.runOnIdle { assertTrue(undone && redone) }
        compose.onNodeWithContentDescription("Done editing").performClick()
        compose.onNodeWithContentDescription("Undo edit").assertDoesNotExist()
        compose.onNodeWithContentDescription("Redo edit").assertDoesNotExist()
    }

    private fun showSearch(
        width: Dp = 328.dp,
        fontScale: Float = 1f,
        direction: LayoutDirection = LayoutDirection.Ltr,
        content: String = "A note to search\nA note to keep",
        type: String = "TEXT",
    ) {
        val searching = mutableStateOf(true)
        compose.setContent {
            MyNotesTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    LocalLayoutDirection provides direction,
                ) {
                    Surface {
                        Column(Modifier.width(width)) {
                            if (searching.value) FindInNote(content, type, onDismiss = { searching.value = false })
                            else EditorTopBar(SaveStatus.Saved, {}, false, false, onBack = {}, onToggleEdit = {},
                                onShare = {}, onSearch = { searching.value = true }, onVersions = {}, onExport = {}, onRemind = {}, onDelete = {})
                        }
                    }
                }
            }
        }
    }

    @Test fun inlineSearchFindsMatchesAndClosingRestoresTheToolbar() {
        showSearch()
        compose.onNodeWithContentDescription("More options").assertDoesNotExist()
        compose.onNodeWithContentDescription("Find in note").performTextInput("note")
        compose.onNodeWithText("1 of 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next match").performClick()
        compose.onNodeWithText("2 of 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous match").performClick()
        compose.onNodeWithText("1 of 2").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/reports/daily-use/editor-search-inline.png")
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithContentDescription("Find in note").assertDoesNotExist()
        compose.onNodeWithContentDescription("More options").assertIsDisplayed()
    }

    @Test fun narrowSearchKeepsTheInputAndCloseControlSeparate() {
        showSearch(width = 288.dp, fontScale = 1.5f)
        val close = compose.onNodeWithContentDescription("Close search").fetchSemanticsNode().boundsInRoot
        val input = compose.onNodeWithContentDescription("Find in note").fetchSemanticsNode().boundsInRoot
        assertTrue(close.right <= input.left)
        compose.onNodeWithContentDescription("Find in note").performTextInput("missing")
        compose.onNodeWithText("No matches").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithContentDescription("Clear search").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/reports/daily-use/editor-search-narrow-large-text.png")
    }

    @Test fun searchDirectionFollowsRightToLeftLayout() {
        showSearch(direction = LayoutDirection.Rtl)
        val search = compose.onNodeWithContentDescription("Find in note").fetchSemanticsNode().boundsInRoot
        val close = compose.onNodeWithContentDescription("Close search").fetchSemanticsNode().boundsInRoot
        assertTrue(search.right <= close.left)
    }

    @Test fun boardTextRemainsSearchableInInlineSearch() {
        showSearch(content = """{"t":[{"t":"Project plan"}]}""", type = "SCRIBBLE")
        compose.onNodeWithContentDescription("Find in note").performTextInput("project")
        compose.onNodeWithText("1 of 1").assertIsDisplayed()
    }
}