package de.tizian.hamsterrad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {

    private fun frame(speed: Double, time: Int, dist: Int, hr: Int = 132) =
        Live(speed, time, dist, 12.0, hr)

    private fun finished(events: List<Event>): Event.Finished =
        events.filterIsInstance<Event.Finished>().single()

    @Test
    fun `startet bei laufendem band`() {
        val engine = SessionEngine()
        val events = engine.onFrame(frame(1.2, 4, 12), 10_000)
        assertEquals(listOf<Event>(Event.Started()), events)
        assertEquals(4, engine.now?.seconds)
    }

    @Test
    fun `startet nicht bei stillstand`() {
        val engine = SessionEngine()
        assertTrue(engine.onFrame(frame(0.0, 0, 0), 10_000).isEmpty())
        assertNull(engine.now)
    }

    @Test
    fun `beendet nach zwanzig sekunden stillstand`() {
        val engine = SessionEngine()
        engine.onFrame(frame(1.2, 100, 500), 0)
        engine.onFrame(frame(1.2, 101, 510), 1_000)
        engine.onFrame(frame(0.0, 102, 515), 2_000)
        val events = engine.onFrame(frame(0.0, 103, 518), 22_000)
        assertEquals(103, finished(events).session.seconds)
        assertEquals(1_000L, finished(events).session.end)
    }

    @Test
    fun `laeuft bei kurzer pause weiter`() {
        val engine = SessionEngine()
        engine.onFrame(frame(1.2, 100, 500), 0)
        engine.onFrame(frame(0.0, 101, 500), 1_000)
        assertTrue(engine.onFrame(frame(0.0, 102, 500), 20_000).isEmpty())
        assertEquals(102, engine.now?.seconds)
    }

    @Test
    fun `zaehlerreset trennt sessions`() {
        val engine = SessionEngine()
        engine.onFrame(frame(1.2, 100, 500), 0)
        val events = engine.onFrame(frame(1.2, 5, 20), 10_000)
        assertEquals(2, events.size)
        assertEquals(100, finished(events).session.seconds)
        assertEquals(500, finished(events).session.distanceM)
        assertEquals(Event.Started(), events[1])
        assertEquals(5, engine.now?.seconds)
    }

    @Test
    fun `verstummtes band beendet die session`() {
        val engine = SessionEngine()
        engine.onFrame(frame(1.2, 30, 200), 1_000)
        var events = emptyList<Event>()
        for (at in listOf(6_000L, 11_000L, 16_000L, 21_000L, 26_000L)) events = engine.onTick(at)
        assertEquals(1, events.size)
        assertEquals(1_000L, finished(events).session.end)
        assertEquals(30, finished(events).session.seconds)
        assertNull(engine.now)
    }

    @Test
    fun `schlafende cpu beendet die session nicht`() {
        val engine = SessionEngine()
        engine.onFrame(frame(1.2, 30, 200), 1_000)
        engine.onTick(6_000)
        assertTrue(engine.onTick(40_000).isEmpty())
        assertTrue(engine.onTick(45_000).isEmpty())
        assertEquals(30, engine.now?.seconds)
    }

    @Test
    fun `uebernimmt eine fertige session`() {
        val engine = SessionEngine()
        val at = 1_000_000L
        val event = finished(engine.onFrame(frame(0.0, 1_500, 4_200), at))
        assertTrue(event.adopted)
        assertEquals(at - 1_500_000L, event.session.start)
        assertEquals(at, event.session.end)
        assertEquals(1_500, event.session.seconds)
        assertEquals(4_200, event.session.distanceM)
        assertNull(engine.now)
    }

    @Test
    fun `uebernimmt dieselbe session nicht zweimal`() {
        val engine = SessionEngine()
        engine.remember(Session(0, 0, seconds = 1_500, distanceM = 4_200, kcal = 0.0, hrAvg = 0, hrMax = 0))
        assertTrue(engine.onFrame(frame(0.0, 1_500, 4_200), 1_000_000).isEmpty())
    }

    @Test
    fun `setzt eine session nach einer trennung fort`() {
        val engine = SessionEngine()
        val t0 = 1_000_000L
        engine.onFrame(frame(1.2, 100, 500), t0)
        engine.onFrame(frame(1.2, 101, 510), t0 + 1_000)
        val saved = finished(engine.onDisconnected(t0 + 2_000)).session

        engine.onConnected()
        val started = engine.onFrame(frame(1.2, 130, 650), t0 + 4_000)
            .filterIsInstance<Event.Started>()
            .single()
        assertEquals(saved, started.replaces)
        engine.onFrame(frame(0.0, 131, 650), t0 + 5_000)
        val ended = finished(engine.onFrame(frame(0.0, 131, 650), t0 + 30_000))

        assertEquals(saved.start, ended.session.start)
        assertEquals(saved, ended.replaces)
        assertEquals(131, ended.session.seconds)
        assertEquals(650, ended.session.distanceM)
    }

    @Test
    fun `ersetzt die session wenn sie waehrend der trennung endet`() {
        val engine = SessionEngine()
        val t0 = 1_000_000L
        engine.onFrame(frame(1.2, 100, 500), t0)
        engine.onFrame(frame(1.2, 101, 510), t0 + 1_000)
        val saved = finished(engine.onDisconnected(t0 + 2_000)).session

        engine.onConnected()
        val ended = finished(engine.onFrame(frame(0.0, 400, 2_000), t0 + 60_000))

        assertTrue(ended.adopted)
        assertEquals(saved, ended.replaces)
        assertEquals(saved.start, ended.session.start)
        assertEquals(400, ended.session.seconds)
        assertEquals(2_000, ended.session.distanceM)
        assertEquals(t0 + 60_000, ended.session.end)
    }

    @Test
    fun `ein leerer verbindungsverlust verliert die gemerkte session nicht`() {
        val engine = SessionEngine()
        val t0 = 1_000_000L
        engine.onFrame(frame(1.2, 100, 500), t0)
        val saved = finished(engine.onDisconnected(t0 + 1_000)).session

        engine.onConnected()
        engine.onDisconnected(t0 + 2_000)
        engine.onConnected()

        val ended = finished(engine.onFrame(frame(0.0, 400, 2_000), t0 + 60_000))
        assertEquals(saved, ended.replaces)
    }

    @Test
    fun `eine pause loescht die gemerkte session nicht`() {
        val engine = SessionEngine()
        val t0 = 1_000_000L
        engine.onFrame(frame(1.2, 100, 500), t0)
        val saved = finished(engine.onDisconnected(t0 + 1_000)).session

        engine.reset()
        engine.onConnected()

        val ended = finished(engine.onFrame(frame(0.0, 400, 2_000), t0 + 60_000))
        assertEquals(saved, ended.replaces)
    }

    @Test
    fun `setzt nach einem prozessneustart die gespeicherte session fort`() {
        val engine = SessionEngine()
        val t0 = 1_000_000L
        val saved = Session(t0, t0 + 1_000, seconds = 100, distanceM = 500, kcal = 12.0, hrAvg = 60, hrMax = 70)
        engine.adopt(saved)
        engine.onConnected()

        val ended = finished(engine.onFrame(frame(0.0, 400, 2_000), t0 + 60_000))
        assertEquals(saved, ended.replaces)
    }

    @Test
    fun `meldet nach einem prozessneustart dieselbe session nicht erneut`() {
        val engine = SessionEngine()
        val t0 = 1_000_000L
        val saved = Session(t0, t0 + 1_000, seconds = 100, distanceM = 500, kcal = 12.0, hrAvg = 60, hrMax = 70)
        engine.adopt(saved)
        engine.onConnected()

        assertTrue(engine.onFrame(frame(0.0, 100, 500), t0 + 60_000).isEmpty())
    }

    @Test
    fun `beginnt nach einem zaehlerreset neu`() {
        val engine = SessionEngine()
        val t0 = 1_000_000L
        engine.onFrame(frame(1.2, 100, 500), t0)
        finished(engine.onDisconnected(t0 + 1_000))

        engine.onConnected()
        assertEquals(listOf<Event>(Event.Started()), engine.onFrame(frame(1.2, 5, 20), t0 + 10_000))
        assertEquals(5, engine.now?.seconds)
    }

    @Test
    fun `uebernimmt keine zu kurze session`() {
        val engine = SessionEngine()
        assertTrue(engine.onFrame(frame(0.0, 4, 3), 1_000_000).isEmpty())
    }

    @Test
    fun `verbindungsverlust beendet die session`() {
        val engine = SessionEngine()
        engine.onFrame(frame(1.2, 20, 100), 0)
        val events = engine.onDisconnected(5_000)
        assertEquals(5_000L, finished(events).session.end)
        assertEquals(20, finished(events).session.seconds)
    }

    @Test
    fun `verwirft eine zu kurze session`() {
        val engine = SessionEngine()
        engine.onFrame(frame(1.2, 0, 0), 0)
        engine.onFrame(frame(0.0, 8, 3), 1_000)
        assertTrue(engine.onFrame(frame(0.0, 8, 3), 30_000).isEmpty())
        assertNull(engine.now)
    }
}
