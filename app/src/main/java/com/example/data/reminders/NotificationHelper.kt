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
import com.example.domain.model.Reminder

/** Creates the reminders notification channel and posts reminder notifications. */
object NotificationHelper {
    const val CHANNEL_ID = "reminders"
    private const val CHANNEL_NAME = "Reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Note and task reminders"
                    enableVibration(true)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    fun notify(context: Context, reminder: Reminder) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val tap = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            if (reminder.noteId != null) putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, reminder.noteId)
        }
        val pending = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title.ifBlank { "Reminder" })
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (reminder.body.isNotBlank()) {
            builder.setContentText(reminder.body)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
        }
        try {
            manager.notify(reminder.id.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted (Android 13+) - silently skip.
        }
    }
}
