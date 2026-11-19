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
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private val SVC_FFF0 = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
private val CHR_FFF1 = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
private val CHR_FFF2 = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
private val POLL_FRAME = byteArrayOf(0x02, 0x51, 0x51, 0x03)

private const val IDLE_STOP_MS = 20_000L
private const val START_SPEED = 0.5
private const val STOP_SPEED = 0.2

/** Ein Rahmen des Bands, so wie es ihn schickt. */
data class Live(
    val status: Int,
    val speedKmh: Double,
    val timeS: Int,
    val distM: Int,
    val kcal: Double,
    val hr: Int,
)

/** Laufende Session, relativ zur Startmarke. */
data class Now(
    val seconds: Int,
    val distanceM: Int,
    val speedKmh: Double,
    val hr: Int,
    val kcal: Double,
)

data class Session(
    val start: Long,
    val end: Long,
    val seconds: Int,
    val distanceM: Int,
    val kcal: Double,
    val hrAvg: Int,
    val hrMax: Int,
)

object Treadmill {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var ctx: Context? = null
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var pollJob: Job? = null
    private var scanning = false
    private var writePending = false

    private var baseline: Live? = null
    private var idleSince = 0L
    private var hrSum = 0L
    private var hrCount = 0
    private var hrMax = 0

    val live = mutableStateOf<Live?>(null)
    val now = mutableStateOf<Now?>(null)
    val connected = mutableStateOf(false)
    val status = mutableStateOf("Suche Laufband …")
    val lastDevice = mutableStateOf("")

    fun start(context: Context) {
        if (ctx == null) ctx = context.applicationContext
        Sessions.load(context)
        if (!scanning && gatt == null) scan()
    }

    // ---------------------------------------------------------------- Scan

    @SuppressLint("MissingPermission")
    fun scan() {
        val c = ctx ?: return
        if (scanning) return
        val adapter = c.getSystemService(BluetoothManager::class.java).adapter ?: return
        if (!adapter.isEnabled) {
            status.value = "Bluetooth ist aus"
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: return
        scanning = true
        status.value = "Suche Laufband …"
        scanner.startScan(scanCallback)
        scope.launch {
            delay(12_000)
            if (scanning) stopScan()
            if (gatt == null) status.value = "Band nicht gefunden"
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        val c = ctx ?: return
        val adapter = c.getSystemService(BluetoothManager::class.java).adapter
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (gatt != null) return
            val name = result.scanRecord?.deviceName
                ?: runCatching { result.device.name }.getOrNull()
                ?: return
            val advertisesFff0 = result.scanRecord?.serviceUuids
                ?.any { it.uuid == SVC_FFF0 } == true
            val looksLikeTreadmill = advertisesFff0 ||
                name.startsWith("FIT-") || name.startsWith("F37s-") ||
                name.startsWith("sProRun-") || name.startsWith("sWalk") ||
                name.startsWith("sPad")
            if (!looksLikeTreadmill) return
            status.value = "Verbinde mit $name …"
            connect(result.device)
        }
    }

    // --------------------------------------------------------------- GATT

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        stopScan()
        close()
        lastDevice.value = runCatching { device.name }.getOrNull() ?: device.address
        gatt = device.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private val callback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, s: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected.value = true
                status.value = "Verbunden, ermittle Dienste …"
                runCatching { g.discoverServices() }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected.value = false
                status.value = "Getrennt"
                stopPolling()
                runCatching { g.close() }
                if (gatt === g) gatt = null
                finishSession()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, s: Int) {
            val svc = g.getService(SVC_FFF0)
            val notify = svc?.getCharacteristic(CHR_FFF1)
            writeChar = svc?.getCharacteristic(CHR_FFF2)
            if (notify == null || writeChar == null) {
                status.value = "Laufband-Dienst fehlt"
                return
            }
            runCatching { g.setCharacteristicNotification(notify, true) }
            val d = notify.getDescriptor(CCCD)
            if (d == null) {
                status.value = "Bereit"
                startPolling()
                return
            }
            val ok = runCatching {
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
            if (!ok) startPolling()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, s: Int) {
            status.value = "Bereit"
            startPolling()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, s: Int) {
            writePending = false
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            onFrame(c.value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onFrame(value)
        }
    }

    // --------------------------------------------------------------- Poll

    @SuppressLint("MissingPermission")
    private fun startPolling() {
        if (pollJob != null) return
        pollJob = scope.launch {
            while (true) {
                val g = gatt
                val c = writeChar
                if (g == null || c == null) break
                if (!writePending) {
                    writePending = true
                    val ok = runCatching { write(g, c) }.getOrDefault(false)
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
    private fun write(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, POLL_FRAME, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) ==
                BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                c.value = POLL_FRAME
                g.writeCharacteristic(c)
            }
        }
    }

    // -------------------------------------------------------------- Rahmen

    private fun onFrame(raw: ByteArray) {
        val l = parse(raw) ?: return
        live.value = l
        val base = baseline
        if (base == null) {
            if (l.speedKmh >= START_SPEED) begin(l)
            return
        }
        val seconds = (l.timeS - base.timeS).coerceAtLeast(0)
        val metres = (l.distM - base.distM).coerceAtLeast(0)
        now.value = Now(
            seconds = seconds,
            distanceM = metres,
            speedKmh = l.speedKmh,
            hr = l.hr,
            kcal = (l.kcal - base.kcal).coerceAtLeast(0.0),
        )
        if (l.hr in 30..220) {
            hrSum += l.hr
            hrCount++
            if (l.hr > hrMax) hrMax = l.hr
        }
        if (l.speedKmh < STOP_SPEED) {
            if (idleSince == 0L) idleSince = System.currentTimeMillis()
            else if (System.currentTimeMillis() - idleSince >= IDLE_STOP_MS) finishSession()
        } else {
            idleSince = 0L
        }
    }

    private fun begin(l: Live) {
        baseline = l
        idleSince = 0L
        hrSum = 0
        hrCount = 0
        hrMax = 0
        now.value = Now(0, 0, l.speedKmh, l.hr, 0.0)
        status.value = "Session läuft"
    }

    private fun finishSession() {
        val base = baseline ?: return
        val cur = now.value ?: return
        baseline = null
        now.value = null
        if (cur.seconds < 10 || cur.distanceM < 5) {
            status.value = "Bereit"
            return
        }
        val s = Session(
            start = System.currentTimeMillis() - cur.seconds * 1000L,
            end = System.currentTimeMillis(),
            seconds = cur.seconds,
            distanceM = cur.distanceM,
            kcal = cur.kcal,
            hrAvg = if (hrCount > 0) (hrSum / hrCount).toInt() else 0,
            hrMax = hrMax,
        )
        ctx?.let { Sessions.append(it, s) }
        status.value = "Session gespeichert"
    }

    private fun parse(b: ByteArray): Live? {
        if (b.size < 17) return null
        if (b[0] != 0x02.toByte() || b[1] != 0x51.toByte()) return null
        if (b[b.size - 1] != 0x03.toByte()) return null
        var xor = 0
        for (i in 1 until b.size - 2) xor = xor xor (b[i].toInt() and 0xFF)
        if (xor != (b[b.size - 2].toInt() and 0xFF)) return null
        return Live(
            status = b[2].toInt() and 0xFF,
            speedKmh = (b[3].toInt() and 0xFF) / 10.0,
            timeS = u16(b, 5),
            distM = u16(b, 7),
            kcal = u16(b, 9) / 10.0,
            hr = b[13].toInt() and 0xFF,
        )
    }

    private fun u16(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    @SuppressLint("MissingPermission")
    private fun close() {
        stopPolling()
        val g = gatt ?: return
        runCatching { g.disconnect() }
        runCatching { g.close() }
        gatt = null
        connected.value = false
    }
}
