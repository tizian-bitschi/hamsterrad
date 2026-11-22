package de.tizian.hamsterrad

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Der Zeitraum, den das Diagramm zeigt. */
enum class Zeitraum(val titel: String) {
    TAG("7 Tage"),
    WOCHE("4 Wochen"),
    MONAT("12 Monate"),
}

/** Ein Balken im Diagramm: ein Tag, eine Kalenderwoche oder ein Monat. */
data class Eimer(val label: String, val meter: Int)

/** Was die Summenzeile über den gewählten Zeitraum sagt. */
data class Summe(val sessions: Int, val meter: Int, val sekunden: Int)

/**
 * Die Rechnung hinter dem Verlauf. Alles hier ist rein: hinein gehen die
 * gespeicherten Sessions und ein Zeitpunkt, heraus kommen Balken und Summe.
 */
object Verlauf {

    /** Kurzformen, damit zwölf Balken nebeneinander passen. */
    private val MONATE = arrayOf(
        "Jan", "Feb", "Mär", "Apr", "Mai", "Jun",
        "Jul", "Aug", "Sep", "Okt", "Nov", "Dez",
    )

    private val MONATE_LANG = arrayOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    )

    private data class Grenze(val label: String, val von: Long, val bis: Long)

    fun eimer(
        zeitraum: Zeitraum,
        jetzt: Long,
        sessions: List<Session>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Eimer> = grenzen(zeitraum, jetzt, zone).map { grenze ->
        Eimer(
            label = grenze.label,
            meter = sessions.filter { it.start in grenze.von until grenze.bis }
                .sumOf { it.distanceM },
        )
    }

    fun summe(
        zeitraum: Zeitraum,
        jetzt: Long,
        sessions: List<Session>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Summe {
        val alle = grenzen(zeitraum, jetzt, zone)
        val von = alle.first().von
        val bis = alle.last().bis
        val drin = sessions.filter { it.start in von until bis }
        return Summe(
            sessions = drin.size,
            meter = drin.sumOf { it.distanceM },
            sekunden = drin.sumOf { it.seconds },
        )
    }

    /** „3 Sessions · 5,42 km · 1:12:30" */
    fun summenText(summe: Summe): String {
        val anzahl = if (summe.sessions == 1) "1 Session" else "${summe.sessions} Sessions"
        return "$anzahl · ${formatKm(summe.meter)} km · ${fmtTime(summe.sekunden)}"
    }

    /** „November 2026" */
    fun monatsKopf(monat: YearMonth): String =
        "${MONATE_LANG[monat.monthValue - 1]} ${monat.year}"

    private fun grenzen(zeitraum: Zeitraum, jetzt: Long, zone: ZoneId): List<Grenze> {
        val heute = Instant.ofEpochMilli(jetzt).atZone(zone).toLocalDate()
        return when (zeitraum) {
            Zeitraum.TAG -> tage(heute, zone)
            Zeitraum.WOCHE -> wochen(heute, zone)
            Zeitraum.MONAT -> monate(heute, zone)
        }
    }

    private fun tage(heute: LocalDate, zone: ZoneId): List<Grenze> = (6 downTo 0).map { zurueck ->
        val tag = heute.minusDays(zurueck.toLong())
        Grenze(
            label = String.format(Locale.GERMAN, "%02d.%02d.", tag.dayOfMonth, tag.monthValue),
            von = tag.atStartOfDay(zone).toInstant().toEpochMilli(),
            bis = tag.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    private fun wochen(heute: LocalDate, zone: ZoneId): List<Grenze> {
        val montag = heute.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (3 downTo 0).map { zurueck ->
            val start = montag.minusWeeks(zurueck.toLong())
            Grenze(
                label = "KW ${start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}",
                von = start.atStartOfDay(zone).toInstant().toEpochMilli(),
                bis = start.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
    }

    private fun monate(heute: LocalDate, zone: ZoneId): List<Grenze> {
        val monat = YearMonth.from(heute)
        return (11 downTo 0).map { zurueck ->
            val yearMonth = monat.minusMonths(zurueck.toLong())
            Grenze(
                label = MONATE[yearMonth.monthValue - 1],
                von = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                bis = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
    }
}

/** Ein gelöschter Eintrag, der zurückgelegt werden kann. */
private data class Geloescht(val index: Int, val session: Session)

/** Die Verlaufsseite: Diagramm, Summe, alle Sessions nach Monaten. */
@Composable
internal fun VerlaufSeite() {
    val context = LocalContext.current
    val sessions = Sessions.all
    var zeitraum by rememberSaveable { mutableStateOf(Zeitraum.TAG) }
    var detail by remember { mutableStateOf<Session?>(null) }
    var geloescht by remember { mutableStateOf<Geloescht?>(null) }
    val snacks = remember { SnackbarHostState() }

    LaunchedEffect(geloescht) {
        val weg = geloescht ?: return@LaunchedEffect
        val ergebnis = snacks.showSnackbar(
            message = "Gelöscht",
            actionLabel = "Rückgängig",
            duration = SnackbarDuration.Short,
        )
        if (ergebnis == SnackbarResult.ActionPerformed) {
            Sessions.insert(context, weg.index, weg.session)
        }
        geloescht = null
    }

    val offen = detail
    BackHandler(enabled = offen != null) { detail = null }

    Box(Modifier.fillMaxSize()) {
        if (offen != null) {
            SessionDetail(
                session = offen,
                onZurueck = { detail = null },
                onLoeschen = {
                    val index = sessions.indexOf(offen)
                    Sessions.remove(context, offen)
                    detail = null
                    geloescht = Geloescht(index, offen)
                },
            )
        } else {
            Liste(
                zeitraum = zeitraum,
                sessions = sessions,
                onZeitraum = { zeitraum = it },
                onOeffnen = { detail = it },
            )
        }
        SnackbarHost(snacks, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Liste(
    zeitraum: Zeitraum,
    sessions: List<Session>,
    onZeitraum: (Zeitraum) -> Unit,
    onOeffnen: (Session) -> Unit,
) {
    val jetzt = System.currentTimeMillis()
    val eimer = Verlauf.eimer(zeitraum, jetzt, sessions)
    val summe = Verlauf.summe(zeitraum, jetzt, sessions)
    val laeuft by Treadmill.now

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 26.dp),
    ) {
        Text("VERLAUF", color = Palette.Muted, fontSize = 11.sp, letterSpacing = 5.sp)
        Spacer(Modifier.height(18.dp))
        ZeitraumWahl(zeitraum, onZeitraum)
        Spacer(Modifier.height(12.dp))
        Diagramm(eimer)
        Spacer(Modifier.height(10.dp))
        Text(Verlauf.summenText(summe), color = Palette.Ink, fontSize = 13.sp)
        if (laeuft != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Session läuft — erscheint nach dem Ende.",
                color = Palette.Muted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(22.dp))
        if (sessions.isEmpty()) {
            Text("Noch keine Sessions.", color = Palette.Muted, fontSize = 13.sp)
        } else {
            Gruppen(sessions, onOeffnen)
        }
    }
}

@Composable
private fun Gruppen(sessions: List<Session>, onOeffnen: (Session) -> Unit) {
    val zone = ZoneId.systemDefault()
    val nachMonat = sessions.groupBy {
        YearMonth.from(Instant.ofEpochMilli(it.start).atZone(zone))
    }
    nachMonat.forEach { (monat, eintraege) ->
        Text(Verlauf.monatsKopf(monat), color = Palette.Muted, fontSize = 11.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(4.dp))
        eintraege.forEach { session -> SessionZeile(session, onOeffnen) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SessionZeile(session: Session, onOeffnen: (Session) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOeffnen(session) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(kurzDatum(session.start), color = Palette.Ink, fontSize = 14.sp)
                Text(uhrzeit(session.start), color = Palette.Muted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmtTime(session.seconds),
                    color = Palette.Ink,
                    fontSize = 18.sp,
                    fontFamily = Numeral,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${formatKm(session.distanceM)} km" +
                        if (session.hrAvg > 0) " · ${session.hrAvg} bpm" else "",
                    color = Palette.Muted,
                    fontSize = 11.sp,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Sand))
    }
}

@Composable
private fun ZeitraumWahl(gewaehlt: Zeitraum, onWaehlen: (Zeitraum) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Zeitraum.entries.forEach { zeitraum ->
            Text(
                zeitraum.titel,
                color = if (zeitraum == gewaehlt) Palette.Forest else Palette.Muted,
                fontSize = 12.sp,
                textDecoration = if (zeitraum == gewaehlt) TextDecoration.Underline else null,
                modifier = Modifier.clickable { onWaehlen(zeitraum) },
            )
        }
    }
}

@Composable
private fun Diagramm(eimer: List<Eimer>) {
    val max = eimer.maxOf { it.meter }.coerceAtLeast(1)
    val schmal = eimer.size > 9
    Row(
        Modifier.fillMaxWidth().height(112.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(if (schmal) 2.dp else 4.dp),
    ) {
        eimer.forEach { eintrag ->
            val hoehe = ((eintrag.meter.toFloat() / max) * 52f).coerceAtLeast(2f)
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    if (eintrag.meter > 0) formatKm(eintrag.meter) else "",
                    color = Palette.Muted,
                    fontSize = 8.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .width(if (schmal) 14.dp else 26.dp)
                        .height(hoehe.dp)
                        .background(
                            if (eintrag.meter > 0) Palette.Forest else Palette.Sand,
                            RoundedCornerShape(2.dp),
                        ),
                )
                Spacer(Modifier.height(3.dp))
                Text(eintrag.label, color = Palette.Muted, fontSize = 8.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SessionDetail(session: Session, onZurueck: () -> Unit, onLoeschen: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 26.dp),
    ) {
        Text(
            "‹ Verlauf",
            color = Palette.Forest,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onZurueck),
        )
        Spacer(Modifier.height(26.dp))
        Text(
            "${kurzDatum(session.start)} · ${uhrzeit(session.start)}",
            color = Palette.Muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text("DAUER", color = Palette.Muted, fontSize = 10.sp, letterSpacing = 3.sp)
        Text(
            fmtTime(session.seconds),
            color = Palette.Ink,
            fontSize = 64.sp,
            fontFamily = Numeral,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(22.dp))
        Wert("Distanz", "${formatKm(session.distanceM)} km")
        val tempo = paceOf(session.seconds, session.distanceM)
        Wert("Ø-Tempo", if (tempo == "–") "–" else "$tempo min/km")
        Wert("Ø-Herzfrequenz", if (session.hrAvg > 0) "${session.hrAvg} bpm" else "–")
        Wert("HF-Spitze", if (session.hrMax > 0) "${session.hrMax} bpm" else "–")
        Wert("Energie", "${String.format(Locale.GERMAN, "%.0f", session.kcal)} kcal")
        Spacer(Modifier.height(30.dp))
        TextButton(onClick = onLoeschen) { Text("Session löschen", color = Palette.Ink) }
    }
}

@Composable
private fun Wert(label: String, wert: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Palette.Muted, fontSize = 12.sp)
        Text(wert, color = Palette.Ink, fontSize = 15.sp)
    }
}
