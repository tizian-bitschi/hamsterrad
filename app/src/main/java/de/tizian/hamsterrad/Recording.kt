package de.tizian.hamsterrad

/** Was die Aufzeichnung gerade tut. */
enum class RecordingState {
    OFF,
    MISSING_PERMISSION,
    PAUSED,
    NO_BLUETOOTH,
    RUNNING,
    READY,
    NO_BAND,
}

/** Die Wörter, mit denen die Aufzeichnung über sich spricht. */
internal object Recording {

    /** Was in der Dauerbenachrichtigung steht: bereit, Session läuft, pausiert, Band weg. */
    fun notification(state: RecordingState, now: Now?): String = when (state) {
        RecordingState.RUNNING -> running(now)
        RecordingState.PAUSED -> "Aufzeichnung pausiert"
        RecordingState.READY -> "Aufzeichnung bereit"
        else -> "Aufzeichnung bereit · Band nicht in Reichweite"
    }

    /** Was in der Zeile im Cockpit steht. Dort steht auch, warum nichts geht. */
    fun cockpit(state: RecordingState, now: Now?): String = when (state) {
        RecordingState.OFF -> "Aufzeichnung aus"
        RecordingState.MISSING_PERMISSION -> "Bluetooth-Berechtigung fehlt"
        RecordingState.NO_BLUETOOTH -> "Bluetooth ist aus"
        RecordingState.RUNNING -> "Aufzeichnung läuft"
        else -> notification(state, now)
    }

    /** Die Aktion an der Dauerbenachrichtigung, oder null während einer Session. */
    fun action(state: RecordingState): String? = when (state) {
        RecordingState.RUNNING -> null
        RecordingState.PAUSED -> "Fortsetzen"
        else -> "Pausieren"
    }

    private fun running(now: Now?): String =
        "Session läuft: ${fmtTime(now?.seconds ?: 0)} · ${formatKm(now?.distanceM ?: 0)} km"
}
