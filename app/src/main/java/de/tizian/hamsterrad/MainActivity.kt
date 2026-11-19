package de.tizian.hamsterrad

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var pendingAction: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.isNotEmpty() && grants.values.all { it }) pendingAction?.invoke()
            pendingAction = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BleCapture.init(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    App(onScan = { ensurePermissions { BleCapture.startScan() } })
                }
            }
        }
    }

    private fun ensurePermissions(action: () -> Unit) {
        val needed = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@Composable
private fun App(onScan: () -> Unit) {
    val status by CaptureState.status
    val devices = CaptureState.devices
    val log = CaptureState.log

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("Hamsterrad · Diagnose", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(status, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onScan) { Text("Scan") }
            OutlinedButton(onClick = { BleCapture.stopAll() }) { Text("Stopp + speichern") }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Gerät antippen verbindet und startet den Mitschnitt.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            devices.forEach { d ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { BleCapture.connect(d.mac) }
                        .padding(vertical = 6.dp),
                ) {
                    Text("${d.name}   ${d.rssi} dBm", fontSize = 14.sp)
                    Text(
                        "${d.mac}   ${d.services}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Mitschnitt", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(log) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }
    }
}
