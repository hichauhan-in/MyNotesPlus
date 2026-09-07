package com.example.data.sync

import android.content.Context
import com.example.data.settings.SettingsRepository
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Drives automatic sync so notes / reminders edited on one device reach the others without the user
 * having to open Settings and tap "Sync now". While the app is in the foreground it runs an
 * immediate catch-up sync, a light periodic poll, and a debounced sync shortly after any local
 * change. Everything no-ops unless Drive is connected and the key is unlocked on this device, and a
 * token is acquired *silently* (no UI when access was already granted).
 */
object SyncCoordinator {

    private val inFlight = AtomicBoolean(false)

    /** Skip an automatic sync if we already synced this recently (avoids spamming Drive). */
    private const val MIN_AUTO_SYNC_INTERVAL_MS = 15_000L

    /** Safety-net poll interval while the app is open (catches changes made on other devices). */
    private const val PERIODIC_INTERVAL_MS = 3 * 60_000L

    /** How long after the last local edit to wait before syncing (lets a burst of edits settle). */
    private const val CHANGE_DEBOUNCE_MS = 4_000L

    /**
     * Starts foreground sync and returns a [Job] the caller cancels when the app leaves the
     * foreground (Activity.onStop). It (1) does an immediate catch-up sync, (2) polls periodically
     * as a safety net, and (3) syncs a few seconds after the user changes anything ([changeSignals]
     * emits on every note / reminder write). Changes made *by* an in-progress sync are ignored so a
     * pull can never loop back into another sync.
     */
    @OptIn(FlowPreview::class)
    fun startForegroundSync(
        context: Context,
        settings: SettingsRepository,
        manager: CloudSyncManager,
        changeSignals: Flow<Any?>,
        scope: CoroutineScope,
    ): Job = scope.launch {
        val appCtx = context.applicationContext
        // 1) Immediate catch-up (throttled so quick re-foregrounding doesn't spam Drive).
        if (System.currentTimeMillis() - settings.snapshot().lastSyncedAt >= MIN_AUTO_SYNC_INTERVAL_MS) {
            runSync(appCtx, settings, manager)
        }
        // 2) Periodic safety-net poll while this device sits open.
        launch {
            while (isActive) {
                delay(PERIODIC_INTERVAL_MS)
                runSync(appCtx, settings, manager)
            }
        }
        // 3) Debounced sync after local edits settle.
        launch {
            changeSignals
                .drop(1) // ignore the initial on-subscribe emission
                .filter { !SyncStatus.syncing.value }
                .debounce(CHANGE_DEBOUNCE_MS)
                .collect { runSync(appCtx, settings, manager) }
        }
    }

    /** User-initiated sync (the Home "+" tap). No throttle; skipped only if a sync is already running. */
    fun manualSync(
        context: Context,
        settings: SettingsRepository,
        manager: CloudSyncManager,
        scope: CoroutineScope,
    ) {
        scope.launch { runSync(context.applicationContext, settings, manager) }
    }

    /** Shared sync body: connectivity/key checks, a silent token, then [CloudSyncManager.syncNow]. */
    private suspend fun runSync(appCtx: Context, settings: SettingsRepository, manager: CloudSyncManager) {
        if (!inFlight.compareAndSet(false, true)) return
        try {
            val snapshot = settings.snapshot()
            if (snapshot.driveAccountEmail == null || !snapshot.recoveryConfigured) return
            if (!manager.hasLocalKey()) return
            val token = silentToken(appCtx) ?: return
            manager.syncNow(token)
        } finally {
            inFlight.set(false)
        }
    }

    /** Gets a Drive access token without any UI, or null if consent would be required / it fails. */
    private suspend fun silentToken(context: Context): String? = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context)
            .authorize(DriveAuth.request())
            .addOnSuccessListener { result ->
                cont.resume(if (!result.hasResolution()) result.accessToken else null)
            }
            .addOnFailureListener { cont.resume(null) }
    }
}
