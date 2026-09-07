package com.example.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** True on tablets / large windows (>= 600dp wide) - accounts for split-screen and orientation. */
@Composable
fun isExpandedWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

/**
 * Horizontal padding that keeps long-form content centred at a comfortable reading width on large
 * screens, while leaving phones exactly as they are. On a wide window the content column is capped
 * at [maxContentWidth]; on a narrow one it just uses [compact]. Because it reacts to the live window
 * width (not just the device), it also does the right thing in split-screen and on rotation.
 */
@Composable
fun responsiveHorizontalPadding(compact: Dp = 22.dp, maxContentWidth: Dp = 760.dp): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return if (screenWidth > maxContentWidth + compact * 2) {
        (screenWidth - maxContentWidth) / 2
    } else {
        compact
    }
}
