package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.di.AppContainer
import com.example.data.sync.SyncCoordinator
import com.example.ui.lock.AppLockGate
import com.example.ui.navigation.MyNotesNavigation
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.theme.MyNotesTheme
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : FragmentActivity() {
    // A quick-create action sent from a home-screen widget (note / checklist / expense / board).
    private val pendingQuickAction = mutableStateOf<String?>(null)

    // A note id to open, sent from a reminder notification tap.
    private val pendingOpenNoteId = mutableStateOf<String?>(null)

    // True to open the Reminders screen, sent from the reminders widget.
    private val pendingOpenReminders = mutableStateOf(false)

    // Foreground sync loop (immediate + periodic + change-triggered); cancelled when backgrounded.
    private var foregroundSyncJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingQuickAction.value = intent?.getStringExtra(EXTRA_QUICK_ACTION)
        pendingOpenNoteId.value = intent?.getStringExtra(EXTRA_OPEN_NOTE_ID)
        pendingOpenReminders.value = intent?.getBooleanExtra(EXTRA_OPEN_REMINDERS, false) ?: false

        val settingsRepository = AppContainer.settingsRepository!!
        // Read the persisted settings once up front so the theme / lock state is
        // correct on the very first frame (no flash of the wrong theme or content).
        val initialSettings = runBlocking { settingsRepository.settings.first() }

        setContent {
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = initialSettings)

            // Optionally hide the content from the recent-apps preview / screenshots.
            LaunchedEffect(settings.hideFromRecents) {
                if (settings.hideFromRecents) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            MyNotesTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (!settings.onboardingComplete) {
                        OnboardingScreen(
                            onFinish = {
                                AppContainer.applicationScope.launch {
                                    settingsRepository.setOnboardingComplete(true)
                                }
                            },
                        )
                    } else {
                        AppLockGate(enabled = settings.appLockEnabled) {
                            MyNotesNavigation(
                                pendingQuickAction = pendingQuickAction.value,
                                onQuickActionHandled = { pendingQuickAction.value = null },
                                pendingOpenNoteId = pendingOpenNoteId.value,
                                onOpenNoteHandled = { pendingOpenNoteId.value = null },
                                pendingOpenReminders = pendingOpenReminders.value,
                                onOpenRemindersHandled = { pendingOpenReminders.value = false },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_QUICK_ACTION)?.let { pendingQuickAction.value = it }
        intent.getStringExtra(EXTRA_OPEN_NOTE_ID)?.let { pendingOpenNoteId.value = it }
        if (intent.getBooleanExtra(EXTRA_OPEN_REMINDERS, false)) pendingOpenReminders.value = true
    }

    override fun onStart() {
        super.onStart()
        // Keep in sync while the app is open: an immediate catch-up, a light periodic poll, and a
        // debounced sync shortly after the user adds/edits/deletes a note or reminder. All no-ops
        // unless Drive is connected and the key is unlocked on this device.
        val settings = AppContainer.settingsRepository ?: return
        val manager = AppContainer.cloudSyncManager ?: return
        val notes = AppContainer.noteRepository
        val reminders = AppContainer.reminderRepository
        val changes: Flow<Any?> = when {
            notes != null && reminders != null -> merge(notes.changeSignal(), reminders.changeSignal())
            notes != null -> notes.changeSignal()
            reminders != null -> reminders.changeSignal()
            else -> emptyFlow()
        }
        foregroundSyncJob = SyncCoordinator.startForegroundSync(
            this, settings, manager, changes, AppContainer.applicationScope,
        )
    }

    override fun onStop() {
        super.onStop()
        // Stop the foreground sync loop; it resumes on the next onStart.
        foregroundSyncJob?.cancel()
        foregroundSyncJob = null
        // Refresh the home-screen widgets so the note count / backup status stay current.
        WidgetUpdater.refreshAll(this)
    }

    companion object {
        /** Intent extra a widget sets to open the editor with a given note type. */
        const val EXTRA_QUICK_ACTION = "com.example.QUICK_ACTION"

        /** Intent extra a reminder notification sets to open a specific note. */
        const val EXTRA_OPEN_NOTE_ID = "com.example.OPEN_NOTE_ID"

        /** Intent extra the reminders widget sets to open the Reminders screen. */
        const val EXTRA_OPEN_REMINDERS = "com.example.OPEN_REMINDERS"
    }
}
