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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Lime = Color(0xFFC6FF00)
private val Cyan = Color(0xFF00E5FF)
private val Magenta = Color(0xFFFF3D7F)
private val Amber = Color(0xFFFFC400)
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

    val speed = now?.speedKmh ?: live?.speedKmh ?: 0.0
    val paused = now != null && speed < 0.2
    val last = Sessions.all.firstOrNull()
    val showLast = !connected && now == null && last != null

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 22.dp, vertical = 26.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (paused) "Pause" else status,
                color = if (paused) Amber else Cyan,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
            )
            Text(
                if (connected) "● live" else "○ nicht verbunden",
                color = if (connected) Lime else Dim,
                fontSize = 12.sp,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (showLast && last != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LETZTE SESSION", color = Dim, fontSize = 11.sp, letterSpacing = 5.sp)
                    Text(
                        fmtTime(last.seconds),
                        color = Color(0xFF777777),
                        fontSize = 72.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${fmt2(last.distanceM / 1000.0)} km · ${dayFormat(last.start)} · ${last.hrAvg} bpm",
                        color = Color(0xFF555555),
                        fontSize = 14.sp,
                    )
                }
            } else {
                Column(
                    Modifier.alpha(if (paused) 0.35f else 1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("ZEIT", color = Dim, fontSize = 11.sp, letterSpacing = 5.sp)
                    Text(
                        fmtTime(now?.seconds ?: 0),
                        color = Lime,
                        fontSize = 92.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("DISTANZ", color = Dim, fontSize = 11.sp, letterSpacing = 5.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            fmt2((now?.distanceM ?: 0) / 1000.0),
                            color = Cyan,
                            fontSize = 44.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(" km", color = Dim, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val hrs = now?.hr ?: live?.hr ?: 0
            Metric("TEMPO", if (showLast) "–" else paceText(speed), "min/km", Cyan)
            Metric("SPEED", if (showLast) "–" else fmt1(speed), "km/h", Cyan)
            Metric("HF", if (showLast || hrs !in 30..220) "–" else "$hrs", "bpm", Magenta)
        }

        Spacer(Modifier.height(18.dp))
        val (weekCount, weekMetres) = Sessions.thisWeek()
        Text(
            "Diese Woche: $weekCount Sessions · ${fmt2(weekMetres / 1000.0)} km",
            color = Dim,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = { history = true }) { Text("Verlauf", color = Cyan) }
            TextButton(onClick = { Treadmill.scan() }) { Text("Neu verbinden", color = Dim) }
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
                fontSize = 28.sp,
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
            LazyColumn {
                item {
                    Chart()
                    Spacer(Modifier.height(4.dp))
                    if (list.isNotEmpty()) {
                        Text("Wischen löscht einen Eintrag.", color = Dim, fontSize = 10.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                }
                if (list.isEmpty()) {
                    item { Text("Noch keine Sessions.", color = Dim) }
                } else {
                    items(list, key = { it.start }) { s -> SessionRow(s) }
                }
            }
        },
        containerColor = Color(0xFF111111),
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
                Text("löschen", color = Magenta, fontSize = 11.sp)
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111))
                .padding(vertical = 9.dp),
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
                Text(if (metres > 0) fmt2(metres / 1000.0) else "", fontSize = 8.sp, color = Dim)
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .width(24.dp)
                        .height(h.dp)
                        .background(if (metres > 0) Cyan else Color(0xFF1B1B1B), RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(3.dp))
                Text(label, fontSize = 8.sp, color = Dim)
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


