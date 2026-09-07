package com.example.ui.editor

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.ArrowRightAlt
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Rectangle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.attachments.AttachmentStore
import com.example.data.attachments.EncAttachment
import com.example.data.ml.HandwritingRecognizer
import com.example.domain.model.AttachmentMarkup
import com.example.ui.theme.LocalNeuColors
import com.example.ui.theme.neumorphicRaised
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class WbMode { DRAW, SHAPE, MOVE, TEXT }

private enum class WbShapeType { LINE, ARROW, RECT, ELLIPSE }

private data class WbText(val id: String, val x: Float, val y: Float, val text: String, val colorArgb: Int = 0)

/** A single freehand stroke. [color] 0 means "follow the theme" (onSurface); else a fixed ARGB. */
private data class WbStroke(val points: List<Offset>, val color: Int, val width: Float)

/** A straight-edged shape drawn between [start] and [end] (canvas coordinates). */
private data class WbShape(
    val type: WbShapeType,
    val start: Offset,
    val end: Offset,
    val color: Int,
    val width: Float,
)

/** An image placed on the board. [attachment] is the encrypted attachment file name. */
private data class WbImage(
    val id: String,
    val attachment: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

private data class WbModel(
    val strokes: List<WbStroke>,
    val shapes: List<WbShape>,
    val images: List<WbImage>,
    val texts: List<WbText>,
    val panX: Float,
    val panY: Float,
    val scale: Float,
)

private const val WB_MIN_SCALE = 0.2f
private const val WB_MAX_SCALE = 8f

private data class PenPreset(val label: String, val width: Float)

private val penPresets = listOf(
    PenPreset("Fine", 2f),
    PenPreset("Medium", 4f),
    PenPreset("Bold", 7f),
    PenPreset("Marker", 12f),
)

// 0 == follow the current theme (onSurface); everything else is a fixed ARGB colour.
private val penColors = listOf(
    0,
    0xFFE53935.toInt(),
    0xFFFB8C00.toInt(),
    0xFFFDD835.toInt(),
    0xFF43A047.toInt(),
    0xFF1E88E5.toInt(),
    0xFF8E24AA.toInt(),
    0xFFEC407A.toInt(),
)

private fun emptyWb() = WbModel(emptyList(), emptyList(), emptyList(), emptyList(), 0f, 0f, 1f)

// Sticky-note colours for board text notes. 0 == the default surface (theme) colour.
private val wbNoteColors = listOf(
    0,
    0xFFFFF3B0.toInt(),
    0xFFFFD6A5.toInt(),
    0xFFCDEAC0.toInt(),
    0xFFBFD7FF.toInt(),
    0xFFE7C6FF.toInt(),
    0xFFFFC9C9.toInt(),
)

/**
 * Width / height of an attachment image (respecting EXIF rotation), or null if it can't be read.
 * Used to size a board image to its natural aspect ratio so it never looks stretched or letterboxed.
 * Call off the main thread (it decodes bounds + reads EXIF).
 */
private fun imageAspectRatio(context: Context, name: String): Float? {
    val bytes = AttachmentStore.readDecrypted(context, name) ?: return null
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    var w = opts.outWidth
    var h = opts.outHeight
    if (w <= 0 || h <= 0) return null
    runCatching {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            val t = w; w = h; h = t
        }
    }
    return w.toFloat() / h.toFloat()
}

private fun parseWb(content: String): WbModel = runCatching {
    if (content.isBlank()) return@runCatching emptyWb()
    val o = JSONObject(content)
    val sArr = o.optJSONArray("s") ?: JSONArray()
    val strokes = (0 until sArr.length()).mapNotNull { i ->
        when (val el = sArr.get(i)) {
            is JSONObject -> {
                val ptsArr = el.optJSONArray("p") ?: JSONArray()
                val pts = (0 until ptsArr.length()).map { j ->
                    val p = ptsArr.getJSONArray(j)
                    Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
                }
                WbStroke(pts, el.optInt("c", 0), el.optDouble("w", 3.0).toFloat())
            }
            is JSONArray -> {
                // Legacy format: a stroke was just an array of [x, y] points.
                val pts = (0 until el.length()).map { j ->
                    val p = el.getJSONArray(j)
                    Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
                }
                WbStroke(pts, 0, 3f)
            }
            else -> null
        }
    }
    val tArr = o.optJSONArray("t") ?: JSONArray()
    val texts = (0 until tArr.length()).map { i ->
        val t = tArr.getJSONObject(i)
        WbText(
            t.optString("id", UUID.randomUUID().toString()),
            t.optDouble("x", 0.0).toFloat(),
            t.optDouble("y", 0.0).toFloat(),
            t.optString("t"),
            t.optInt("c", 0),
        )
    }
    val shArr = o.optJSONArray("sh") ?: JSONArray()
    val shapes = (0 until shArr.length()).mapNotNull { i ->
        val sh = shArr.optJSONObject(i) ?: return@mapNotNull null
        val type = runCatching { WbShapeType.valueOf(sh.optString("k", "RECT")) }.getOrDefault(WbShapeType.RECT)
        WbShape(
            type,
            Offset(sh.optDouble("sx", 0.0).toFloat(), sh.optDouble("sy", 0.0).toFloat()),
            Offset(sh.optDouble("ex", 0.0).toFloat(), sh.optDouble("ey", 0.0).toFloat()),
            sh.optInt("c", 0),
            sh.optDouble("w", 3.0).toFloat(),
        )
    }
    val imArr = o.optJSONArray("im") ?: JSONArray()
    val images = (0 until imArr.length()).mapNotNull { i ->
        val im = imArr.optJSONObject(i) ?: return@mapNotNull null
        val name = im.optString("a")
        if (name.isBlank()) return@mapNotNull null
        WbImage(
            im.optString("id", UUID.randomUUID().toString()),
            name,
            im.optDouble("x", 0.0).toFloat(),
            im.optDouble("y", 0.0).toFloat(),
            im.optDouble("w", 300.0).toFloat(),
            im.optDouble("h", 200.0).toFloat(),
        )
    }
    WbModel(
        strokes = strokes,
        shapes = shapes,
        images = images,
        texts = texts,
        panX = o.optDouble("px", 0.0).toFloat(),
        panY = o.optDouble("py", 0.0).toFloat(),
        scale = o.optDouble("sc", 1.0).toFloat().coerceIn(WB_MIN_SCALE, WB_MAX_SCALE),
    )
}.getOrDefault(emptyWb())

private fun serializeWb(m: WbModel): String {
    val o = JSONObject()
    o.put("px", m.panX.toDouble())
    o.put("py", m.panY.toDouble())
    o.put("sc", m.scale.toDouble())
    val sArr = JSONArray()
    m.strokes.forEach { stroke ->
        val pts = JSONArray()
        stroke.points.forEach { p -> pts.put(JSONArray().put(p.x.toDouble()).put(p.y.toDouble())) }
        sArr.put(JSONObject().put("c", stroke.color).put("w", stroke.width.toDouble()).put("p", pts))
    }
    o.put("s", sArr)
    val shArr = JSONArray()
    m.shapes.forEach { sh ->
        shArr.put(
            JSONObject()
                .put("k", sh.type.name)
                .put("sx", sh.start.x.toDouble()).put("sy", sh.start.y.toDouble())
                .put("ex", sh.end.x.toDouble()).put("ey", sh.end.y.toDouble())
                .put("c", sh.color).put("w", sh.width.toDouble()),
        )
    }
    o.put("sh", shArr)
    val imArr = JSONArray()
    m.images.forEach { im ->
        imArr.put(
            JSONObject()
                .put("id", im.id).put("a", im.attachment)
                .put("x", im.x.toDouble()).put("y", im.y.toDouble())
                .put("w", im.width.toDouble()).put("h", im.height.toDouble()),
        )
    }
    o.put("im", imArr)
    // Embed standard attachment tokens so the app's attachment tracker (AttachmentMarkup.fileNames)
    // discovers these files for cleanup-on-delete and export bundling.
    if (m.images.isNotEmpty()) {
        val att = JSONArray()
        m.images.forEach { att.put(AttachmentMarkup.imageToken(it.attachment)) }
        o.put("att", att)
    }
    val tArr = JSONArray()
    m.texts.forEach { t ->
        tArr.put(JSONObject().put("id", t.id).put("x", t.x.toDouble()).put("y", t.y.toDouble()).put("t", t.text).put("c", t.colorArgb))
    }
    o.put("t", tArr)
    return o.toString()
}

private fun DrawScope.drawWbStroke(points: List<Offset>, color: Color, width: Float) {
    when {
        points.size == 1 -> drawCircle(color, radius = width.dp.toPx() / 2f, center = points[0])
        points.size > 1 -> {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = width.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

private fun DrawScope.drawWbShape(type: WbShapeType, start: Offset, end: Offset, color: Color, width: Float) {
    val w = width.dp.toPx()
    when (type) {
        WbShapeType.LINE -> drawLine(color, start, end, strokeWidth = w, cap = StrokeCap.Round)
        WbShapeType.ARROW -> {
            drawLine(color, start, end, strokeWidth = w, cap = StrokeCap.Round)
            val angle = atan2(end.y - start.y, end.x - start.x)
            val headLen = 18f + width * 1.5f
            val spread = 0.45f
            val a1 = angle + Math.PI.toFloat() - spread
            val a2 = angle + Math.PI.toFloat() + spread
            drawLine(color, end, Offset(end.x + headLen * cos(a1), end.y + headLen * sin(a1)), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(color, end, Offset(end.x + headLen * cos(a2), end.y + headLen * sin(a2)), strokeWidth = w, cap = StrokeCap.Round)
        }
        WbShapeType.RECT -> {
            val topLeft = Offset(min(start.x, end.x), min(start.y, end.y))
            drawRect(color = color, topLeft = topLeft, size = Size(abs(end.x - start.x), abs(end.y - start.y)), style = Stroke(width = w, join = StrokeJoin.Round))
        }
        WbShapeType.ELLIPSE -> {
            val topLeft = Offset(min(start.x, end.x), min(start.y, end.y))
            drawOval(color = color, topLeft = topLeft, size = Size(abs(end.x - start.x), abs(end.y - start.y)), style = Stroke(width = w))
        }
    }
}

/**
 * A pan/zoomable "whiteboard" note: freehand scribbling is the default, and the user can drop
 * draggable text notes and move around the infinite canvas. The strokes and the text notes share
 * one graphics-layer transform, so everything pans and zooms together (a text note keeps its place
 * and scales with the drawing).
 */
@Composable
internal fun ScribbleEditor(
    seedKey: String,
    title: String,
    content: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    meta: @Composable () -> Unit = {},
) {
    val neu = LocalNeuColors.current
    // Parse off the main thread so opening a whiteboard with thousands of strokes never blocks the
    // screen-open animation; the board starts empty and fills in a frame or two later.
    var model by remember { mutableStateOf(emptyWb()) }
    LaunchedEffect(seedKey) { model = withContext(Dispatchers.Default) { parseWb(content) } }
    var mode by remember { mutableStateOf(WbMode.DRAW) }
    var penWidth by remember { mutableStateOf(4f) }
    var penColor by remember { mutableStateOf(0) }
    val livePoints = remember { mutableStateListOf<Offset>() }
    var shapeType by remember { mutableStateOf(WbShapeType.RECT) }
    var liveShapeStart by remember { mutableStateOf<Offset?>(null) }
    var liveShapeEnd by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val defaultStrokeColor = MaterialTheme.colorScheme.onSurface
    val liveColor = if (penColor == 0) defaultStrokeColor else Color(penColor)
    val readOnly = LocalReadOnly.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The board image currently open in the crop editor (null = none).
    var cropImageId by remember { mutableStateOf<String?>(null) }

    fun update(m: WbModel) {
        model = m
        onContentChange(serializeWb(m))
    }

    fun zoomBy(factor: Float) {
        val cw = canvasSize.width.toFloat()
        val ch = canvasSize.height.toFloat()
        if (cw <= 0f || ch <= 0f) {
            update(model.copy(scale = (model.scale * factor).coerceIn(WB_MIN_SCALE, WB_MAX_SCALE)))
            return
        }
        val cx = cw / 2f
        val cy = ch / 2f
        val newScale = (model.scale * factor).coerceIn(WB_MIN_SCALE, WB_MAX_SCALE)
        val canvasCx = (cx - model.panX) / model.scale
        val canvasCy = (cy - model.panY) / model.scale
        update(model.copy(scale = newScale, panX = cx - canvasCx * newScale, panY = cy - canvasCy * newScale))
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val name = withContext(Dispatchers.IO) { AttachmentStore.importFromUri(context, uri) }
                if (name != null) {
                    // Size the new image to its natural aspect ratio (no stretching / letterboxing),
                    // and drop it centred in the current view.
                    val aspect = withContext(Dispatchers.IO) { imageAspectRatio(context, name) }
                    val cw = canvasSize.width.toFloat()
                    val ch = canvasSize.height.toFloat()
                    val cx = if (cw > 0f) (cw / 2f - model.panX) / model.scale else 0f
                    val cy = if (ch > 0f) (ch / 2f - model.panY) / model.scale else 0f
                    val w = 300f
                    val h = if (aspect != null && aspect > 0f) (w / aspect).coerceIn(80f, 1400f) else 200f
                    update(model.copy(images = model.images + WbImage(UUID.randomUUID().toString(), name, cx - w / 2f, cy - h / 2f, w, h)))
                }
            }
        }
    }
    fun addImage() {
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // On-device handwriting → text: recognise the board's freehand strokes with ML Kit and drop the
    // result as a text note at the centre of the current view. Nothing is uploaded.
    fun recognizeBoardToText() {
        if (model.strokes.isEmpty()) return
        android.widget.Toast.makeText(context, "Reading handwriting…", android.widget.Toast.LENGTH_SHORT).show()
        scope.launch {
            val text = HandwritingRecognizer.recognize(model.strokes.map { it.points })
            if (text.isBlank()) {
                android.widget.Toast.makeText(context, "Couldn't read the handwriting", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val cw = canvasSize.width.toFloat()
            val ch = canvasSize.height.toFloat()
            val cx = if (cw > 0f) (cw / 2f - model.panX) / model.scale else 0f
            val cy = if (ch > 0f) (ch / 2f - model.panY) / model.scale else 0f
            update(model.copy(texts = model.texts + WbText(UUID.randomUUID().toString(), cx - 60f, cy, text)))
            android.widget.Toast.makeText(context, "Added as a text note", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = title,
            onValueChange = onTitleChange,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            readOnly = readOnly,
            decorationBox = { inner ->
                if (title.isEmpty()) {
                    Text(
                        text = "Board",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
                inner()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 20.dp)) { meta() }
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .neumorphicRaised(18.dp, neu, elevation = 4.dp)
                .background(MaterialTheme.colorScheme.surface)
                .onSizeChanged { canvasSize = it }
                .clipToBounds(),
        ) {
            val gestureModifier = when (if (readOnly) WbMode.MOVE else mode) {
                WbMode.DRAW -> Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        fun toCanvas(p: Offset) = Offset((p.x - model.panX) / model.scale, (p.y - model.panY) / model.scale)
                        livePoints.clear()
                        livePoints.add(toCanvas(down.position))
                        var active = true
                        while (active) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                active = false
                            } else {
                                livePoints.add(toCanvas(change.position))
                                change.consume()
                            }
                        }
                        if (livePoints.isNotEmpty()) {
                            update(model.copy(strokes = model.strokes + WbStroke(livePoints.toList(), penColor, penWidth)))
                        }
                        livePoints.clear()
                    }
                }

                WbMode.SHAPE -> Modifier.pointerInput(shapeType) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        fun toCanvas(p: Offset) = Offset((p.x - model.panX) / model.scale, (p.y - model.panY) / model.scale)
                        val start = toCanvas(down.position)
                        liveShapeStart = start
                        liveShapeEnd = start
                        var active = true
                        while (active) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                active = false
                            } else {
                                liveShapeEnd = toCanvas(change.position)
                                change.consume()
                            }
                        }
                        val s = liveShapeStart
                        val e = liveShapeEnd
                        if (s != null && e != null && (abs(e.x - s.x) > 2f || abs(e.y - s.y) > 2f)) {
                            update(model.copy(shapes = model.shapes + WbShape(shapeType, s, e, penColor, penWidth)))
                        }
                        liveShapeStart = null
                        liveShapeEnd = null
                    }
                }

                WbMode.MOVE -> Modifier.pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // Zoom around the pinch point (the two-finger centroid), not the board's
                        // centre, so the spot under the user's fingers stays put while scaling - and
                        // still translate by the pan. This is what makes zoom feel "where I want".
                        val newScale = (model.scale * zoom).coerceIn(WB_MIN_SCALE, WB_MAX_SCALE)
                        val effZoom = if (model.scale != 0f) newScale / model.scale else 1f
                        val newPanX = centroid.x - (centroid.x - pan.x - model.panX) * effZoom
                        val newPanY = centroid.y - (centroid.y - pan.y - model.panY) * effZoom
                        update(model.copy(panX = newPanX, panY = newPanY, scale = newScale))
                    }
                }

                WbMode.TEXT -> Modifier.pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val cx = (pos.x - model.panX) / model.scale
                        val cy = (pos.y - model.panY) / model.scale
                        update(model.copy(texts = model.texts + WbText(UUID.randomUUID().toString(), cx, cy, "")))
                        mode = WbMode.MOVE
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
                // Strokes + text notes share this transform, so they pan/zoom together.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = model.panX
                            translationY = model.panY
                            scaleX = model.scale
                            scaleY = model.scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                ) {
                    // Committed strokes only redraw when a stroke is FINISHED - not on every
                    // pointer move - so drawing on a whiteboard with thousands of strokes stays smooth.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        model.strokes.forEach { stroke ->
                            val c = if (stroke.color == 0) defaultStrokeColor else Color(stroke.color)
                            drawWbStroke(stroke.points, c, stroke.width)
                        }
                        model.shapes.forEach { shape ->
                            val c = if (shape.color == 0) defaultStrokeColor else Color(shape.color)
                            drawWbShape(shape.type, shape.start, shape.end, c, shape.width)
                        }
                    }
                    // The in-progress stroke lives on its own overlay: each new point only repaints
                    // this single stroke, never the whole board.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (livePoints.isNotEmpty()) drawWbStroke(livePoints.toList(), liveColor, penWidth)
                        val ls = liveShapeStart
                        val le = liveShapeEnd
                        if (ls != null && le != null) drawWbShape(shapeType, ls, le, liveColor, penWidth)
                    }
                    model.images.forEach { img ->
                        key(img.id) {
                            WbImageNode(
                                image = img,
                                boardScale = model.scale,
                                onMove = { dx, dy ->
                                    update(model.copy(images = model.images.map { if (it.id == img.id) it.copy(x = it.x + dx, y = it.y + dy) else it }))
                                },
                                onResize = { dw, dh ->
                                    // Resize proportionally (keep the image's aspect ratio) so it never distorts;
                                    // follow whichever drag axis moved more so the corner feels natural.
                                    update(
                                        model.copy(
                                            images = model.images.map {
                                                if (it.id == img.id) {
                                                    val aspect = if (it.height != 0f) it.width / it.height else 1f
                                                    val delta = if (abs(dw) >= abs(dh)) dw else dh
                                                    val newW = (it.width + delta).coerceIn(60f, 4000f)
                                                    it.copy(width = newW, height = (newW / aspect).coerceAtLeast(60f))
                                                } else it
                                            },
                                        ),
                                    )
                                },
                                onCrop = { cropImageId = img.id },
                                onDelete = {
                                    update(model.copy(images = model.images.filterNot { it.id == img.id }))
                                },
                            )
                        }
                    }
                    model.texts.forEach { textNote ->
                        key(textNote.id) {
                            WbTextNote(
                                note = textNote,
                                onMove = { dx, dy ->
                                    update(
                                        model.copy(
                                            texts = model.texts.map {
                                                if (it.id == textNote.id) it.copy(x = it.x + dx, y = it.y + dy) else it
                                            },
                                        ),
                                    )
                                },
                                onText = { value ->
                                    update(
                                        model.copy(
                                            texts = model.texts.map { if (it.id == textNote.id) it.copy(text = value) else it },
                                        ),
                                    )
                                },
                                onColor = {
                                    val cur = wbNoteColors.indexOf(textNote.colorArgb).coerceAtLeast(0)
                                    val next = wbNoteColors[(cur + 1) % wbNoteColors.size]
                                    update(
                                        model.copy(
                                            texts = model.texts.map { if (it.id == textNote.id) it.copy(colorArgb = next) else it },
                                        ),
                                    )
                                },
                                onDelete = { update(model.copy(texts = model.texts.filterNot { it.id == textNote.id })) },
                            )
                        }
                    }
                }
            }

            if (model.strokes.isEmpty() && model.shapes.isEmpty() && model.images.isEmpty() && model.texts.isEmpty() && livePoints.isEmpty()) {
                Text(
                    text = "Draw anywhere with your finger or stylus. Switch tools below to add shapes, pan, zoom, or drop a text note.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }

            if (!readOnly) {
                WbToolbar(
                    mode = mode,
                    penColor = penColor,
                    penWidth = penWidth,
                    shapeType = shapeType,
                    onMode = { mode = it },
                    onPenWidth = { penWidth = it; mode = WbMode.DRAW },
                    onPenColor = { penColor = it; mode = WbMode.DRAW },
                    onPickShape = { shapeType = it; mode = WbMode.SHAPE },
                    onAddImage = { addImage() },
                    onRecognize = { recognizeBoardToText() },
                    onZoomIn = { zoomBy(1.25f) },
                    onZoomOut = { zoomBy(0.8f) },
                    onUndo = {
                        when {
                            model.strokes.isNotEmpty() -> update(model.copy(strokes = model.strokes.dropLast(1)))
                            model.shapes.isNotEmpty() -> update(model.copy(shapes = model.shapes.dropLast(1)))
                        }
                    },
                    onClear = {
                        if (model.strokes.isNotEmpty() || model.shapes.isNotEmpty() || model.images.isNotEmpty() || model.texts.isNotEmpty()) {
                            update(model.copy(strokes = emptyList(), shapes = emptyList(), images = emptyList(), texts = emptyList()))
                        }
                    },
                    onResetView = { update(model.copy(panX = 0f, panY = 0f, scale = 1f)) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // Crop an existing board image (reuses the note editor's freeform crop). Swaps in the cropped
    // copy, re-fits the frame to the new aspect ratio, and removes the old encrypted file.
    val cropTarget = cropImageId?.let { id -> model.images.firstOrNull { it.id == id } }
    if (cropTarget != null && !readOnly) {
        ImageCropDialog(
            name = cropTarget.attachment,
            onCropped = { newName ->
                val targetId = cropTarget.id
                cropImageId = null
                scope.launch {
                    val aspect = withContext(Dispatchers.IO) { imageAspectRatio(context, newName) }
                    update(
                        model.copy(
                            images = model.images.map {
                                if (it.id == targetId) {
                                    val newH = if (aspect != null && aspect > 0f) (it.width / aspect).coerceAtLeast(60f) else it.height
                                    it.copy(attachment = newName, height = newH)
                                } else it
                            },
                        ),
                    )
                }
            },
            onDismiss = { cropImageId = null },
        )
    }
}

@Composable
private fun WbImageNode(
    image: WbImage,
    boardScale: Float,
    onMove: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onCrop: () -> Unit,
    onDelete: () -> Unit,
) {
    val readOnly = LocalReadOnly.current
    val density = LocalDensity.current
    // This node lives INSIDE the zoomed board layer, so a fixed dp would shrink/grow with the zoom.
    // Divide handle metrics by the board scale to keep them a constant, easy-to-grab on-screen size.
    val s = boardScale.coerceIn(WB_MIN_SCALE, WB_MAX_SCALE)
    val handleSize = 30.dp / s
    val handlePad = 5.dp / s
    val iconSize = 16.dp / s
    Box(
        modifier = Modifier
            .offset { IntOffset(image.x.roundToInt(), image.y.roundToInt()) }
            .size(with(density) { image.width.toDp() }, with(density) { image.height.toDp() })
            .clip(RoundedCornerShape(10.dp / s))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        AsyncImage(
            model = EncAttachment(image.attachment),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (!readOnly) {
            // Move (top-left)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(handlePad)
                    .size(handleSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onMove(drag.x, drag.y)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.DragIndicator, contentDescription = "Move image", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(iconSize))
            }
            // Delete (top-right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(handlePad)
                    .size(handleSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Delete image", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(iconSize))
            }
            // Crop (bottom-left)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(handlePad)
                    .size(handleSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .clickable(onClick = onCrop),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Crop, contentDescription = "Crop image", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize))
            }
            // Resize (bottom-right, aspect-locked)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(handlePad)
                    .size(handleSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onResize(drag.x, drag.y)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.OpenInFull, contentDescription = "Resize image", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(iconSize))
            }
        }
    }
}

@Composable
private fun WbTextNote(
    note: WbText,
    onMove: (Float, Float) -> Unit,
    onText: (String) -> Unit,
    onColor: () -> Unit,
    onDelete: () -> Unit,
) {
    val neu = LocalNeuColors.current
    val readOnly = LocalReadOnly.current
    val colored = note.colorArgb != 0
    val bg = if (colored) Color(note.colorArgb) else MaterialTheme.colorScheme.surface
    // Pastel sticky notes are always light, so use a dark ink on them regardless of theme.
    val fg = if (colored) Color(0xFF1E1E24) else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .offset { IntOffset(note.x.roundToInt(), note.y.roundToInt()) }
            .widthIn(max = 220.dp)
            .neumorphicRaised(12.dp, neu, elevation = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .then(
                    if (readOnly) Modifier
                    else Modifier.pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onMove(drag.x, drag.y)
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.DragIndicator,
                contentDescription = "Move note",
                tint = fg.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
        if (!readOnly) {
            // A tap cycles the sticky-note colour.
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (colored) Color(note.colorArgb) else MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, fg.copy(alpha = 0.35f), CircleShape)
                    .clickable(onClick = onColor),
            )
            Spacer(Modifier.width(4.dp))
        }
        BasicTextField(
            value = note.text,
            onValueChange = onText,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = fg),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            readOnly = readOnly,
            decorationBox = { inner ->
                if (note.text.isEmpty()) {
                    Text(
                        text = "Note",
                        style = MaterialTheme.typography.bodyMedium,
                        color = fg.copy(alpha = 0.4f),
                    )
                }
                inner()
            },
            modifier = Modifier
                .widthIn(min = 40.dp, max = 150.dp)
                .padding(horizontal = 6.dp),
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(enabled = !readOnly, onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Delete note",
                tint = fg.copy(alpha = 0.6f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun WbToolbar(
    mode: WbMode,
    penColor: Int,
    penWidth: Float,
    shapeType: WbShapeType,
    onMode: (WbMode) -> Unit,
    onPenWidth: (Float) -> Unit,
    onPenColor: (Int) -> Unit,
    onPickShape: (WbShapeType) -> Unit,
    onAddImage: () -> Unit,
    onRecognize: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onResetView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val neu = LocalNeuColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .neumorphicRaised(24.dp, neu, elevation = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        WbPenButton(
            selected = mode == WbMode.DRAW,
            penWidth = penWidth,
            onSelect = { onMode(WbMode.DRAW) },
            onPickWidth = onPenWidth,
        )
        WbColorButton(penColor = penColor, onPick = onPenColor)
        WbShapeButton(
            selected = mode == WbMode.SHAPE,
            shapeType = shapeType,
            onSelect = { onMode(WbMode.SHAPE) },
            onPick = onPickShape,
        )
        WbToolButton(Icons.Rounded.PanTool, "Move", selected = mode == WbMode.MOVE) { onMode(WbMode.MOVE) }
        WbToolButton(Icons.Rounded.TextFields, "Add text", selected = mode == WbMode.TEXT) { onMode(WbMode.TEXT) }
        WbToolButton(Icons.Rounded.AddPhotoAlternate, "Add image", selected = false, onClick = onAddImage)
        WbToolButton(Icons.Rounded.Spellcheck, "Handwriting to text", selected = false, onClick = onRecognize)
        WbDivider()
        WbToolButton(Icons.Rounded.Remove, "Zoom out", selected = false, onClick = onZoomOut)
        WbToolButton(Icons.Rounded.Add, "Zoom in", selected = false, onClick = onZoomIn)
        WbToolButton(Icons.Rounded.CenterFocusStrong, "Reset view", selected = false, onClick = onResetView)
        WbDivider()
        WbToolButton(Icons.Rounded.Undo, "Undo", selected = false, onClick = onUndo)
        WbToolButton(Icons.Rounded.DeleteSweep, "Clear", selected = false, onClick = onClear)
    }
}

private val wbShapeOptions = listOf(
    Triple(WbShapeType.LINE, Icons.Rounded.HorizontalRule, "Line"),
    Triple(WbShapeType.ARROW, Icons.Rounded.ArrowRightAlt, "Arrow"),
    Triple(WbShapeType.RECT, Icons.Rounded.Rectangle, "Rectangle"),
    Triple(WbShapeType.ELLIPSE, Icons.Rounded.Circle, "Ellipse"),
)

private fun shapeIcon(type: WbShapeType): ImageVector =
    wbShapeOptions.firstOrNull { it.first == type }?.second ?: Icons.Rounded.Rectangle

/** Shapes tool: tap to draw the current shape, long-press to pick line / arrow / rectangle / ellipse. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WbShapeButton(
    selected: Boolean,
    shapeType: WbShapeType,
    onSelect: () -> Unit,
    onPick: (WbShapeType) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSelect,
                    onLongClick = { menu = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                shapeIcon(shapeType),
                contentDescription = "Shapes",
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            wbShapeOptions.forEach { (type, icon, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    trailingIcon = {
                        if (type == shapeType) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = {
                        menu = false
                        onPick(type)
                    },
                )
            }
        }
    }
}

/** Draw tool: tap to draw, long-press to choose the pen thickness. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WbPenButton(
    selected: Boolean,
    penWidth: Float,
    onSelect: () -> Unit,
    onPickWidth: (Float) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSelect,
                    onLongClick = { menu = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Draw,
                contentDescription = "Draw",
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            penPresets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(preset.width.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.onSurface),
                        )
                    },
                    trailingIcon = {
                        if (preset.width == penWidth) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = {
                        menu = false
                        onPickWidth(preset.width)
                    },
                )
            }
        }
    }
}

/** Pen colour: shows the current colour and opens a swatch row. */
@Composable
private fun WbColorButton(penColor: Int, onPick: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val current = if (penColor == 0) MaterialTheme.colorScheme.onSurface else Color(penColor)
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(current)
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                penColors.forEach { c ->
                    val swatch = if (c == 0) MaterialTheme.colorScheme.onSurface else Color(c)
                    val isSelected = c == penColor
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .clickable {
                                menu = false
                                onPick(c)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun WbDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .height(24.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
    )
}

@Composable
private fun WbToolButton(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}
