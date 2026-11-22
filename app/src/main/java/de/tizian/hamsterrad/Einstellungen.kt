package de.tizian.hamsterrad

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Der Schalter, die Verbindung, das System und die Version. */
@Composable
internal fun Einstellungen(
    onAufzeichnung: (Boolean) -> Unit,
    onBenachrichtigungen: () -> Unit,
    onAkku: () -> Unit,
) {
    val context = LocalContext.current
    val an by Treadmill.recording
    val status by Treadmill.status
    val verbunden by Treadmill.connected
    val akkuFrei by Treadmill.batteryExempt
    val laeuft = Treadmill.now.value != null
    val zustand = Treadmill.state
    val bandName = Prefs.name(context)
    val bandAdresse = Prefs.address(context)
    val darfMelden = Notifications.erlaubt(context)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 26.dp),
    ) {
        Text("EINSTELLUNGEN", color = Palette.Muted, fontSize = 11.sp, letterSpacing = 5.sp)

        Abschnitt("AUFZEICHNUNG") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aufzeichnung", color = Palette.Ink, fontSize = 15.sp)
                    Text(
                        Recording.stellung(zustand),
                        color = Palette.Muted,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = an,
                    onCheckedChange = onAufzeichnung,
                    enabled = !laeuft,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Palette.Paper,
                        checkedTrackColor = Palette.Forest,
                        checkedBorderColor = Palette.Forest,
                        uncheckedThumbColor = Palette.Muted,
                        uncheckedTrackColor = Palette.Sand,
                        uncheckedBorderColor = Palette.Muted,
                        // Während einer Session ist der Schalter gesperrt, soll aber
                        // weiterhin als "an" zu erkennen sein.
                        disabledCheckedThumbColor = Palette.Paper,
                        disabledCheckedTrackColor = Palette.Forest.copy(alpha = 0.35f),
                        disabledCheckedBorderColor = Palette.Forest.copy(alpha = 0.35f),
                        disabledUncheckedThumbColor = Palette.Muted,
                        disabledUncheckedTrackColor = Palette.Sand,
                        disabledUncheckedBorderColor = Palette.Muted,
                    ),
                )
            }
            if (laeuft) {
                Text(
                    "Während einer Session lässt sich die Aufzeichnung nicht ausschalten.",
                    color = Palette.Muted,
                    fontSize = 11.sp,
                )
            }
        }

        Abschnitt("BAND") {
            Text(bandName ?: "Kein Band bekannt", color = Palette.Ink, fontSize = 15.sp)
            Text(bandAdresse ?: "Noch keine Verbindung", color = Palette.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(status, color = if (verbunden) Palette.Forest else Palette.Muted, fontSize = 12.sp)
            if (!an) {
                Text(
                    "Aufzeichnung ist aus — die App verbindet sich nicht.",
                    color = Palette.Muted,
                    fontSize = 11.sp,
                )
            } else {
                TextButton(
                    onClick = { Treadmill.reconnect() },
                    enabled = !laeuft,
                ) {
                    Text(
                        "Verbindung suchen",
                        color = if (laeuft) Palette.Muted else Palette.Forest,
                    )
                }
            }
        }

        Abschnitt("SYSTEM") {
            Zeile("Akku-Optimierung", if (akkuFrei) "ausgenommen" else "aktiv") {
                if (!akkuFrei) {
                    TextButton(onClick = onAkku) { Text("Zulassen", color = Palette.Forest) }
                }
            }
            if (!akkuFrei) {
                Text(
                    "Ohne Ausnahme kann Android den Dienst anhalten.",
                    color = Palette.Muted,
                    fontSize = 11.sp,
                )
            }
            Zeile("Benachrichtigungen", if (darfMelden) "erlaubt" else "nicht erlaubt") {
                if (!darfMelden) {
                    TextButton(onClick = onBenachrichtigungen) { Text("Erlauben", color = Palette.Forest) }
                }
            }
        }

        Abschnitt("ÜBER") {
            Zeile("Hamsterrad", "Version ${versionName(context)}")
        }
    }
}

@Composable
private fun Abschnitt(titel: String, inhalt: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(26.dp))
    Text(titel, color = Palette.Muted, fontSize = 10.sp, letterSpacing = 3.sp)
    Spacer(Modifier.height(8.dp))
    Column(content = inhalt)
}

@Composable
private fun Zeile(label: String, wert: String, aktion: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Palette.Ink, fontSize = 15.sp)
            Text(wert, color = Palette.Muted, fontSize = 12.sp)
        }
        if (aktion != null) aktion()
    }
}

private fun versionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: ""
