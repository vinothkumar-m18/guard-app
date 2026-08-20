package com.vinoth.guardapp
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
object NotificationHelper {
    private const val CHANNEL_ID = "friction_channel"
    private const val CHANNEL_NAME = "Guard Friction"
    private var notifId = 1000
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }
    }
    // Full-screen intent: attempts to launch ReflectionActivity automatically, without requiring
    // a manual tap. Some OEMs (possibly this Vivo/OriginOS device) may downgrade this to a
    // tap-to-open notification depending on background-launch restrictions - the notification
    // itself (with setContentIntent as a fallback) still works either way.
    fun showFrictionNotification(context: Context, domain: String) {
        ensureChannel(context)
        val intent = Intent(context, ReflectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("domain", domain)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, domain.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Hold on a moment")
            .setContentText("Blocked attempt: $domain")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId++, notification)
    }
}
