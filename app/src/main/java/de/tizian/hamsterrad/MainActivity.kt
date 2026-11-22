package de.tizian.hamsterrad

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var pending: (() -> Unit)? = null

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (bluetoothGranted()) pending?.invoke()
            else Treadmill.permissionMissing.value = true
            pending = null
        }

    private val notifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Papier ist hell: dunkle Symbole in den Systemleisten, Fenster schon vor Compose creme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(Palette.Paper.toArgb(), Palette.Paper.toArgb()),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Sessions.load(this)
        Treadmill.prepare(this)
        setContent {
            MaterialTheme(colorScheme = HamsterTheme) {
                Surface(Modifier.fillMaxSize(), color = Palette.Paper) {
                    Shell(
                        onAufzeichnung = ::schalten,
                        onBerechtigungen = { withPermissions(::startRecording) },
                        onBenachrichtigungen = ::requestNotifications,
                        onAkku = ::requestBattery,
                    )
                }
            }
        }
        if (Prefs.recording(this)) withPermissions(::startRecording)
    }

    override fun onResume() {
        super.onResume()
        Treadmill.batteryExempt.value = batteryExempt()
    }

    /** Einschalten fragt zuerst die Bluetooth-Berechtigung; Ausschalten braucht keine. */
    private fun schalten(an: Boolean) {
        if (!an) {
            Treadmill.setRecording(this, false)
            return
        }
        withPermissions {
            Treadmill.permissionMissing.value = false
            Treadmill.setRecording(this, true)
            askBatteryOnce()
            askNotificationsOnce()
        }
    }

    private fun startRecording() {
        Treadmill.permissionMissing.value = false
        TreadmillService.start(this)
        askBatteryOnce()
        askNotificationsOnce()
    }

    /** Aus den Einstellungen heraus: fragt ohne Rücksicht auf frühere Fragen. */
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Aus den Einstellungen heraus: öffnet die Akku-Ausnahme, ohne das Fragen-Flag zu setzen. */
    private fun requestBattery() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    /** Die Benachrichtigung ist Kür: die Aufzeichnung läuft auch ohne sie. */
    private fun askNotificationsOnce() {
        if (Build.VERSION.SDK_INT < 33 || Prefs.askedNotifications(this)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Prefs.setAskedNotifications(this)
        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun bluetoothGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun batteryExempt(): Boolean =
        getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName)
            ?: true

    private fun askBatteryOnce() {
        if (Prefs.askedBattery(this) || batteryExempt()) {
            Treadmill.batteryExempt.value = batteryExempt()
            return
        }
        Prefs.setAskedBattery(this)
        requestBattery()
    }

    private fun withPermissions(action: () -> Unit) {
        if (bluetoothGranted()) {
            action()
            return
        }
        pending = action
        permissions.launch(
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
        )
    }
}
