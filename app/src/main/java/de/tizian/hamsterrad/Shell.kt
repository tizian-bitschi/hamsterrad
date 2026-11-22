package de.tizian.hamsterrad

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
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
    onBluetooth: () -> Unit,
    onBenachrichtigungen: () -> Unit,
    onAkku: () -> Unit,
) {
    var ziel by rememberSaveable { mutableStateOf(Ziel.COCKPIT) }

    // Zurück führt immer erst ins Cockpit, wie in jeder App mit unterer Leiste.
    BackHandler(enabled = ziel != Ziel.COCKPIT) { ziel = Ziel.COCKPIT }

    Column(Modifier.fillMaxSize().background(Palette.Paper).safeDrawingPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (ziel) {
                Ziel.COCKPIT -> Cockpit(
                    onAufzeichnung = onAufzeichnung,
                    onBerechtigungen = onBerechtigungen,
                    onBluetooth = onBluetooth,
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

/** Drei Wörter auf Sand: das gewählte in Grün und größer, die anderen in Tinte. */
@Composable
private fun Leiste(gewaehlt: Ziel, onWaehlen: (Ziel) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).background(Palette.Sand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ziel.entries.forEach { ziel ->
                val gewaehltHier = ziel == gewaehlt
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onWaehlen(ziel) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ziel.titel,
                        // Warmgrau trägt auf Sand keine 4,5:1; die Auswahl unterscheidet
                        // sich zusätzlich in der Größe, nicht nur in der Farbe.
                        color = if (gewaehltHier) Palette.Forest else Palette.Ink,
                        fontSize = if (gewaehltHier) 12.sp else 11.sp,
                        fontWeight = if (gewaehltHier) FontWeight.SemiBold else FontWeight.Normal,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}
