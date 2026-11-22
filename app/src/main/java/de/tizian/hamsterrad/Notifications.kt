package de.tizian.hamsterrad

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Notifications {

    private const val CHANNEL_RECORDING = "aufzeichnung"
    private const val CHANNEL_SESSION = "session"
    const val FOREGROUND_ID = 1
    private const val SESSION_ID = 2

    /** Ob die App Benachrichtigungen zeigen darf. Unter Android 12 gibt es die Frage nicht. */
    fun erlaubt(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, "Aufzeichnung", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Zeigt, ob die Aufzeichnung läuft."
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SESSION, "Sessions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Meldet das Ende einer Session."
            },
        )
    }

    /** Die Dauerbenachrichtigung, die am Dienst hängt. */
    fun foreground(context: Context, text: String): Notification =
        Notification.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Hamsterrad")
            .setContentText(text)
            .setContentIntent(openApp(context, 0))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .build()

    fun updateForeground(context: Context, text: String) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(FOREGROUND_ID, foreground(context, text))
        }
    }

    /** Die Benachrichtigung nach einer Session. */
    fun showSession(context: Context, session: Session) {
        val note = Notification.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Session beendet")
            .setContentText(summary(session.seconds, session.distanceM, session.hrAvg))
            .setContentIntent(openApp(context, 2))
            .setAutoCancel(true)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.notify(SESSION_ID, note)
        }
    }

    private fun openApp(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
}
