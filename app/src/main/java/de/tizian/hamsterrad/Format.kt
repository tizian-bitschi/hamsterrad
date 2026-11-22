package de.tizian.hamsterrad

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private val WOCHENTAGE = arrayOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/** „Di 22." */
internal fun kurzDatum(ms: Long): String {
    val tag = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${WOCHENTAGE[tag.dayOfWeek.value - 1]} ${tag.dayOfMonth}."
}

/** „18:04" */
internal fun uhrzeit(ms: Long): String {
    val zeit = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(zeit.hour, zeit.minute)
}

internal fun fmtTime(s: Int): String {
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

internal fun formatSpeed(v: Double): String = String.format(Locale.GERMAN, "%.1f", v)

internal fun formatKm(metres: Int): String = String.format(Locale.GERMAN, "%.2f", metres / 1000.0)

/** Dauer, Distanz und Puls einer gespeicherten Session. */
internal fun summary(seconds: Int, distanceM: Int, hrAvg: Int): String =
    "${fmtTime(seconds)} · ${formatKm(distanceM)} km" + if (hrAvg > 0) " · $hrAvg bpm" else ""

internal fun paceText(speedKmh: Double): String {
    if (speedKmh < 0.3) return "–"
    val secPerKm = (3600 / speedKmh).toInt()
    return "%d:%02d".format(secPerKm / 60, secPerKm % 60)
}

/** Das Durchschnittstempo einer gespeicherten Session in min/km. */
internal fun paceOf(seconds: Int, metres: Int): String {
    if (seconds <= 0) return "–"
    return paceText(metres * 3.6 / seconds)
}
