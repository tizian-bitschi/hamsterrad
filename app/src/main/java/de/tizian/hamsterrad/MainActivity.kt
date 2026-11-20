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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        setContent {
            MaterialTheme(colorScheme = HamsterTheme) {
                Surface(Modifier.fillMaxSize(), color = Palette.Paper) {
                    Cockpit(onFixPermissions = { withPermissions(::startRecording) })
                }
            }
        }
        withPermissions(::startRecording)
    }

    override fun onResume() {
        super.onResume()
        Treadmill.batteryExempt.value = batteryExempt()
    }

    private fun startRecording() {
        Treadmill.permissionMissing.value = false
        TreadmillService.start(this)
        askBatteryOnce()
        askNotificationsOnce()
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
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
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

@Composable
private fun Cockpit(onFixPermissions: () -> Unit) {
    val live by Treadmill.live
    val now by Treadmill.now
    val status by Treadmill.status
    val connected by Treadmill.connected
    var history by remember { mutableStateOf(false) }

    val speed = now?.speedKmh ?: live?.speedKmh ?: 0.0
    val bandStill = now != null && speed < 0.2
    val last = Sessions.all.firstOrNull()
    val showLast = !connected && now == null && last != null

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Paper)
            .safeDrawingPadding()
            .padding(horizontal = 22.dp, vertical = 26.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                status,
                color = Palette.Forest,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
            )
            Text(
                if (connected) "● live" else "○ nicht verbunden",
                color = if (connected) Palette.Forest else Palette.Muted,
                fontSize = 12.sp,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (showLast && last != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LETZTE SESSION", color = Palette.Muted, fontSize = 11.sp, letterSpacing = 5.sp)
                    Text(
                        fmtTime(last.seconds),
                        color = Palette.Ink,
                        fontSize = 72.sp,
                        fontFamily = Numeral,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${formatKm(last.distanceM)} km · ${dayFormat(last.start)} · ${last.hrAvg} bpm",
                        color = Palette.Muted,
                        fontSize = 14.sp,
                    )
                }
            } else {
                Column(
                    Modifier.alpha(if (bandStill) 0.35f else 1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("ZEIT", color = Palette.Muted, fontSize = 11.sp, letterSpacing = 5.sp)
                    Text(
                        fmtTime(now?.seconds ?: 0),
                        color = Palette.Ink,
                        fontSize = 92.sp,
                        fontFamily = Numeral,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("DISTANZ", color = Palette.Muted, fontSize = 11.sp, letterSpacing = 5.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatKm(now?.distanceM ?: 0),
                            color = Palette.Forest,
                            fontSize = 44.sp,
                            fontFamily = Numeral,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(" km", color = Palette.Muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val hrs = now?.hr ?: live?.hr ?: 0
            Metric("TEMPO", if (showLast) "–" else paceText(speed), "min/km")
            Metric("SPEED", if (showLast) "–" else formatSpeed(speed), "km/h")
            Metric("HF", if (showLast || hrs !in 30..220) "–" else "$hrs", "bpm")
        }

        Spacer(Modifier.height(18.dp))
        val (weekCount, weekMetres) = Sessions.thisWeek()
        Text(
            "Diese Woche: $weekCount Sessions · ${formatKm(weekMetres)} km",
            color = Palette.Muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = { history = true }) { Text("Verlauf", color = Palette.Forest) }
            TextButton(onClick = { Treadmill.reconnect() }) { Text("Neu verbinden", color = Palette.Forest) }
        }
        Spacer(Modifier.height(10.dp))
        RecordingLine(onFixPermissions)
    }

    if (history) HistoryDialog { history = false }
}

/** Eine Zeile, die zeigt, was die Aufzeichnung tut, und sie zugleich schaltet. */
@Composable
private fun RecordingLine(onFixPermissions: () -> Unit) {
    val context = LocalContext.current
    val state = Treadmill.state
    val exempt by Treadmill.batteryExempt
    val suffix = if (!exempt && state != RecordingState.MISSING_PERMISSION) {
        " · Akku-Optimierung an"
    } else {
        ""
    }
    val paused = state == RecordingState.PAUSED

    // Der Chip ist so breit wie sein Text; die Klickfläche bleibt die ganze Zeile.
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = state != RecordingState.RUNNING) {
                when (state) {
                    RecordingState.MISSING_PERMISSION -> onFixPermissions()
                    RecordingState.OFF -> TreadmillService.start(context)
                    // Bluetooth einschalten kann nur der Nutzer; die Zeile sagt nur Bescheid.
                    RecordingState.NO_BLUETOOTH -> Unit
                    else -> Treadmill.togglePause(context)
                }
            },
    ) {
        Text(
            Recording.cockpit(state, Treadmill.now.value) + suffix,
            color = if (paused) Palette.Paper else Palette.Muted,
            fontSize = 11.sp,
            modifier = if (paused) {
                Modifier
                    .background(Palette.Ink, RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            } else {
                Modifier
            },
        )
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Palette.Muted, fontSize = 10.sp, letterSpacing = 3.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = Palette.Forest,
                fontSize = 28.sp,
                fontFamily = Numeral,
                fontWeight = FontWeight.SemiBold,
            )
            Text(" $unit", color = Palette.Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

@Composable
private fun HistoryDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } },
        title = { Text("Verlauf", color = Palette.Ink) },
        text = {
            val list = Sessions.all
            LazyColumn {
                item {
                    Chart()
                    Spacer(Modifier.height(4.dp))
                    if (list.isNotEmpty()) {
                        Text("Wischen löscht einen Eintrag.", color = Palette.Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                }
                if (list.isEmpty()) {
                    item { Text("Noch keine Sessions.", color = Palette.Muted) }
                } else {
                    items(list, key = { it.start }) { s -> SessionRow(s) }
                }
            }
        },
        containerColor = Palette.Paper,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionRow(s: Session) {
    val ctx = LocalContext.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                Sessions.remove(ctx, s)
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().padding(end = 12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("löschen", color = Palette.Ink, fontSize = 11.sp)
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Sand)
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(dayFormat(s.start), color = Palette.Ink, fontSize = 13.sp)
            Text(
                summary(s.seconds, s.distanceM, s.hrAvg),
                color = Palette.Ink,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Chart() {
    val days = dayBuckets()
    val max = days.maxOf { it.second }.coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(106.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        days.forEach { (label, metres) ->
            val h = ((metres.toFloat() / max) * 48f).coerceAtLeast(2f)
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(if (metres > 0) formatKm(metres) else "", fontSize = 8.sp, color = Palette.Muted)
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .width(24.dp)
                        .height(h.dp)
                        .background(
                            if (metres > 0) Palette.Forest else Palette.Sand,
                            RoundedCornerShape(2.dp),
                        ),
                )
                Spacer(Modifier.height(3.dp))
                Text(label, fontSize = 8.sp, color = Palette.Muted)
            }
        }
    }
}

/** Sieben Tagessummen, heute als letzter Balken. */
private fun dayBuckets(): List<Pair<String, Int>> {
    val out = ArrayList<Pair<String, Int>>(7)
    for (i in 6 downTo 0) {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_MONTH, -i)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        val end = start + 86_400_000L
        val metres = Sessions.all.filter { it.start in start until end }.sumOf { it.distanceM }
        out.add(SimpleDateFormat("dd.MM.", Locale.GERMAN).format(Date(start)) to metres)
    }
    return out
}
