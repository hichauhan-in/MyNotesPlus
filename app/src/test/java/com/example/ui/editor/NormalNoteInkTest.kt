package com.example.ui.editor

import android.app.Application
import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.TestKeyStoreProvider
import com.example.data.settings.PageInkTextMode
import com.example.di.AppContainer
import com.example.domain.model.Note
import com.example.ui.theme.MyNotesTheme
import com.example.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.UUID
import kotlinx.coroutines.runBlocking
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
@Config(sdk = [35], qualifiers = "w360dp-h840dp", application = Application::class)
class NormalNoteInkTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: EditorViewModel
    private val generation = mutableStateOf(0)

    private fun show(mode: PageInkTextMode = PageInkTextMode.BELOW, content: String = "Existing paragraph") {
        TestKeyStoreProvider.install()
        AppContainer.init(ApplicationProvider.getApplicationContext<Context>())
        val note = Note(UUID.randomUUID().toString(), "Annotated note", content, 1, 1)
        runBlocking {
            AppContainer.settingsRepository!!.setPageInkTextMode(mode)
            AppContainer.settingsRepository!!.setSmartSuggestionsEnabled(false)
            AppContainer.settingsRepository!!.setOpenNotesInEditMode(true)
            AppContainer.noteRepository!!.saveNote(note)
        }
        compose.setContent {
            MyNotesTheme(themeMode = ThemeMode.LIGHT) {
                Surface {
                    val editor: EditorViewModel = viewModel(key = "${note.id}:${generation.value}")
                    SideEffect { model = editor }
                    androidx.compose.runtime.key(generation.value) {
                        EditorScreen(editor, note.id, onNavigateBack = {})
                    }
                }
            }
        }
        compose.waitUntil(10_000) { ::model.isInitialized && model.state.value.id == note.id }
        compose.onNodeWithText("Existing paragraph").assertIsDisplayed()
    }

    private fun drawOver(text: String) {
        compose.onNodeWithContentDescription("Draw on page").performClick()
        val before = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(Offset(before.left + 16f, before.top + 16f))
            moveTo(Offset(before.left + 120f, before.top + 20f))
            up()
        }
        compose.waitForIdle()
        val after = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        assertEquals("Drawing must not move existing text", before.top, after.top, 0.5f)
        compose.onNodeWithText("Done").performScrollTo().performClick()
    }

    @Test fun inkOverTextKeepsEarlierWritingInPlaceAndNewWritingBelow() {
        show()
        drawOver("Existing paragraph")
        compose.onNodeWithText("Existing paragraph").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
        compose.onNodeWithContentDescription("Continue below ink").performTextInput("Later words")
        val first = compose.onNodeWithText("Existing paragraph").fetchSemanticsNode().boundsInRoot
        val later = compose.onNodeWithText("Later words").fetchSemanticsNode().boundsInRoot
        assertTrue(later.top >= first.bottom)
        drawOver("Later words")
        compose.onNodeWithText("Later words").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
        compose.onNodeWithContentDescription("Continue below ink").performTextInput("More writing")
        compose.runOnIdle {
            assertEquals(2, PageInk.decode(model.state.value.content).size)
            assertEquals(2, Regex("\\[\\[inkspace:").findAll(model.state.value.content).count())
        }
        compose.onRoot().captureRoboImage("build/reports/daily-use/note-ink-over-existing-text.png")
    }

    @Test fun reopeningAndUndoRedoPreserveTheWritingBoundary() {
        show()
        drawOver("Existing paragraph")
        val drawn = model.state.value.content
        compose.onNodeWithContentDescription("Undo edit").performClick()
        compose.runOnIdle { assertTrue(PageInk.decode(model.state.value.content).isEmpty()) }
        compose.onNodeWithContentDescription("Redo edit").performClick()
        compose.runOnIdle { assertEquals(drawn, model.state.value.content) }
        compose.onNodeWithContentDescription("Continue below ink").performTextInput("Later words")
        compose.runOnIdle { model.flush() }
        compose.waitUntil(10_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            model.state.value.saveStatus in setOf(SaveStatus.Saved, SaveStatus.Error)
        }
        compose.runOnIdle { assertEquals(SaveStatus.Saved, model.state.value.saveStatus) }
        val saved = model.state.value.content
        val previous = model
        compose.runOnIdle { generation.value++ }
        compose.waitUntil(10_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            model !== previous && model.state.value.content == saved
        }
        compose.onNodeWithText("Existing paragraph").assertIsDisplayed()
        compose.onNodeWithContentDescription("Continue below ink").assertIsDisplayed()
        compose.onNodeWithText("Later words").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, PageInk.decode(model.state.value.content).size) }
    }

    @Test fun freeOverlayStillAllowsEditingExistingText() {
        show(mode = PageInkTextMode.FREE)
        drawOver("Existing paragraph")
        compose.onNodeWithText("Existing paragraph").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText))
        compose.onNodeWithText("Existing paragraph").performTextInput(" allowed")
        compose.runOnIdle {
            assertTrue(model.state.value.content.contains("allowed"))
            assertFalse(model.state.value.content.contains("[[inkspace:"))
        }
    }

    @Test fun menuSearchPreservesTheActualEditorAndItsEditMode() {
        show()
        val original = model.state.value.content
        compose.onNodeWithContentDescription("Find in note").assertDoesNotExist()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithContentDescription("Find in note").performTextInput("paragraph")
        compose.onNodeWithText("1 of 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("More options").assertDoesNotExist()
        compose.onNodeWithContentDescription("Undo edit").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithText("Existing paragraph").assertIsDisplayed()
        compose.onNodeWithContentDescription("Done editing").assertIsDisplayed()
        compose.runOnIdle { assertEquals(original, model.state.value.content) }
    }

    @Test fun drawingInBlankSpaceReservesRoomBelowTheActualStroke() {
        show()
        compose.onNodeWithContentDescription("Draw on page").performClick()
        val original = compose.onNodeWithText("Existing paragraph").fetchSemanticsNode().boundsInRoot
        val inkBottom = original.top + 180f
        compose.onRoot().performTouchInput {
            down(Offset(original.left + 20f, inkBottom - 24f))
            moveTo(Offset(original.left + 130f, inkBottom))
            up()
        }
        compose.runOnIdle {
            assertEquals(1, PageInk.decode(model.state.value.content).size)
            assertTrue(model.state.value.content.contains("[[inkspace:"))
        }
        compose.onNodeWithText("Done").performScrollTo().performClick()
        val writing = compose.onNodeWithContentDescription("Continue below ink").fetchSemanticsNode().boundsInRoot
        assertTrue(writing.top > inkBottom)
        assertEquals(original.top, compose.onNodeWithText("Existing paragraph").fetchSemanticsNode().boundsInRoot.top, 0.5f)
    }

    @Test fun enablingProtectionAfterFreeDrawingKeepsTextButMovesNewTypingBelowInk() {
        show(mode = PageInkTextMode.FREE)
        drawOver("Existing paragraph")
        val original = compose.onNodeWithText("Existing paragraph").fetchSemanticsNode().boundsInRoot
        runBlocking { AppContainer.settingsRepository!!.setPageInkTextMode(PageInkTextMode.BELOW) }
        compose.waitUntil(10_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            compose.onAllNodesWithContentDescription("Continue below ink").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Existing paragraph").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
        assertEquals(original.top, compose.onNodeWithText("Existing paragraph").fetchSemanticsNode().boundsInRoot.top, 0.5f)
        compose.onNodeWithContentDescription("Continue below ink").performTextInput("Safe continuation")
        compose.runOnIdle { assertTrue(model.state.value.content.contains("Safe continuation")) }
    }
}