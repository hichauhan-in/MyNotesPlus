package com.example.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.attachments.AttachmentStore
import com.example.data.attachments.EncAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Corner { TL, TR, BL, BR }

/**
 * A full-screen freeform image cropper. The user drags the crop rectangle's corners (or the
 * whole rectangle) over the image; confirming writes the cropped region to a new private file.
 */
@Composable
internal fun ImageCropDialog(
    name: String,
    onCropped: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bytes by remember(name) { mutableStateOf<ByteArray?>(null) }
    var loadFailed by remember(name) { mutableStateOf(false) }
    LaunchedEffect(name) {
        val decoded = withContext(Dispatchers.IO) { AttachmentStore.readDecrypted(context, name) }
        bytes = decoded
        loadFailed = decoded == null
    }
    val imageSize = remember(bytes) { bytes?.let { orientedImageSize(it) } }
    var working by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0F)),
        ) {
            val data = bytes
            if (data == null && !loadFailed) {
                // Still decrypting - brief.
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.Center),
                )
                CropIconButton(Icons.Rounded.Close, "Close", Modifier.align(Alignment.TopStart).padding(12.dp), onDismiss)
                return@Box
            }
            if (data == null || imageSize == null) {
                Text(
                    text = "Couldn't open this image.",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                CropIconButton(Icons.Rounded.Close, "Close", Modifier.align(Alignment.TopStart).padding(12.dp), onDismiss)
                return@Box
            }

            val (imgW, imgH) = imageSize
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val topReserve = with(density) { 72.dp.toPx() }
                val bottomReserve = with(density) { 112.dp.toPx() }
                val areaH = (boxH - topReserve - bottomReserve).coerceAtLeast(1f)

                val scale = min(boxW / imgW, areaH / imgH)
                val dispW = imgW * scale
                val dispH = imgH * scale
                val imgLeft = (boxW - dispW) / 2f
                val imgTop = topReserve + (areaH - dispH) / 2f
                val bounds = Rect(imgLeft, imgTop, imgLeft + dispW, imgTop + dispH)
                val minSize = with(density) { 56.dp.toPx() }

                var crop by remember(imgW, imgH, boxW, boxH) { mutableStateOf(bounds) }

                // The image itself, fit into its computed rect.
                Box(
                    modifier = Modifier
                        .offset { IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()) }
                        .size(with(density) { dispW.toDp() }, with(density) { dispH.toDp() }),
                ) {
                    AsyncImage(
                        model = EncAttachment(name),
                        contentDescription = "Image to crop",
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Dim outside the crop rect + crop border + rule-of-thirds grid.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scrim = Color(0xB3000000)
                    drawRect(scrim, Offset(0f, 0f), Size(this.size.width, crop.top))
                    drawRect(scrim, Offset(0f, crop.bottom), Size(this.size.width, this.size.height - crop.bottom))
                    drawRect(scrim, Offset(0f, crop.top), Size(crop.left, crop.height))
                    drawRect(scrim, Offset(crop.right, crop.top), Size(this.size.width - crop.right, crop.height))
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(crop.left, crop.top),
                        size = Size(crop.width, crop.height),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    val gridColor = Color.White.copy(alpha = 0.35f)
                    val tw = crop.width / 3f
                    val th = crop.height / 3f
                    for (i in 1..2) {
                        drawLine(gridColor, Offset(crop.left + tw * i, crop.top), Offset(crop.left + tw * i, crop.bottom), 1.dp.toPx())
                        drawLine(gridColor, Offset(crop.left, crop.top + th * i), Offset(crop.right, crop.top + th * i), 1.dp.toPx())
                    }
                }

                // Drag the whole rectangle to reposition it.
                Box(
                    modifier = Modifier
                        .offset { IntOffset(crop.left.roundToInt(), crop.top.roundToInt()) }
                        .size(with(density) { crop.width.toDp() }, with(density) { crop.height.toDp() })
                        .pointerInput(bounds) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                crop = moveRect(crop, drag.x, drag.y, bounds)
                            }
                        },
                )

                // Corner handles.
                Corner.entries.forEach { corner ->
                    CropCornerHandle(
                        crop = crop,
                        corner = corner,
                        bounds = bounds,
                        minSize = minSize,
                        onChange = { crop = it },
                    )
                }

                // Top bar: cancel.
                CropIconButton(
                    icon = Icons.Rounded.Close,
                    description = "Cancel",
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    onClick = onDismiss,
                )

                // Bottom bar: reset + crop.
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { crop = bounds }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reset", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(enabled = !working) {
                                working = true
                                val request = crop
                                scope.launch {
                                    val name = withContext(Dispatchers.IO) {
                                        cropToFile(context, data, request, bounds)
                                    }
                                    working = false
                                    if (name != null) onCropped(name) else onDismiss()
                                }
                            }
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                    ) {
                        if (working) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Crop",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CropCornerHandle(
    crop: Rect,
    corner: Corner,
    bounds: Rect,
    minSize: Float,
    onChange: (Rect) -> Unit,
) {
    val density = LocalDensity.current
    val handlePx = with(density) { 30.dp.toPx() }
    val current by rememberUpdatedState(crop)
    val cx = if (corner == Corner.TL || corner == Corner.BL) crop.left else crop.right
    val cy = if (corner == Corner.TL || corner == Corner.TR) crop.top else crop.bottom
    Box(
        modifier = Modifier
            .offset { IntOffset((cx - handlePx / 2f).roundToInt(), (cy - handlePx / 2f).roundToInt()) }
            .size(with(density) { handlePx.toDp() })
            .pointerInput(corner, bounds) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onChange(resizeRect(current, corner, drag.x, drag.y, bounds, minSize))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun CropIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

private fun moveRect(r: Rect, dx: Float, dy: Float, bounds: Rect): Rect {
    val left = (r.left + dx).coerceIn(bounds.left, bounds.right - r.width)
    val top = (r.top + dy).coerceIn(bounds.top, bounds.bottom - r.height)
    return Rect(left, top, left + r.width, top + r.height)
}

private fun resizeRect(r: Rect, corner: Corner, dx: Float, dy: Float, bounds: Rect, minSize: Float): Rect {
    var left = r.left
    var top = r.top
    var right = r.right
    var bottom = r.bottom
    when (corner) {
        Corner.TL -> {
            left = (left + dx).coerceIn(bounds.left, right - minSize)
            top = (top + dy).coerceIn(bounds.top, bottom - minSize)
        }
        Corner.TR -> {
            right = (right + dx).coerceIn(left + minSize, bounds.right)
            top = (top + dy).coerceIn(bounds.top, bottom - minSize)
        }
        Corner.BL -> {
            left = (left + dx).coerceIn(bounds.left, right - minSize)
            bottom = (bottom + dy).coerceIn(top + minSize, bounds.bottom)
        }
        Corner.BR -> {
            right = (right + dx).coerceIn(left + minSize, bounds.right)
            bottom = (bottom + dy).coerceIn(top + minSize, bounds.bottom)
        }
    }
    return Rect(left, top, right, bottom)
}

/** Crops [data] to the region described by [crop] (in the same coordinate space as [bounds]). */
private fun cropToFile(context: android.content.Context, data: ByteArray, crop: Rect, bounds: Rect): String? {
    val bmp = decodeOrientedBitmap(data, 4096) ?: return null
    return try {
        val fl = ((crop.left - bounds.left) / bounds.width).coerceIn(0f, 1f)
        val ft = ((crop.top - bounds.top) / bounds.height).coerceIn(0f, 1f)
        val fr = ((crop.right - bounds.left) / bounds.width).coerceIn(0f, 1f)
        val fb = ((crop.bottom - bounds.top) / bounds.height).coerceIn(0f, 1f)
        val x = (fl * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
        val y = (ft * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
        val w = ((fr - fl) * bmp.width).roundToInt().coerceIn(1, bmp.width - x)
        val h = ((fb - ft) * bmp.height).roundToInt().coerceIn(1, bmp.height - y)
        val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
        val name = AttachmentStore.saveBitmap(context, cropped)
        if (cropped != bmp) cropped.recycle()
        name
    } finally {
        bmp.recycle()
    }
}

/** Image size in display (EXIF-upright) orientation, matching how Coil renders it. */
private fun orientedImageSize(data: ByteArray): Pair<Int, Int>? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
    val rotation = exifRotation(data)
    return if (rotation == 90 || rotation == 270) opts.outHeight to opts.outWidth
    else opts.outWidth to opts.outHeight
}

private fun decodeOrientedBitmap(data: ByteArray, maxDim: Int): Bitmap? = runCatching {
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, boundsOpts)
    val w = boundsOpts.outWidth
    val h = boundsOpts.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (w / sample > maxDim || h / sample > maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = BitmapFactory.decodeByteArray(data, 0, data.size, opts) ?: return null
    val rotation = exifRotation(data)
    if (rotation == 0) {
        bmp
    } else {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        if (rotated != bmp) bmp.recycle()
        rotated
    }
}.getOrNull()

private fun exifRotation(data: ByteArray): Int = runCatching {
    val exif = ExifInterface(java.io.ByteArrayInputStream(data))
    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}.getOrDefault(0)
