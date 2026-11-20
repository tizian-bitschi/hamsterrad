package de.tizian.hamsterrad

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Die Rollen der Papier-Palette. Warum diese Töne und warum nur zwei Textfarben tragen:
 * `docs/adr/0002-papier-statt-neon.md`.
 */
internal object Palette {
    /** Die Seite. */
    val Paper = Color(0xFFF7F3EE)

    /** Fläche, Balkenspur, Zeile im Verlauf. */
    val Sand = Color(0xFFEAE2DA)

    /** Tinte: die Zeit, Session-Werte, „löschen". */
    val Ink = Color(0xFF5C262E)

    /** Zweite Datenfarbe: Distanz, Tempo, Speed, HF, Aktionen. */
    val Forest = Color(0xFF3B5C54)

    /** Labels, Einheiten, Inaktives. */
    val Muted = Color(0xFF6F665D)

    /**
     * Auf [Ink] 4,3:1 — genug für Icon-Nabe und Kanten, nicht für Fließtext.
     * Auf Papier nur 2,5:1: dort bleibt Messing eine Haarlinie ohne Bedeutung.
     */
    val Brass = Color(0xFFC4934B)
}

/**
 * Newsreader 600, auf Ziffern, Komma, Punkt, Doppelpunkt und Gedankenstrich beschnitten.
 * Die Ziffern sind von Haus aus gleich breit (1285/2000 em), die laufende Uhr springt also nicht.
 */
internal val Numeral = FontFamily(Font(R.font.newsreader_600, FontWeight.SemiBold))

/** Material 3 braucht ein Farbschema für Dialoge und TextButtons; der Rest ist eigene Palette. */
internal val HamsterTheme = lightColorScheme(
    primary = Palette.Forest,
    onPrimary = Palette.Paper,
    secondary = Palette.Ink,
    onSecondary = Palette.Paper,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Sand,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Muted,
    outlineVariant = Palette.Sand,
    scrim = Palette.Ink,
    error = Palette.Ink,
    onError = Palette.Paper,
)
