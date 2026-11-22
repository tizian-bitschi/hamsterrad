package de.tizian.hamsterrad

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `durchschnittstempo aus dauer und distanz`() {
        assertEquals("8:20", paceOf(seconds = 600, metres = 1200))
    }

    @Test
    fun `ohne strecke gibt es kein tempo`() {
        assertEquals("–", paceOf(seconds = 600, metres = 0))
    }

    @Test
    fun `ohne dauer gibt es kein tempo`() {
        assertEquals("–", paceOf(seconds = 0, metres = 500))
    }
}
