package de.tizian.hamsterrad

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val SVC_FFF0 = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
private val CHR_FFF1 = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
private val CHR_FFF2 = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
private val SVC_F100 = UUID.fromString("0000f100-0000-1000-8000-00805f9b34fb")
private val CHR_F101 = UUID.fromString("0000f101-0000-1000-8000-00805f9b34fb")
private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private val POLL_FRAME = byteArrayOf(0x02, 0x51, 0x51, 0x03)

data class DeviceRow(val name: String, val mac: String, val rssi: Int, val services: String)

object CaptureState {
    val status = mutableStateOf("Bereit")
    val devices = mutableStateListOf<DeviceRow>()
    val log = mutableStateListOf<String>()
}

/** Nur-Lese-Mitschnitt: verbindet, abonniert FFF1 und pollt FFF2. Schreibt nichts Steuerndes. */
object BleCapture {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var appContext: Context? = null

    private var gatt: BluetoothGatt? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var pollJob: Job? = null
    private var scanning = false
    private var writePending = false

    private val found = LinkedHashMap<String, BluetoothDevice>()
    private val entries = JSONArray()
    private var startedAt = 0L
    private var deviceLabel = ""

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    // ---------------------------------------------------------------- Log

    private fun record(kind: String, text: String = "", hex: String? = null) {
        val o = JSONObject()
        o.put("t", System.currentTimeMillis())
        o.put("kind", kind)
        if (text.isNotEmpty()) o.put("text", text)
        if (hex != null) o.put("hex", hex)
        synchronized(entries) { entries.put(o) }
        if (kind != "scan") {
            val line = if (hex != null) {
                "$kind $hex" + if (text.isEmpty()) "" else " · $text"
            } else {
                "$kind $text"
            }
            CaptureState.log.add(0, line)
            while (CaptureState.log.size > 400) CaptureState.log.removeAt(CaptureState.log.lastIndex)
        }
    }

    // --------------------------------------------------------------- Scan

    @SuppressLint("MissingPermission")
    fun startScan() {
        val ctx = appContext ?: return
        if (scanning) return
        val adapter = ctx.getSystemService(BluetoothManager::class.java).adapter ?: return
        if (!adapter.isEnabled) {
            CaptureState.status.value = "Bluetooth ist aus"
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: return

        scanning = true
        CaptureState.devices.clear()
        found.clear()
        CaptureState.status.value = "Suche Geräte …"
        record("info", "scan gestartet")
        scanner.startScan(scanCallback)

        scope.launch {
            delay(15_000)
            if (scanning) stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
        val ctx = appContext ?: return
        val adapter = ctx.getSystemService(BluetoothManager::class.java).adapter
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        CaptureState.status.value = "Suche beendet"
        record("info", "scan beendet")
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val rec = result.scanRecord
            val name = rec?.deviceName
                ?: runCatching { result.device.name }.getOrNull()
                ?: "(ohne Namen)"
            val services = rec?.serviceUuids
                ?.joinToString(" ") { it.uuid.toString().substring(4, 8) }
                .orEmpty()
            val row = DeviceRow(name, result.device.address, result.rssi, services)

            val index = CaptureState.devices.indexOfFirst { it.mac == row.mac }
            if (index >= 0) {
                CaptureState.devices[index] = row
            } else {
                CaptureState.devices.add(row)
                found[row.mac] = result.device
                val raw = rec?.bytes?.toHex().orEmpty()
                record(
                    "scan",
                    "$name ${row.mac} rssi=${row.rssi} svc=[$services] ad=$raw",
                )
            }
            CaptureState.devices.sortByDescending { it.rssi }
        }
    }

    // ------------------------------------------------------------ Verbindung

    @SuppressLint("MissingPermission")
    fun connect(mac: String) {
        val ctx = appContext ?: return
        val device = found[mac] ?: return
        stopScan()
        closeGatt()

        deviceLabel = runCatching { device.name }.getOrNull() ?: mac
        startedAt = System.currentTimeMillis()
        CaptureState.status.value = "Verbinde mit $deviceLabel …"
        record("info", "connect $deviceLabel $mac")
        gatt = device.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                CaptureState.status.value = "Verbunden, ermittle Dienste …"
                record("info", "connected status=$status")
                runCatching { g.discoverServices() }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                CaptureState.status.value = "Getrennt"
                record("info", "disconnected status=$status")
                stopPolling()
                runCatching { g.close() }
                if (gatt === g) gatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            record("info", "services status=$status")
            g.services.forEach { svc ->
                record("svc", "service ${svc.uuid}")
                svc.characteristics.forEach { c ->
                    record(
                        "svc",
                        "  char ${c.uuid} props=0x${Integer.toHexString(c.properties)}",
                    )
                }
            }

            var n: BluetoothGattCharacteristic? = null
            var w: BluetoothGattCharacteristic? = null

            val fff0 = g.getService(SVC_FFF0)
            if (fff0 != null) {
                n = fff0.getCharacteristic(CHR_FFF1)
                w = fff0.getCharacteristic(CHR_FFF2)
            }
            if (n == null || w == null) {
                val f101 = g.getService(SVC_F100)?.getCharacteristic(CHR_F101)
                if (f101 != null) {
                    n = f101
                    w = f101
                }
            }

            notifyChar = n
            writeChar = w
            if (n == null || w == null) {
                CaptureState.status.value = "FFF1/FFF2 nicht gefunden"
                record("info", "FFF1/FFF2 fehlen – anderes Gerät?")
                return
            }
            enableNotify(g)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            record("info", "descriptor ${d.uuid} status=$status")
            if (d.uuid == CCCD) startPolling()
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            status: Int,
        ) {
            writePending = false
            if (status != BluetoothGatt.GATT_SUCCESS) record("info", "write status=$status")
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            handleValue(c, c.value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleValue(c, value)
        }
    }

    private fun handleValue(c: BluetoothGattCharacteristic, value: ByteArray) {
        val xorOk = value.size >= 4 && value.drop(1).dropLast(1).fold(0) { acc, b ->
            acc xor (b.toInt() and 0xFF)
        } == 0
        record(
            "rx",
            "${c.uuid.toString().substring(4, 8)} len=${value.size} xor=${if (xorOk) "ok" else "BAD"}",
            value.toHex(),
        )
    }

    // ------------------------------------------------------------- Notify

    @SuppressLint("MissingPermission")
    private fun enableNotify(g: BluetoothGatt) {
        val c = notifyChar ?: return
        runCatching { g.setCharacteristicNotification(c, true) }
        val d = c.getDescriptor(CCCD)
        if (d == null) {
            record("info", "CCCD fehlt, polle trotzdem")
            startPolling()
            return
        }
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(d)
                }
            }
        }.getOrDefault(false)
        record("info", "notify einschalten ok=$started")
        if (!started) startPolling()
    }

    // --------------------------------------------------------------- Poll

    @SuppressLint("MissingPermission")
    private fun startPolling() {
        if (pollJob != null) return
        CaptureState.status.value = "Poll aktiv (450 ms)"
        record("info", "poll gestartet")
        pollJob = scope.launch {
            while (true) {
                val g = gatt ?: break
                val c = writeChar ?: break
                if (!writePending) {
                    writePending = true
                    val ok = runCatching { write(g, c, POLL_FRAME) }.getOrDefault(false)
                    if (!ok) writePending = false
                }
                delay(450)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        writePending = false
    }

    @SuppressLint("MissingPermission")
    private fun write(g: BluetoothGatt, c: BluetoothGattCharacteristic, data: ByteArray): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                c.value = data
                g.writeCharacteristic(c)
            }
        }
    }

    // ------------------------------------------------------------- Aufräumen

    @SuppressLint("MissingPermission")
    fun stopAll() {
        stopScan()
        stopPolling()
        closeGatt()
        CaptureState.status.value = "Gestoppt"
        saveCapture()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        val g = gatt ?: return
        runCatching { g.disconnect() }
        runCatching { g.close() }
        gatt = null
    }

    fun saveCapture() {
        val ctx = appContext ?: return
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "capture-$stamp.json")
        val root = JSONObject()
        root.put("device", deviceLabel)
        root.put("startedAt", startedAt)
        root.put("savedAt", System.currentTimeMillis())
        synchronized(entries) { root.put("entries", entries) }
        runCatching { file.writeText(root.toString()) }
            .onSuccess {
                CaptureState.status.value = "Gespeichert: ${file.name}"
                record("info", "gespeichert ${file.absolutePath} (${entries.length()} Einträge)")
            }
            .onFailure { record("info", "Speichern fehlgeschlagen: ${it.message}") }
    }
}

private fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
