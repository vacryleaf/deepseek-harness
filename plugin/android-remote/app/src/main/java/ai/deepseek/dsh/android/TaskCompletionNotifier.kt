package ai.deepseek.dsh.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/** Posts a local notification when the loaded DSH root session completes a turn. */
class TaskCompletionNotifier(private val context: Context) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.task_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.task_notification_channel_description)
            },
        )
    }

    /** Posts a notification unless the Android 13 runtime permission is unavailable. */
    fun notifyTaskCompleted(serviceUrl: String, completion: TaskCompletion) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val key = "$serviceUrl|${completion.sessionId}|${completion.turn}|${completion.sequence}"
        if (!rememberCompletion(key)) return

        ensureChannel()
        val host = Uri.parse(serviceUrl).host ?: context.getString(R.string.remote_service_short)
        val text = context.getString(R.string.task_completed_message, host, completion.turn)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.task_completed_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        val notificationId = (System.currentTimeMillis() and 0x7fffffff).toInt()
        notificationManager.notify(notificationId, notification)
    }

    private fun rememberCompletion(key: String): Boolean {
        synchronized(recentCompletions) {
            if (!recentCompletions.add(key)) return false
            while (recentCompletions.size > MAX_RECENT_COMPLETIONS) {
                recentCompletions.iterator().next().let(recentCompletions::remove)
            }
            return true
        }
    }

    private companion object {
        val recentCompletions = LinkedHashSet<String>()
        const val CHANNEL_ID = "dsh-task-completed"
        const val REQUEST_CODE = 2001
        const val MAX_RECENT_COMPLETIONS = 512
    }
}
