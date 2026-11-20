package de.tizian.hamsterrad

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Palette hat einen Töter: Messing sieht gut aus und trägt trotzdem keinen Text.
 * Dieser Test hält die Rollen aus `docs/adr/0002-papier-statt-neon.md` fest, damit
 * ein späterer Griff in die Farben nicht unbemerkt unter AA rutscht.
 */
class PaletteContrastTest {

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun `tinte und gruen tragen auf papier und sand`() {
        for (flaeche in listOf(Palette.Paper, Palette.Sand)) {
            for (farbe in listOf(Palette.Ink, Palette.Forest)) {
                assertTrue(
                    "$farbe auf $flaeche muss 4,5:1 halten, ist ${contrast(farbe, flaeche)}",
                    contrast(farbe, flaeche) >= 4.5,
                )
            }
        }
    }

    /** Warmgrau trägt nur auf Papier (5,09:1); auf Sand sind es 4,39:1 und damit zu wenig. */
    @Test
    fun `warmgrau traegt nur auf papier`() {
        assertTrue(contrast(Palette.Muted, Palette.Paper) >= 4.5)
        assertTrue(contrast(Palette.Muted, Palette.Sand) < 4.5)
    }

    @Test
    fun `messing bleibt zierde auf papier`() {
        assertTrue(contrast(Palette.Brass, Palette.Paper) < 3.0)
    }
}
