package de.tizian.hamsterrad

/** Was der Kern aus einem Rahmen macht. */
sealed interface Event {
    /** replaces: die gespeicherte Session, die dieser Start fortsetzt. */
    data class Started(val replaces: Session? = null) : Event

    /** replaces: die gespeicherte Session, die ersetzt wird, wenn die Session eine Trennung überlebt hat. */
    data class Finished(
        val session: Session,
        val adopted: Boolean = false,
        val replaces: Session? = null,
    ) : Event
}

/** Ab dieser Geschwindigkeit läuft das Band. */
internal const val RUNNING_SPEED_KMH = 0.5

/** Takt, in dem der Wächter nach einem verstummten Band sieht. */
internal const val TICK_MS = 5_000L

/**
 * Die Session-Regeln ohne Android. Jede Zeit kommt als Zeitstempel herein,
 * damit sich die Regeln ohne Gerät prüfen lassen.
 */
class SessionEngine {

    private companion object {
        const val STOP_SPEED = 0.2
        const val IDLE_STOP_MS = 20_000L
        const val STALL_MS = 25_000L
        const val MIN_SECONDS = 10
        const val MIN_METRES = 5
        const val RESUME_WINDOW_MS = 10 * 60_000L
    }

    /** Die laufende Session, oder null. */
    var now: Now? = null
        private set

    /** Zähler der zuletzt gespeicherten Session, damit eine übernommene nicht doppelt landet. */
    var lastSaved: Session? = null
        private set

    /** Nach einem Prozessneustart: die zuletzt gespeicherte Session darf weiterlaufen. */
    fun adopt(saved: Session?) {
        lastSaved = saved
        carried = saved
    }

    /** Nach dem Speichern: diese Session ist jetzt die letzte. */
    fun remember(session: Session) {
        lastSaved = session
    }

    /** Eine Session, die eine Trennung überlebt hat und weiterlaufen kann. */
    private var carried: Session? = null

    private var active = false
    private var prev: Live? = null
    private var idleSince = 0L
    private var lastFrameAt = 0L
    private var lastMovingAt = 0L
    private var lastTickAt = 0L
    private var startWall = 0L
    private var replacing: Session? = null
    private var hrSum = 0L
    private var hrCount = 0
    private var hrMax = 0
    private var firstOfConnection = true

    fun onConnected() {
        firstOfConnection = true
        lastTickAt = 0L
    }

    /** Nach einer Pause: alles vergessen, aber die gemerkte Session bleibt. */
    fun reset() {
        active = false
        now = null
        prev = null
        idleSince = 0L
        lastFrameAt = 0L
        lastTickAt = 0L
        replacing = null
        firstOfConnection = true
    }

    /**
     * Die Verbindung ist weg. Die Session wird gespeichert und gemerkt: kommt die
     * Verbindung zurück und die Zähler laufen weiter, ist es dieselbe Session und
     * die gespeicherte Fassung wird ersetzt statt eine zweite angelegt.
     *
     * shortcut: dieselbe Session wird nur innerhalb von zehn Minuten erkannt. Bei
     * längeren Funklöchern steht die Strecke zweimal im Verlauf. Aufräumen, wenn
     * die Trennungen länger werden.
     */
    fun onDisconnected(at: Long): List<Event> {
        val events = finish(at)
        // Nur eine wirklich beendete Session wird gemerkt. Ein fehlgeschlagener
        // Verbindungsversuch darf die gemerkte nicht wegwerfen.
        events.filterIsInstance<Event.Finished>().firstOrNull()?.let { carried = it.session }
        return events
    }

    fun onFrame(frame: Live, at: Long): List<Event> {
        lastFrameAt = at
        val first = firstOfConnection
        firstOfConnection = false

        if (!active) {
            val before = carried
            val sameRun = first && before != null &&
                at - before.end <= RESUME_WINDOW_MS &&
                frame.timeS >= before.seconds &&
                frame.distM >= before.distanceM &&
                (frame.speedKmh >= RUNNING_SPEED_KMH ||
                    frame.timeS > before.seconds || frame.distM > before.distanceM)
            if (!sameRun) carried = null
            return when {
                frame.speedKmh >= RUNNING_SPEED_KMH -> {
                    if (sameRun) carried = null
                    begin(frame, at, if (sameRun) before else null)
                    listOf(Event.Started(if (sameRun) before else null))
                }
                sameRun -> {
                    carried = null
                    listOf(
                        Event.Finished(
                            continuing(before, frame, at),
                            adopted = true,
                            replaces = before,
                        ),
                    )
                }
                first && frame.timeS >= MIN_SECONDS && frame.distM >= MIN_METRES &&
                    lastSaved?.let { it.seconds == frame.timeS && it.distanceM == frame.distM } != true ->
                    listOf(Event.Finished(adopted(frame, at), adopted = true))
                else -> emptyList()
            }
        }

        val previous = prev
        // Das Band setzt seine Zähler auf 0: das ist eine neue Fahrt, also neue Session.
        if (previous != null && (frame.timeS + 2 < previous.timeS || frame.distM + 2 < previous.distM)) {
            val events = finish(at)
            begin(frame, at, null)
            prev = frame
            return events + Event.Started()
        }
        prev = frame
        now = Now(frame.timeS, frame.distM, frame.speedKmh, frame.hr, frame.kcal)

        if (frame.hr in 30..220) {
            hrSum += frame.hr
            hrCount++
            if (frame.hr > hrMax) hrMax = frame.hr
        }
        if (frame.speedKmh >= STOP_SPEED) lastMovingAt = at
        if (frame.speedKmh < STOP_SPEED) {
            if (idleSince == 0L) idleSince = at
            // Das Ende ist der letzte Rahmen mit Bewegung, nicht der Ablauf der Frist.
            else if (at - idleSince >= IDLE_STOP_MS) return finish(lastMovingAt)
        } else {
            idleSince = 0L
        }
        return emptyList()
    }

    /**
     * Läuft im festen Takt, damit ein verstummtes Band die Session beendet.
     * Ein großer Abstand zwischen zwei Takten heißt: die CPU hat geschlafen und
     * nicht das Band. Dann wird die Frist mitgeschoben statt die Session zu beenden.
     */
    fun onTick(at: Long): List<Event> {
        if (lastTickAt == 0L) {
            lastTickAt = at
            return emptyList()
        }
        val gap = at - lastTickAt
        lastTickAt = at
        if (gap > TICK_MS * 2) {
            lastFrameAt += gap
            return emptyList()
        }
        if (active && at - lastFrameAt >= STALL_MS) return finish(lastFrameAt)
        return emptyList()
    }

    private fun begin(frame: Live, at: Long, replace: Session?) {
        active = true
        prev = frame
        idleSince = 0L
        lastFrameAt = at
        lastMovingAt = at
        startWall = replace?.start ?: (at - frame.timeS * 1000L)
        replacing = replace
        hrSum = 0
        hrCount = 0
        hrMax = 0
        now = Now(frame.timeS, frame.distM, frame.speedKmh, frame.hr, frame.kcal)
    }

    private fun finish(at: Long): List<Event> {
        if (!active) return emptyList()
        val cur = now
        active = false
        now = null
        prev = null
        idleSince = 0L
        val replace = replacing
        replacing = null
        if (cur == null || cur.seconds < MIN_SECONDS || cur.distanceM < MIN_METRES) return emptyList()
        return listOf(
            Event.Finished(
                Session(
                    start = if (startWall > 0) startWall else at - cur.seconds * 1000L,
                    end = at,
                    seconds = cur.seconds,
                    distanceM = cur.distanceM,
                    kcal = cur.kcal,
                    hrAvg = if (hrCount > 0) (hrSum / hrCount).toInt() else 0,
                    hrMax = hrMax,
                ),
                replaces = replace,
            ),
        )
    }

    private fun fromFrame(frame: Live, at: Long, start: Long, hrAvg: Int, hrMax: Int) = Session(
        start = start,
        end = at,
        seconds = frame.timeS,
        distanceM = frame.distM,
        kcal = frame.kcal,
        hrAvg = hrAvg,
        hrMax = hrMax,
    )

    /** Die Session lief während der Trennung weiter und ist inzwischen zu Ende. */
    private fun continuing(before: Session, frame: Live, at: Long) =
        fromFrame(frame, at, before.start, before.hrAvg, before.hrMax)

    private fun adopted(frame: Live, at: Long) =
        fromFrame(frame, at, at - frame.timeS * 1000L, 0, 0)
}
