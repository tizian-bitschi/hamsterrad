package de.tizian.hamsterrad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notifications {

    private const val CHANNEL_RECORDING = "aufzeichnung"
    private const val CHANNEL_SESSION = "session"
    const val FOREGROUND_ID = 1
    private const val SESSION_ID = 2

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
    fun foreground(context: Context, text: String, action: String?): Notification {
        val builder = Notification.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Hamsterrad")
            .setContentText(text)
            .setContentIntent(openApp(context, 0))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
        if (action != null) {
            val toggle = PendingIntent.getService(
                context,
                1,
                Intent(context, TreadmillService::class.java).setAction(TreadmillService.ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(Notification.Action.Builder(null, action, toggle).build())
        }
        return builder.build()
    }

    fun updateForeground(context: Context, text: String, action: String?) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(FOREGROUND_ID, foreground(context, text, action))
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
