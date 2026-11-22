package de.tizian.hamsterrad

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingTest {

    private val now = Now(seconds = 92, distanceM = 310, speedKmh = 1.2, hr = 130, kcal = 12.0)

    @Test
    fun `satz fuer jeden zustand`() {
        assertEquals("Aufzeichnung aus", Recording.notification(RecordingState.OFF, null))
        assertEquals(
            "Bluetooth-Berechtigung fehlt",
            Recording.notification(RecordingState.MISSING_PERMISSION, null),
        )
        assertEquals("Bluetooth ist aus", Recording.notification(RecordingState.NO_BLUETOOTH, null))
        assertEquals("Aufzeichnung bereit", Recording.notification(RecordingState.READY, null))
        assertEquals(
            "Aufzeichnung bereit · Band nicht in Reichweite",
            Recording.notification(RecordingState.NO_BAND, null),
        )
    }

    @Test
    fun `laufende session nennt zeit und distanz`() {
        assertEquals("Session läuft: 01:32 · 0,31 km", Recording.notification(RecordingState.RUNNING, now))
    }

    @Test
    fun `wort fuer den schalter in den einstellungen`() {
        assertEquals("aus", Recording.stellung(RecordingState.OFF))
        assertEquals("läuft", Recording.stellung(RecordingState.READY))
        assertEquals("Session läuft", Recording.stellung(RecordingState.RUNNING))
        assertEquals("Berechtigung fehlt", Recording.stellung(RecordingState.MISSING_PERMISSION))
        assertEquals("Bluetooth aus", Recording.stellung(RecordingState.NO_BLUETOOTH))
        assertEquals("wartet auf Band", Recording.stellung(RecordingState.NO_BAND))
    }
}
