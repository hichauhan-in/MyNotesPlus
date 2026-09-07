package com.example.data.ml

import android.content.Context
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device generative AI (Gemini Nano via ML Kit GenAI / Android AICore). **100% local and free** —
 * note text is processed on the device and never uploaded. It only runs on phones that ship AICore +
 * Gemini Nano (recent high-end devices); on everything else these calls degrade gracefully to
 * `false` / `null`, so the UI simply hides the feature.
 */
object AiAssist {

    private fun buildOptions(context: Context): SummarizerOptions =
        SummarizerOptions.builder(context)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .setLongInputAutoTruncationEnabled(true)
            .build()

    /** Whether on-device summarization is usable on this device right now (API 26+ and AICore ready). */
    suspend fun canSummarize(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val summarizer = runCatching { Summarization.getClient(buildOptions(context)) }.getOrNull() ?: return false
        return try {
            awaitFuture(summarizer.checkFeatureStatus()) != FeatureStatus.UNAVAILABLE
        } catch (e: Exception) {
            false
        } finally {
            runCatching { summarizer.close() }
        }
    }

    /** Summarizes [text] into a few bullet points, or null if unavailable / too short / on failure. */
    suspend fun summarize(context: Context, text: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val clean = text.trim()
        if (clean.length < 200) return null
        val summarizer = runCatching { Summarization.getClient(buildOptions(context)) }.getOrNull() ?: return null
        return try {
            if (awaitFuture(summarizer.checkFeatureStatus()) == FeatureStatus.UNAVAILABLE) return null
            val request = SummarizationRequest.builder(clean).build()
            // runInference (no callback) returns a future; the model auto-downloads on first use.
            val result = withContext(Dispatchers.IO) { summarizer.runInference(request).get() }
            result.summary?.trim()?.ifBlank { null }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { summarizer.close() }
        }
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }

    private suspend fun <T> awaitFuture(future: com.google.common.util.concurrent.ListenableFuture<T>): T =
        suspendCancellableCoroutine { cont ->
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                { runnable -> runnable.run() },
            )
        }
}
