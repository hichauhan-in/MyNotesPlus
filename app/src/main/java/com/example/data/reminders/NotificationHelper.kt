package com.example.data.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.di.AppContainer
import com.example.domain.model.Reminder

/** Creates the reminders notification channel and posts reminder notifications. */
object NotificationHelper {
    const val CHANNEL_ID = "reminders"
    private const val CHANNEL_NAME = "Reminders"

    fun settingsIntent(context: Context): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
    }

    fun openSettings(context: Context) {
        runCatching { context.startActivity(settingsIntent(context)) }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Note and task reminders"
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    suspend fun notify(context: Context, reminder: Reminder): Boolean {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val hideContent = runCatching { AppContainer.settingsRepository?.snapshot()?.appLockEnabled != false }.getOrDefault(true)

        val tap = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            if (reminder.noteId != null) putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, reminder.noteId)
            else putExtra(MainActivity.EXTRA_OPEN_REMINDERS, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (hideContent) "MyNotes+ reminder" else reminder.title.ifBlank { "Reminder" })
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("MyNotes+ reminder")
                    .build(),
            )
        if (hideContent) {
            builder.setContentText("Unlock MyNotes+ to view")
        } else if (reminder.body.isNotBlank()) {
            builder.setContentText(reminder.body)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
        }
        if (!hideContent) {
            fun actionIntent(action: String): PendingIntent {
                val intent = Intent(context, ReminderActionReceiver::class.java).apply {
                    this.action = action
                    data = android.net.Uri.Builder().scheme("mynotes").authority("reminder-action")
                        .appendPath(reminder.id).appendPath(reminder.effectiveAt.toString()).appendPath(action).build()
                    putExtra(ReminderReceiver.EXTRA_ID, reminder.id)
                    putExtra(ReminderReceiver.EXTRA_AT, reminder.effectiveAt)
                }
                return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            builder.addAction(0, "Done", actionIntent(ReminderActionReceiver.DONE))
            builder.addAction(0, "Snooze 15 min", actionIntent(ReminderActionReceiver.SNOOZE))
        }
        try {
            manager.notify(reminder.id.hashCode(), builder.build())
            return true
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted (Android 13+) - silently skip.
            return false
        }
    }
}
