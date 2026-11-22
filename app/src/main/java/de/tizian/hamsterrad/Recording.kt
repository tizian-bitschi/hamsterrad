package de.tizian.hamsterrad

/** Was die Aufzeichnung gerade tut. */
enum class RecordingState {
    OFF,
    MISSING_PERMISSION,
    NO_BLUETOOTH,
    RUNNING,
    READY,
    NO_BAND,
}

/** Die Wörter, mit denen die Aufzeichnung über sich spricht. */
internal object Recording {

    /** Der eine Satz, den die Dauerbenachrichtigung und das Cockpit sagen. */
    fun notification(state: RecordingState, now: Now?): String = when (state) {
        RecordingState.OFF -> "Aufzeichnung aus"
        RecordingState.MISSING_PERMISSION -> "Bluetooth-Berechtigung fehlt"
        RecordingState.NO_BLUETOOTH -> "Bluetooth ist aus"
        RecordingState.RUNNING -> running(now)
        RecordingState.READY -> "Aufzeichnung bereit"
        RecordingState.NO_BAND -> "Aufzeichnung bereit · Band nicht in Reichweite"
    }

    /** Ein Wort für den Schalter in den Einstellungen. */
    fun kurz(state: RecordingState): String = when (state) {
        RecordingState.OFF -> "aus"
        RecordingState.MISSING_PERMISSION -> "Berechtigung fehlt"
        RecordingState.NO_BLUETOOTH -> "Bluetooth aus"
        RecordingState.RUNNING -> "Session läuft"
        RecordingState.READY -> "läuft"
        RecordingState.NO_BAND -> "wartet auf Band"
    }

    private fun running(now: Now?): String =
        "Session läuft: ${fmtTime(now?.seconds ?: 0)} · ${formatKm(now?.distanceM ?: 0)} km"
}
