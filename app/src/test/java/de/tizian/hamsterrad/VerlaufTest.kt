package de.tizian.hamsterrad

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class VerlaufTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun session(start: String, meter: Int, seconds: Int = 600) = Session(
        start = at(start),
        end = at(start) + seconds * 1_000L,
        seconds = seconds,
        distanceM = meter,
        kcal = 12.0,
        hrAvg = 130,
        hrMax = 150,
    )

    /** Sonntag, 22. November 2026, 12:00. */
    private val jetzt = at("2026-11-22T12:00:00")

    @Test
    fun `tagseimer kennt sieben tage und zaehlt ab mitternacht`() {
        val balken = Verlauf.balken(
            Zeitraum.TAG,
            jetzt,
            listOf(
                session("2026-11-22T08:00", 1200),
                session("2026-11-21T23:59", 800),
                session("2026-11-15T10:00", 999),
            ),
            zone,
        )

        assertEquals(7, balken.size)
        assertEquals("22.11.", balken[6].label)
        assertEquals(1200, balken[6].meter)
        assertEquals(1, balken[6].sessions)
        assertEquals("21.11.", balken[5].label)
        assertEquals(800, balken[5].meter)
        assertEquals(2000, balken.sumOf { it.meter })
    }

    @Test
    fun `wocheneimer beginnt montag und reicht vier wochen`() {
        val balken = Verlauf.balken(
            Zeitraum.WOCHE,
            jetzt,
            listOf(
                session("2026-11-16T00:30", 100),
                session("2026-11-15T23:30", 200),
                session("2026-10-26T10:00", 300),
                session("2026-10-25T23:59", 400),
            ),
            zone,
        )

        assertEquals(listOf("KW 44", "KW 45", "KW 46", "KW 47"), balken.map { it.label })
        assertEquals(listOf(300, 0, 200, 100), balken.map { it.meter })
    }

    @Test
    fun `monateimer reicht zwoelf monate`() {
        val balken = Verlauf.balken(
            Zeitraum.MONAT,
            jetzt,
            listOf(
                session("2026-11-22T09:00", 500),
                session("2026-11-01T00:00", 100),
                session("2026-10-31T23:59", 200),
                session("2025-12-01T10:00", 300),
                session("2025-11-30T23:59", 400),
            ),
            zone,
        )

        assertEquals(12, balken.size)
        assertEquals("Dez", balken[0].label)
        assertEquals(300, balken[0].meter)
        assertEquals("Okt", balken[10].label)
        assertEquals(200, balken[10].meter)
        assertEquals("Nov", balken[11].label)
        assertEquals(600, balken[11].meter)
    }

    @Test
    fun `summe folgt dem gewaehlten zeitraum`() {
        val sessions = listOf(
            session("2026-11-22T08:00", 1200, seconds = 600),
            session("2026-10-27T10:00", 300, seconds = 900),
        )

        assertEquals(
            Summe(1, 1200, 600),
            Verlauf.summe(Verlauf.balken(Zeitraum.TAG, jetzt, sessions, zone)),
        )
        assertEquals(
            Summe(2, 1500, 1500),
            Verlauf.summe(Verlauf.balken(Zeitraum.WOCHE, jetzt, sessions, zone)),
        )
    }

    @Test
    fun `summenzeile nennt anzahl distanz und dauer`() {
        assertEquals("3 Sessions · 5,42 km · 1:12:30", Verlauf.summenText(Summe(3, 5420, 4350)))
        assertEquals("1 Session · 5,42 km · 01:00", Verlauf.summenText(Summe(1, 5420, 60)))
        assertEquals("0 Sessions · 0,00 km · 00:00", Verlauf.summenText(Summe(0, 0, 0)))
    }

    @Test
    fun `monatskopf nennt monat und jahr`() {
        assertEquals("November 2026", Verlauf.monatsKopf(YearMonth.of(2026, 11)))
        assertEquals("März 2025", Verlauf.monatsKopf(YearMonth.of(2025, 3)))
    }
}
