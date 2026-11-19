package de.tizian.hamsterrad

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Lime = Color(0xFFC6FF00)
private val Cyan = Color(0xFF00E5FF)
private val Magenta = Color(0xFFFF3D7F)
private val Dim = Color(0xFF6A6A6A)

class MainActivity : ComponentActivity() {

    private var pending: (() -> Unit)? = null

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.isNotEmpty() && grants.values.all { it }) pending?.invoke()
            pending = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Sessions.load(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) { Cockpit() }
            }
        }
        withPermissions { Treadmill.start(this) }
    }

    private fun withPermissions(action: () -> Unit) {
        val needed = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) action() else {
            pending = action
            permissions.launch(missing.toTypedArray())
        }
    }
}

@Composable
private fun Cockpit() {
    val live by Treadmill.live
    val now by Treadmill.now
    val status by Treadmill.status
    val connected by Treadmill.connected
    var history by remember { mutableStateOf(false) }

    val seconds = now?.seconds ?: 0
    val distanceKm = (now?.distanceM ?: 0) / 1000.0
    val speed = now?.speedKmh ?: live?.speedKmh ?: 0.0
    val hr = now?.hr ?: live?.hr ?: 0

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 22.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(status, color = Cyan, fontSize = 12.sp, letterSpacing = 1.sp)
            Text(if (connected) "● live" else "○ offline", color = if (connected) Lime else Dim, fontSize = 12.sp)
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ZEIT", color = Dim, fontSize = 11.sp, letterSpacing = 5.sp)
            Text(
                fmtTime(seconds),
                color = Lime,
                fontSize = 96.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(18.dp))
            Text("DISTANZ", color = Dim, fontSize = 11.sp, letterSpacing = 5.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    fmt2(distanceKm),
                    color = Cyan,
                    fontSize = 46.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Text(" km", color = Dim, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric("TEMPO", paceText(speed), "min/km", Cyan)
            Metric("SPEED", fmt1(speed), "km/h", Cyan)
            Metric("HF", if (hr in 30..220) "$hr" else "–", "bpm", Magenta)
        }

        val (weekCount, weekMetres) = Sessions.thisWeek()
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Diese Woche: $weekCount Sessions · ${fmt2(weekMetres / 1000.0)} km",
                color = Dim,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = { history = true }) { Text("Verlauf", color = Cyan) }
                TextButton(onClick = { Treadmill.scan() }) { Text("Neu verbinden", color = Dim) }
            }
        }
    }

    if (history) HistoryDialog { history = false }
}

@Composable
private fun Metric(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Dim, fontSize = 10.sp, letterSpacing = 3.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = color,
                fontSize = 30.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(" $unit", color = Dim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

@Composable
private fun HistoryDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } },
        title = { Text("Verlauf", color = Cyan) },
        text = {
            val list = Sessions.all
            if (list.isEmpty()) {
                Text("Noch keine Sessions.", color = Dim)
            } else {
                LazyColumn {
                    items(list) { s ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(dayFormat(s.start), color = Color.White, fontSize = 13.sp)
                            Text(
                                "${fmtTime(s.seconds)} · ${fmt2(s.distanceM / 1000.0)} km · ${s.hrAvg} bpm",
                                color = Dim,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF111111),
    )
}

private fun fmtTime(s: Int): String {
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private fun fmt2(v: Double): String = String.format(Locale.GERMAN, "%.2f", v)

private fun fmt1(v: Double): String = String.format(Locale.GERMAN, "%.1f", v)

private fun paceText(speedKmh: Double): String {
    if (speedKmh < 0.3) return "–"
    val secPerKm = (3600 / speedKmh).toInt()
    return "%d:%02d".format(secPerKm / 60, secPerKm % 60)
}

private fun dayFormat(ms: Long): String =
    SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN).format(Date(ms))
