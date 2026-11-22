package de.tizian.hamsterrad

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
data class Eimer(
    val label: String,
    val meter: Int,
    val sekunden: Int,
    val sessions: Int,
)

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
        val drin = sessions.filter { it.start in grenze.von until grenze.bis }
        Eimer(
            label = grenze.label,
            meter = drin.sumOf { it.distanceM },
            sekunden = drin.sumOf { it.seconds },
            sessions = drin.size,
        )
    }

    /** Die Summe über die gezeigten Balken. */
    fun summe(eimer: List<Eimer>): Summe = Summe(
        sessions = eimer.sumOf { it.sessions },
        meter = eimer.sumOf { it.meter },
        sekunden = eimer.sumOf { it.sekunden },
    )

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
