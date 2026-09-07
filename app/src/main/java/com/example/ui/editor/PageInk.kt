package com.example.ui.editor

import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalNeuColors
import com.example.ui.theme.brandGradientHorizontal
import com.example.ui.theme.neumorphicRaised
import org.json.JSONArray
import org.json.JSONObject

/** One freehand stroke drawn over the whole note. Points are stored in density-independent (dp)
 *  content coordinates, so the ink stays put over the text regardless of screen density. */
internal data class InkStroke(val color: Int, val width: Float, val points: List<Offset>)

/**
 * Whole-note "page ink": the freehand layer a user can draw over the entire note, mixing with
 * typed text, images, tables and everything else. It is persisted as a single token appended to
 * the note content ([[ink:BASE64]]), so it round-trips through the normal encrypted note storage,
 * export and sync without any schema change.
 */
internal object PageInk {
    private val TOKEN = Regex("""\[\[ink:([A-Za-z0-9+/=]+)]]""")

    /** The note content without its ink token (what the block editor parses). */
    fun stripInk(content: String): String = content.replace(TOKEN, "").trimEnd()

    fun decode(content: String): List<InkStroke> {
        val match = TOKEN.find(content) ?: return emptyList()
        return runCatching {
            val json = String(Base64.decode(match.groupValues[1], Base64.NO_WRAP), Charsets.UTF_8)
            val arr = JSONObject(json).optJSONArray("s") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val p = o.optJSONArray("p") ?: return@mapNotNull null
                val pts = (0 until p.length()).mapNotNull { k ->
                    p.optJSONArray(k)?.let { Offset(it.optDouble(0).toFloat(), it.optDouble(1).toFloat()) }
                }
                if (pts.isEmpty()) null
                else InkStroke(o.optInt("c", 0), o.optDouble("w", 3.0).toFloat(), pts)
            }
        }.getOrDefault(emptyList())
    }

    /** The [[ink:...]] token for [strokes], or an empty string when there is no ink. */
    fun encodeToken(strokes: List<InkStroke>): String {
        if (strokes.isEmpty()) return ""
        val arr = JSONArray()
        strokes.forEach { s ->
            val p = JSONArray()
            s.points.forEach { pt -> p.put(JSONArray().put(pt.x.toDouble()).put(pt.y.toDouble())) }
            arr.put(JSONObject().put("c", s.color).put("w", s.width.toDouble()).put("p", p))
        }
        val json = JSONObject().put("s", arr).toString()
        return "[[ink:" + Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) + "]]"
    }
}

/** Pen colours; 0 means "follow the theme" (onSurface, so it flips with light/dark). */
internal val inkPenColors = listOf(
    0,
    0xFFEF4444.toInt(),
    0xFF3B82F6.toInt(),
    0xFF22C55E.toInt(),
    0xFFF59E0B.toInt(),
    0xFFA855F7.toInt(),
)

internal data class InkWidth(val label: String, val dp: Float)

internal val inkWidths = listOf(InkWidth("Fine", 2f), InkWidth("Medium", 4f), InkWidth("Bold", 7f))

private fun inkColor(argb: Int, theme: Color): Color = if (argb == 0) theme else Color(argb)

private fun DrawScope.drawInkPath(pointsDp: List<Offset>, color: Color, widthPx: Float, density: Float, scroll: Float) {
    if (pointsDp.isEmpty()) return
    if (pointsDp.size == 1) {
        val p = pointsDp[0]
        drawCircle(color, radius = widthPx / 2f, center = Offset(p.x * density, p.y * density - scroll))
        return
    }
    val path = Path()
    val p0 = pointsDp[0]
    path.moveTo(p0.x * density, p0.y * density - scroll)
    for (i in 1 until pointsDp.size) {
        val p = pointsDp[i]
        path.lineTo(p.x * density, p.y * density - scroll)
    }
    drawPath(path, color, style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * The freehand overlay drawn on top of the (scrolling) note body. Committed [strokes] always show;
 * when [drawEnabled] a single finger/stylus draws a new stroke and two fingers scroll the page.
 * Strokes are captured in content-dp coordinates (viewport + current scroll), so they stay pinned
 * to the content as it scrolls.
 */
@Composable
internal fun PageInkLayer(
    strokes: List<InkStroke>,
    drawEnabled: Boolean,
    penColor: Int,
    penWidthDp: Float,
    scrollState: ScrollState,
    onCommitStroke: (InkStroke) -> Unit,
    protectedBounds: List<Rect>,
    horizontalInsetPx: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    val themeInk = MaterialTheme.colorScheme.onSurface
    val live = remember { mutableStateListOf<Offset>() }
    val commitStroke = rememberUpdatedState(onCommitStroke)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val exclusions = protectedBounds.map { it.translate(-origin) }
    val currentExclusions = rememberUpdatedState(exclusions)

    val gesture = if (drawEnabled) {
        Modifier.pointerInput(penColor, penWidthDp, density, horizontalInsetPx) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val bounds = Rect(horizontalInsetPx, 0f, size.width - horizontalInsetPx, size.height.toFloat())
                fun canDraw(point: Offset) = bounds.contains(point) &&
                    currentExclusions.value.none { it.contains(point) }
                if (!canDraw(first.position)) return@awaitEachGesture
                first.consume()
                val startScroll = scrollState.value
                live.clear()
                live.add(Offset(first.position.x / density, (first.position.y + startScroll) / density))
                var panning = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            panning = true
                            live.clear()
                        }
                        if (panning) {
                            val moving = event.changes.filter { it.pressed && it.previousPressed }
                            val delta = moving.map { it.positionChange().y }.average().toFloat()
                            if (delta.isFinite()) scrollState.dispatchRawDelta(-delta)
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = event.changes.firstOrNull { it.id == first.id }
                            if (change != null) {
                                change.consume()
                                if (!canDraw(change.position)) break
                                val point = Offset(change.position.x / density, (change.position.y + startScroll) / density)
                                if (live.lastOrNull() != point) live.add(point)
                            }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (!panning && live.isNotEmpty()) {
                        commitStroke.value(InkStroke(penColor, penWidthDp, live.toList()))
                    }
                } finally {
                    live.clear()
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier.fillMaxSize().clipToBounds()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .then(gesture),
    ) {
        content()
        Canvas(Modifier.matchParentSize()) {
            val scroll = scrollState.value.toFloat()
            val protectedPath = Path().apply { exclusions.forEach { addRect(it) } }
            clipRect(left = horizontalInsetPx, right = size.width - horizontalInsetPx) {
                clipPath(protectedPath, clipOp = ClipOp.Difference) {
                    strokes.forEach { stroke ->
                        drawInkPath(stroke.points, inkColor(stroke.color, themeInk), stroke.width * density, density, scroll)
                    }
                    if (live.isNotEmpty()) {
                        drawInkPath(live, inkColor(penColor, themeInk), penWidthDp * density, density, scroll)
                    }
                }
            }
        }
    }
}

/** The bottom toolbar shown while page-draw mode is on: pen colours, widths, undo, clear and done. */
@Composable
internal fun PageInkToolbar(
    penColor: Int,
    penWidthDp: Float,
    onPenColor: (Int) -> Unit,
    onPenWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onRecognize: () -> Unit,
    onDone: () -> Unit,
) {
    val neu = LocalNeuColors.current
    val themeInk = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .neumorphicRaised(24.dp, neu, elevation = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        inkPenColors.forEach { c ->
            val selected = c == penColor
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(inkColor(c, themeInk))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clip(CircleShape)
                    .clickable { onPenColor(c) },
            )
        }
        InkDivider()
        inkWidths.forEach { w ->
            val selected = w.dp == penWidthDp
            val dotSize = (w.dp * 2.2f).coerceAtMost(16f).dp
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else Color.Transparent,
                    )
                    .clickable { onPenWidth(w.dp) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else themeInk),
                )
            }
        }
        InkDivider()
        InkIcon(Icons.Rounded.TextFields, "Convert handwriting to text", onRecognize)
        InkIcon(Icons.Rounded.Undo, "Undo", onUndo)
        InkIcon(Icons.Rounded.DeleteSweep, "Clear ink", onClear)
        Spacer(Modifier.width(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, brandGradientHorizontal(), RoundedCornerShape(50))
                .clickable { onDone() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Done", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun InkDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .height(26.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
    )
}

@Composable
private fun InkIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}
