package com.example.ui.editor

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PageInkLayerTest {
    @get:Rule val compose = createComposeRule()

    private val committed = mutableListOf<InkStroke>()
    private var headerClicks = 0

    private fun showInk(enabled: Boolean = true) {
        compose.setContent {
            var headerBounds by remember { mutableStateOf(Rect.Zero) }
            MaterialTheme {
                Box(Modifier.size(240.dp)) {
                    PageInkLayer(
                        strokes = emptyList(),
                        drawEnabled = enabled,
                        penColor = 0,
                        penWidthDp = 4f,
                        scrollState = rememberScrollState(),
                        onCommitStroke = { committed.add(it) },
                        protectedBounds = listOf(headerBounds),
                        horizontalInsetPx = 0f,
                        modifier = Modifier.testTag("ink"),
                    ) {
                        Column {
                            Box(
                                Modifier.fillMaxWidth().height(60.dp)
                                    .onGloballyPositioned { headerBounds = it.boundsInRoot() }
                                    .clickable { headerClicks++ }
                                    .testTag("header"),
                            )
                        }
                    }
                }
            }
        }
    }

    @Test fun protectedHeaderAcceptsTapsWithoutInk() {
        showInk()
        compose.onNodeWithTag("ink").performTouchInput {
            down(Offset(width / 2f, height / 8f))
            up()
        }
        compose.runOnIdle {
            assertTrue(committed.isEmpty())
            assertEquals(1, headerClicks)
        }
    }

    @Test fun strokeStopsWhenItLeavesTheWritingSurface() {
        showInk()
        compose.onNodeWithTag("ink").performTouchInput {
            down(center)
            moveTo(Offset(center.x, height * 0.8f))
            moveTo(Offset(center.x, height + 100f))
            up()
        }
        compose.runOnIdle {
            assertEquals(1, committed.size)
            assertTrue(committed.single().points.all { it.y in 60f..240f })
        }
    }

    @Test fun strokeStopsBeforeEnteringProtectedHeader() {
        showInk()
        compose.onNodeWithTag("ink").performTouchInput {
            down(center)
            moveTo(Offset(center.x, height / 8f))
            up()
        }
        compose.runOnIdle {
            assertEquals(1, committed.size)
            assertTrue(committed.single().points.all { it.y >= 60f })
        }
    }

    @Test fun readOnlyLayerNeverRecordsInput() {
        showInk(enabled = false)
        compose.onNodeWithTag("ink").performTouchInput {
            down(center)
            moveTo(Offset(center.x, height * 0.8f))
            up()
        }
        compose.onNodeWithTag("header").performClick()
        compose.runOnIdle {
            assertTrue(committed.isEmpty())
            assertEquals(1, headerClicks)
        }
    }
}