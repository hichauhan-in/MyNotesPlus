package com.example.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Theme-aware neumorphic shadow colours. Provided via [LocalNeuColors] so the same
 * component renders correctly in light and dark without branching at every call site.
 */
@Immutable
data class NeuColors(
    val highlight: Color,
    val shadow: Color,
)

val LocalNeuColors = staticCompositionLocalOf {
    NeuColors(highlight = NeuLightHighlight, shadow = NeuLightShadow)
}

/**
 * Draws a single soft, blurred rounded-rect shadow *behind* the content.
 * Blur is achieved with a native [BlurMaskFilter] so it works across API levels.
 */
fun Modifier.softShadow(
    color: Color,
    cornerRadius: Dp,
    blur: Dp,
    offsetX: Dp,
    offsetY: Dp,
    spread: Dp = 0.dp,
    alpha: Float = 1f,
): Modifier = this.drawWithCache {
    val effectiveAlpha = color.alpha * alpha
    if (effectiveAlpha == 0f) {
        onDrawBehind { }
    } else {
        // Build the blurred paint once per size/param change instead of on every draw pass, so
        // scrolling and animating lists of neumorphic cards don't churn Paint/BlurMaskFilter objects.
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        val blurPx = blur.toPx()
        if (blurPx > 0f) {
            frameworkPaint.maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        frameworkPaint.color = color.copy(alpha = effectiveAlpha).toArgb()

        val spreadPx = spread.toPx()
        val left = offsetX.toPx() - spreadPx
        val top = offsetY.toPx() - spreadPx
        val right = size.width + offsetX.toPx() + spreadPx
        val bottom = size.height + offsetY.toPx() + spreadPx
        val radius = cornerRadius.toPx()

        onDrawBehind {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRoundRect(left, top, right, bottom, radius, radius, frameworkPaint)
            }
        }
    }
}

/**
 * A raised neumorphic surface: a soft dark shadow on the bottom-right and a light
 * highlight on the top-left. Apply an opaque background *after* this modifier.
 */
fun Modifier.neumorphicRaised(
    cornerRadius: Dp,
    neuColors: NeuColors,
    elevation: Dp = 9.dp,
    intensity: Float = 1f,
): Modifier {
    val o = elevation * 0.75f
    val b = elevation * 1.7f
    return this
        .softShadow(neuColors.shadow, cornerRadius, blur = b, offsetX = o, offsetY = o, alpha = intensity)
        .softShadow(neuColors.highlight, cornerRadius, blur = b, offsetX = -o, offsetY = -o, alpha = intensity)
}

// ---- Brand gradients -----------------------------------------------------------

val BrandGradientColors = listOf(
    Color(0xFF6D5EF6),
    Color(0xFF7C5CF0),
    Color(0xFF9B5DE5),
)

fun brandGradient(): Brush = Brush.linearGradient(
    colors = BrandGradientColors,
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
)

fun brandGradientHorizontal(): Brush = Brush.horizontalGradient(BrandGradientColors)

fun subtleSurfaceGradient(top: Color, bottom: Color): Brush =
    Brush.verticalGradient(listOf(top, bottom))
