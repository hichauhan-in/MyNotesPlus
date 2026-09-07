package com.example.data.ml

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The kind of one-tap action a detected entity turns into. */
enum class SmartActionType { REMINDER, CALL, EMAIL, URL, ADDRESS }

/** A single detected, actionable item in a note. */
data class SmartSuggestion(
    val type: SmartActionType,
    /** Chip label shown to the user. */
    val label: String,
    /** The raw value (phone / email / url / address), or the matched text for a reminder. */
    val value: String,
    /** For [SmartActionType.REMINDER]: the epoch-millis fire time. */
    val triggerAt: Long = 0L,
)

/**
 * On-device "smart text": finds dates, phone numbers, emails, links and addresses inside a note
 * (Google ML Kit Entity Extraction) and turns them into one-tap actions. The base detector is
 * bundled in the app; a small language model downloads once from Google. **Note text is processed
 * entirely on-device and is never uploaded.**
 */
object SmartText {

    private val timeFormat = SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault())

    /** Analyses [text] and returns the actionable suggestions found (empty on failure / offline). */
    suspend fun analyze(context: Context, text: String): List<SmartSuggestion> {
        if (text.isBlank() || text.length < 4) return emptyList()
        val extractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build(),
        )
        return try {
            awaitTask(extractor.downloadModelIfNeeded())
            val annotations = awaitTask(
                extractor.annotate(EntityExtractionParams.Builder(text).build()),
            )
            buildSuggestions(annotations)
        } catch (e: Exception) {
            emptyList()
        } finally {
            runCatching { extractor.close() }
        }
    }

    private fun buildSuggestions(annotations: List<EntityAnnotation>): List<SmartSuggestion> {
        val out = mutableListOf<SmartSuggestion>()
        val now = System.currentTimeMillis()
        val seen = HashSet<String>()
        var reminderAdded = false
        for (ann in annotations) {
            val snippet = ann.annotatedText
            for (entity in ann.entities) {
                if (entity is DateTimeEntity) {
                    val ts = entity.timestampMillis
                    // Only the soonest FUTURE date becomes a reminder chip (avoids clutter / past dates).
                    if (!reminderAdded && ts > now + 60_000L) {
                        out.add(SmartSuggestion(SmartActionType.REMINDER, timeFormat.format(ts), snippet, ts))
                        reminderAdded = true
                    }
                    continue
                }
                when (entity.type) {
                    Entity.TYPE_PHONE ->
                        if (seen.add("p:$snippet")) out.add(SmartSuggestion(SmartActionType.CALL, snippet, snippet))
                    Entity.TYPE_EMAIL ->
                        if (seen.add("e:$snippet")) out.add(SmartSuggestion(SmartActionType.EMAIL, snippet, snippet))
                    Entity.TYPE_URL ->
                        if (seen.add("u:$snippet")) out.add(SmartSuggestion(SmartActionType.URL, shortUrl(snippet), snippet))
                    Entity.TYPE_ADDRESS ->
                        if (seen.add("a:$snippet")) out.add(SmartSuggestion(SmartActionType.ADDRESS, "Map", snippet))
                }
            }
        }
        return out.take(6)
    }

    private fun shortUrl(u: String): String =
        u.removePrefix("https://").removePrefix("http://").removePrefix("www.").take(28)

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }
}
