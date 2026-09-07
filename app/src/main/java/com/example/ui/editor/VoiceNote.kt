package com.example.ui.editor

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.data.attachments.AttachmentStore
import com.example.ui.components.BrandGradientButton
import com.example.ui.theme.LocalNeuColors
import com.example.ui.theme.brandGradientHorizontal
import com.example.ui.theme.neumorphicRaised
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

private fun createRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
    else @Suppress("DEPRECATION") MediaRecorder()

/** Feeds MediaPlayer from decrypted bytes held in memory, so no plaintext audio touches disk. */
private class BytesMediaDataSource(private val data: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= data.size) return -1
        val len = minOf(size, data.size - position.toInt())
        System.arraycopy(data, position.toInt(), buffer, offset, len)
        return len
    }

    override fun getSize(): Long = data.size.toLong()

    override fun close() {}
}

private fun formatClock(totalSeconds: Int): String =
    "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"

/**
 * Bottom sheet that records a voice note straight into the note's private attachments
 * directory. The microphone permission must already be granted before it is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceRecorderSheet(
    onSave: (fileName: String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val file = remember { AttachmentStore.newAudioFile(context) }
    val recorder = remember { createRecorder(context) }
    var elapsed by remember { mutableStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var stopped by remember { mutableStateOf(false) }
    var keepFile by remember { mutableStateOf(false) }

    fun stopRecorder(): Boolean {
        if (stopped) return false
        stopped = true
        return runCatching { recorder.stop() }.isSuccess
    }

    fun discard() {
        stopRecorder()
        file.delete()
        onCancel()
    }

    DisposableEffect(Unit) {
        val ok = runCatching {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            true
        }.getOrDefault(false)
        recording = ok
        onDispose {
            if (!stopped) runCatching { recorder.stop() }
            runCatching { recorder.release() }
            if (!keepFile) file.delete()
        }
    }

    LaunchedEffect(recording) {
        if (recording) {
            while (isActive) {
                delay(1_000)
                elapsed++
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { discard() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Voice note",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(brandGradientHorizontal()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = formatClock(elapsed),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (recording) "Recording…" else "Couldn't start recording. Check the microphone permission.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (recording) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            BrandGradientButton(
                text = "Save voice note",
                onClick = {
                    val ok = stopRecorder()
                    if (ok && file.length() > 0L) { keepFile = true; onSave(file.name) } else discard()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Discard",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { discard() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** An inline, playable voice-note attachment card shown in the editor. */
@Composable
internal fun AudioAttachment(name: String, onRemove: () -> Unit) {
    val context = LocalContext.current
    val neu = LocalNeuColors.current
    var playing by remember(name) { mutableStateOf(false) }
    var ready by remember(name) { mutableStateOf(false) }
    var failed by remember(name) { mutableStateOf(false) }
    var durationMs by remember(name) { mutableStateOf(0) }
    var positionMs by remember(name) { mutableStateOf(0) }
    var seeking by remember(name) { mutableStateOf(false) }
    var speed by remember(name) { mutableStateOf(1f) }
    var speedMenu by remember { mutableStateOf(false) }
    val player = remember(name) { MediaPlayer() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    val focusListener = remember(player) {
        android.media.AudioManager.OnAudioFocusChangeListener { change ->
            if (change <= android.media.AudioManager.AUDIOFOCUS_LOSS) {
                runCatching { if (player.isPlaying) player.pause() }
                playing = false
            }
        }
    }
    val readOnly = LocalReadOnly.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle

    @Suppress("DEPRECATION")
    fun pause() {
        runCatching { if (player.isPlaying) player.pause() }
        playing = false
        audioManager.abandonAudioFocus(focusListener)
    }

    LaunchedEffect(name) {
        val bytes = withContext(Dispatchers.IO) { AttachmentStore.readDecrypted(context, name) }
        if (bytes == null) { failed = true; return@LaunchedEffect }
        runCatching {
            player.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            player.setDataSource(BytesMediaDataSource(bytes))
            player.setOnPreparedListener { durationMs = it.duration.coerceAtLeast(0); ready = true }
            player.setOnCompletionListener { positionMs = durationMs; pause() }
            player.setOnErrorListener { _, _, _ -> failed = true; ready = false; pause(); true }
            player.prepareAsync()
        }.onFailure { failed = true; ready = false }
    }

    LaunchedEffect(playing) {
        while (playing) {
            if (!seeking) positionMs = runCatching { player.currentPosition.coerceIn(0, durationMs) }.getOrDefault(positionMs)
            delay(250)
        }
    }

    DisposableEffect(player, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); pause(); runCatching { player.release() } }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicRaised(18.dp, neu, elevation = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = ready && !failed, onClick = {
                if (playing) pause() else runCatching {
                    @Suppress("DEPRECATION")
                    val focus = audioManager.requestAudioFocus(focusListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    if (focus == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        if (positionMs >= durationMs) { player.seekTo(0); positionMs = 0 }
                        player.start()
                        player.playbackParams = android.media.PlaybackParams().setSpeed(speed)
                        playing = true
                    }
                }.onFailure { failed = true; pause() }
            }) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause recording" else "Play recording") }
            Column(Modifier.weight(1f)) {
                Text("Voice note", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(if (failed) "Recording unavailable" else if (!ready) "Loading..." else "${formatClock(positionMs / 1000)} / ${formatClock(durationMs / 1000)}", style = MaterialTheme.typography.bodySmall)
            }
            Box {
                TextButton(enabled = ready && !failed, onClick = { speedMenu = true }) { Text("${speed}x") }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                        DropdownMenuItem(text = { Text("${value}x") }, onClick = {
                            speed = value
                            speedMenu = false
                            if (playing) runCatching { player.playbackParams = android.media.PlaybackParams().setSpeed(value) }
                        })
                    }
                }
            }
            if (!readOnly) IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove voice note") }
        }
        Slider(value = positionMs.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1).toFloat()),
            onValueChange = { seeking = true; positionMs = it.toInt() },
            onValueChangeFinished = { runCatching { player.seekTo(positionMs) }; seeking = false },
            valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(), enabled = ready && !failed,
            modifier = Modifier.fillMaxWidth())
    }
}
