package com.example.data.ml

import androidx.compose.ui.geometry.Offset
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device handwriting → text (Google ML Kit Digital Ink). Turns freehand strokes (page-ink or
 * board) into typed text. The English model (~20MB) downloads once from Google; the strokes are
 * recognised **locally** and never uploaded.
 */
object HandwritingRecognizer {

    private val model: DigitalInkRecognitionModel? = runCatching {
        DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
            ?.let { DigitalInkRecognitionModel.builder(it).build() }
    }.getOrNull()

    /**
     * Recognises handwriting from [strokes] (each stroke = its list of points, in any consistent
     * coordinate scale). Returns the top candidate text, or "" if there's nothing / on failure.
     */
    suspend fun recognize(strokes: List<List<Offset>>): String {
        val m = model ?: return ""
        if (strokes.none { it.isNotEmpty() }) return ""
        return try {
            val manager = RemoteModelManager.getInstance()
            awaitTask(manager.download(m, DownloadConditions.Builder().build()))
            val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build())
            val inkBuilder = Ink.builder()
            strokes.forEach { pts ->
                if (pts.isNotEmpty()) {
                    val strokeBuilder = Ink.Stroke.builder()
                    pts.forEach { p -> strokeBuilder.addPoint(Ink.Point.create(p.x, p.y)) }
                    inkBuilder.addStroke(strokeBuilder.build())
                }
            }
            try {
                val result = awaitTask(recognizer.recognize(inkBuilder.build()))
                result.candidates.firstOrNull()?.text.orEmpty()
            } finally { recognizer.close() }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }
}
