package de.tizian.hamsterrad

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

internal fun dayFormat(ms: Long): String =
    SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN).format(Date(ms))
