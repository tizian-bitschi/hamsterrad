package de.tizian.hamsterrad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Die drei Orte der App. */
internal enum class Ziel(val titel: String) {
    COCKPIT("Cockpit"),
    VERLAUF("Verlauf"),
    EINSTELLUNGEN("Einstellungen"),
}

/** Die Hülle: ein Ziel zur Zeit, darunter die Leiste. */
@Composable
internal fun Shell(
    onAufzeichnung: (Boolean) -> Unit,
    onBerechtigungen: () -> Unit,
    onBenachrichtigungen: () -> Unit,
    onAkku: () -> Unit,
) {
    var ziel by rememberSaveable { mutableStateOf(Ziel.COCKPIT) }

    Column(Modifier.fillMaxSize().background(Palette.Paper).safeDrawingPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (ziel) {
                Ziel.COCKPIT -> Cockpit(
                    onAufzeichnung = onAufzeichnung,
                    onBerechtigungen = onBerechtigungen,
                )

                Ziel.VERLAUF -> VerlaufSeite()

                Ziel.EINSTELLUNGEN -> Einstellungen(
                    onAufzeichnung = onAufzeichnung,
                    onBenachrichtigungen = onBenachrichtigungen,
                    onAkku = onAkku,
                )
            }
        }
        Leiste(ziel) { ziel = it }
    }
}

/** Drei Wörter auf Sand, die aktive Wahl in Grün. */
@Composable
private fun Leiste(gewaehlt: Ziel, onWaehlen: (Ziel) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Palette.Ink.copy(alpha = 0.10f)),
        )
        Row(
            Modifier.fillMaxWidth().height(56.dp).background(Palette.Sand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ziel.entries.forEach { ziel ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onWaehlen(ziel) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ziel.titel,
                        color = if (ziel == gewaehlt) Palette.Forest else Palette.Muted,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}
