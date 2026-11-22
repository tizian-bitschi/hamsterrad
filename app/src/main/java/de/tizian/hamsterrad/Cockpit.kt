package de.tizian.hamsterrad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Der Hauptbildschirm: was das Band gerade tut, und was die Aufzeichnung tut. */
@Composable
internal fun Cockpit(
    onAufzeichnung: (Boolean) -> Unit,
    onBerechtigungen: () -> Unit,
    onBluetooth: () -> Unit,
) {
    val live by Treadmill.live
    val now by Treadmill.now
    val status by Treadmill.status
    val connected by Treadmill.connected
    val zustand = Treadmill.state

    val speed = now?.speedKmh ?: live?.speedKmh ?: 0.0
    val bandStill = now != null && speed < 0.2
    val last = Sessions.all.firstOrNull()
    val showLast = !connected && now == null && last != null

    val hinweis = when (zustand) {
        RecordingState.OFF ->
            Hinweis(Recording.notification(zustand, null), "Einschalten") { onAufzeichnung(true) }
        RecordingState.MISSING_PERMISSION ->
            Hinweis(Recording.notification(zustand, null), "Erlauben", onBerechtigungen)
        RecordingState.NO_BLUETOOTH ->
            Hinweis(Recording.notification(zustand, null), "Bluetooth einschalten", onBluetooth)
        else -> null
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 26.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                // Der Hinweis in der Mitte erklärt den Zustand; der Kopf bliebe sonst
                // bei "Suche Laufband" oder widerspräche ihm.
                if (hinweis != null) "" else status,
                color = Palette.Forest,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
            )
            Text(
                // "Live" sind laut Glossar die Werte des letzten Rahmens, nicht die Verbindung.
                if (connected) "● verbunden" else "○ nicht verbunden",
                color = if (connected) Palette.Forest else Palette.Muted,
                fontSize = 12.sp,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                hinweis != null -> HinweisBlock(hinweis)
                last != null && showLast -> LetzteSession(last)
                else -> LiveWerte(now, bandStill)
            }
        }

        val leer = hinweis != null || showLast
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val hrs = now?.hr ?: live?.hr ?: 0
            Metric("TEMPO", if (leer) "–" else paceText(speed), "min/km")
            Metric("SPEED", if (leer) "–" else formatSpeed(speed), "km/h")
            Metric("HF", if (leer || hrs !in 30..220) "–" else "$hrs", "bpm")
        }
    }
}

/** Ein Zustand, in dem das Cockpit nichts messen kann: mit einem Knopf, der etwas dagegen tut. */
private data class Hinweis(val titel: String, val aktion: String, val onAktion: () -> Unit)

@Composable
private fun HinweisBlock(hinweis: Hinweis) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            hinweis.titel,
            color = Palette.Ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PapierKnopf(hinweis.aktion, hinweis.onAktion)
    }
}

@Composable
private fun PapierKnopf(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, Palette.Forest, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(text, color = Palette.Forest, fontSize = 13.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun LetzteSession(last: Session) {
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
            "${formatKm(last.distanceM)} km · ${kurzDatum(last.start)} ${uhrzeit(last.start)} · " +
                "${last.hrAvg} bpm",
            color = Palette.Muted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun LiveWerte(now: Now?, bandStill: Boolean) {
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
            Text(
                " km",
                color = Palette.Muted,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
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
            Text(
                " $unit",
                color = Palette.Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
    }
}
