package de.tizian.hamsterrad

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/**
 * Die Aufzeichnung lebt hier, nicht in der Activity. Der Dienst läuft dauerhaft,
 * weil eine offene Bluetooth-Verbindung mit dem App-Prozess stirbt.
 */
class TreadmillService : Service() {

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        AppVisible.watch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = if (Prefs.paused(this)) RecordingState.PAUSED else RecordingState.READY
        val started = runCatching {
            startForeground(
                Notifications.FOREGROUND_ID,
                Notifications.foreground(this, Recording.notification(state, null), Recording.action(state)),
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
        if (intent?.action == ACTION_TOGGLE) Treadmill.togglePause(this)
        Treadmill.start(this)
        return START_STICKY
    }

    override fun onDestroy() {
        Treadmill.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_TOGGLE = "de.tizian.hamsterrad.TOGGLE"

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, TreadmillService::class.java))
            }
        }
    }
}

/** Die Aufzeichnung kommt nach einem Neustart von selbst zurück. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        TreadmillService.start(context)
    }
}
