package com.example.ui.home

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp", application = Application::class)
class CreateMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test fun adjacentCreateActionsHaveSeparateBounds() {
        compose.setContent {
            MaterialTheme { ExpandableFab(true, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("New note").performScrollTo()
        val note = compose.onNodeWithText("New note").fetchSemanticsNode().boundsInRoot
        val checklist = compose.onNodeWithText("Checklist").fetchSemanticsNode().boundsInRoot
        assertTrue(note.top > checklist.bottom)
    }

    @Test
    @Config(qualifiers = "w640dp-h320dp")
    fun importActionRemainsReachableInLandscape() {
        var imported = false
        compose.setContent {
            MaterialTheme { ExpandableFab(true, {}, {}, {}, { imported = true }, {}) }
        }
        compose.onNodeWithText("Import note").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(imported) }
    }
}