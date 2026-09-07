package com.example.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import com.example.data.attachments.AttachmentStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/**
 * On-device OCR using Google ML Kit's **bundled** Latin text-recognition model. Everything runs
 * locally on the phone - the image and the recognised text never leave the device (no network call,
 * no account, no key). Keeps MyNotes' offline-first, privacy-first promise intact.
 */
object TextExtractor {

    /**
     * Recognises text in an encrypted attachment [name]. Returns the recognised text (may be
     * multi-line) or an empty string when nothing is found / on failure. Safe to call from any
     * coroutine; the heavy work runs off the main thread.
     */
    suspend fun fromAttachment(context: Context, name: String): String {
        val bytes = withContext(Dispatchers.IO) { AttachmentStore.readDecrypted(context, name) } ?: return ""
        val bitmap = withContext(Dispatchers.Default) { decodeCapped(bytes) } ?: return ""
        val rotation = decodeRotation(bytes)
        return recognize(bitmap, rotation)
    }

    private suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    runCatching { recognizer.close() }
                    if (cont.isActive) cont.resume(result.text.trim())
                }
                .addOnFailureListener {
                    runCatching { recognizer.close() }
                    if (cont.isActive) cont.resume("")
                }
            cont.invokeOnCancellation { runCatching { recognizer.close() } }
        }

    /** Decodes the image, down-sampling so the longest side is at most [maxDim] px (memory-safe). */
    private fun decodeCapped(bytes: ByteArray, maxDim: Int = 2560): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull()
    }

    /** EXIF rotation of the image in degrees (0/90/180/270) so sideways photos still read correctly. */
    private fun decodeRotation(bytes: ByteArray): Int = runCatching {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)
}
