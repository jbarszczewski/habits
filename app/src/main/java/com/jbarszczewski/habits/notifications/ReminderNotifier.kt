package com.jbarszczewski.habits.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jbarszczewski.habits.MainActivity
import com.jbarszczewski.habits.R
import com.jbarszczewski.habits.data.Task

/**
 * Shows the noon/4pm "still unfinished" reminder as one grouped notification (not one per habit)
 * so it stays quiet even with several habits pending.
 */
object ReminderNotifier {
    const val CHANNEL_ID = "habit_reminders"
    private const val NOTIFICATION_ID = 1

    /**
     * Registers the notification channel. Safe to call on every app start: creating a channel
     * that already exists is a no-op, and channels only exist on API 26+ (this app's minSdk).
     */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Shows one notification listing [tasks], or shows nothing if the list is empty. Silently
     * does nothing on API 33+ without the POST_NOTIFICATIONS permission, since a worker has no
     * way to ask for it itself.
     */
    fun notifyUnfinished(context: Context, tasks: List<Task>) {
        if (tasks.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.resources.getQuantityString(
            R.plurals.notification_reminder_title,
            tasks.size,
            tasks.size,
        )
        val text = tasks.joinToString { it.name }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
