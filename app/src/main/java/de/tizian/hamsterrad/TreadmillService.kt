package de.tizian.hamsterrad

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/**
 * Die Aufzeichnung lebt hier, nicht in der Activity. Der Dienst läuft, solange
 * der Schalter an ist, weil eine offene Bluetooth-Verbindung mit dem App-Prozess stirbt.
 */
class TreadmillService : Service() {

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        AppVisible.watch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Aus heißt aus: kein Dienst, keine Dauerbenachrichtigung. Der Start kann
        // zwischengespeichert worden sein, bevor der Schalter fiel.
        if (!Prefs.recording(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            startForeground(
                Notifications.FOREGROUND_ID,
                Notifications.foreground(this, Recording.notification(RecordingState.READY, null)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        }
        val failure = started.exceptionOrNull()
        if (failure != null) {
            // Fehlt die Bluetooth-Berechtigung, darf der Dienst nicht als connectedDevice
            // laufen. Andere Fehler sind Programmierfehler und werden nicht als solcher
            // verkleidet: der Dienst bleibt aus, das Cockpit zeigt "Aufzeichnung aus".
            Treadmill.permissionMissing.value = failure is SecurityException
            stopSelf()
            return START_NOT_STICKY
        }
        Treadmill.start(this)
        return START_STICKY
    }

    override fun onDestroy() {
        Treadmill.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {

        /** Startet den Dienst nur, wenn der Schalter an ist. */
        fun start(context: Context) {
            if (!Prefs.recording(context)) return
            runCatching {
                context.startForegroundService(Intent(context, TreadmillService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, TreadmillService::class.java)) }
        }
    }
}

/** Die Aufzeichnung kommt nach einem Neustart von selbst zurück, wenn der Schalter an ist. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        TreadmillService.start(context)
    }
}
