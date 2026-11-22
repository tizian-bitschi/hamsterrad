package de.tizian.hamsterrad

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import android.os.PowerManager
import android.util.Log
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

private const val POLL_MS = 450L
private const val ARM_BACKOFF_MS = 10_000L
private const val RECONNECT_MS = 3_000L
private const val SCAN_MS = 12_000L
private const val SCAN_RETRY_MS = 60_000L
private const val PENDING_NOTICE_MS = 2 * 60_000L
private const val WAKELOCK_TAG = "hamsterrad:band"

/**
 * Die Verbindung zum Band. Läuft im Prozess des Dienstes, damit die Aufzeichnung
 * ein geschlossenes Fenster und einen gesperrten Bildschirm überlebt.
 */
object Treadmill {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val engine = SessionEngine()

    private var ctx: Context? = null
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var pollJob: Job? = null
    private var tickJob: Job? = null
    private var scanning = false
    private var writePending = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var nextArmAt = 0L
    private var pendingNotice: Session? = null
    private var pendingSince = 0L

    val live = mutableStateOf<Live?>(null)
    val now = mutableStateOf<Now?>(null)
    val connected = mutableStateOf(false)
    val status = mutableStateOf("Suche Laufband …")
    val recording = mutableStateOf(true)
    val serviceRunning = mutableStateOf(false)
    val bluetoothOff = mutableStateOf(false)
    val batteryExempt = mutableStateOf(true)
    val permissionMissing = mutableStateOf(false)

    /** Was die Aufzeichnung gerade tut. */
    val state: RecordingState
        get() = when {
            permissionMissing.value -> RecordingState.MISSING_PERMISSION
            !serviceRunning.value -> RecordingState.OFF
            bluetoothOff.value -> RecordingState.NO_BLUETOOTH
            now.value != null -> RecordingState.RUNNING
            connected.value -> RecordingState.READY
            else -> RecordingState.NO_BAND
        }

    /** Liest den gespeicherten Schalterzustand, bevor der erste Rahmen gezeichnet wird. */
    fun prepare(context: Context) {
        ctx = context.applicationContext
        recording.value = Prefs.recording(context)
        status.value = if (recording.value) "Suche Laufband …" else "Aufzeichnung aus"
    }

    /** Vom Dienst aufgerufen. Mehrfachaufrufe sind harmlos. */
    fun start(context: Context) {
        ctx = context.applicationContext
        Sessions.load(context)
        recording.value = Prefs.recording(context)
        engine.adopt(Sessions.all.firstOrNull())
        serviceRunning.value = true
        if (tickJob == null) {
            tickJob = scope.launch {
                while (true) {
                    delay(TICK_MS)
                    tick()
                }
            }
        }
        // Kam das Ausschalten zwischen Dienststart und hier, wird nicht mehr verbunden.
        if (!recording.value) {
            updateNotification()
            return
        }
        arm()
        updateNotification()
    }

    /** Vom Dienst beim Beenden aufgerufen. */
    fun stop() {
        close()
        tickJob?.cancel()
        tickJob = null
        serviceRunning.value = false
        status.value = "Aufzeichnung aus"
    }

    /** Schaltet die Aufzeichnung. Während einer Session ist beides gesperrt. */
    fun setRecording(context: Context, an: Boolean) {
        if (now.value != null) return
        // Ausschalten, wenn schon aus, ändert nichts. Einschalten ist wiederholbar,
        // auch wenn der Dienst früher gestorben ist.
        if (!an && !recording.value) return
        Prefs.setRecording(context, an)
        recording.value = an
        if (an) {
            engine.reset()
            now.value = null
            nextArmAt = 0
            TreadmillService.start(context)
        } else {
            TreadmillService.stop(context)
        }
        updateNotification()
    }

    /** Für den Knopf in den Einstellungen: einmal suchen statt nur warten. */
    fun reconnect() {
        // Während einer Session wäre das ein Abbruch mit Umweg.
        if (!recording.value || now.value != null) return
        close()
        nextArmAt = 0
        scan()
    }

    // ---------------------------------------------------------------- Suchen

    private fun bluetoothAdapter(c: Context) =
        c.getSystemService(BluetoothManager::class.java)?.adapter

    /** Liefert den Adapter, oder null und meldet den Zustand ins Cockpit. */
    @SuppressLint("MissingPermission")
    private fun readyAdapter(c: Context): BluetoothAdapter? {
        val adapter = bluetoothAdapter(c)
        if (adapter == null || !adapter.isEnabled) {
            bluetoothOff.value = true
            status.value = "Bluetooth ist aus"
            return null
        }
        bluetoothOff.value = false
        return adapter
    }

    @SuppressLint("MissingPermission")
    private fun arm() {
        val c = ctx ?: return
        if (gatt != null || scanning) return
        nextArmAt = System.currentTimeMillis() + ARM_BACKOFF_MS
        val adapter = readyAdapter(c) ?: return
        val address = Prefs.address(c)
        if (address == null) {
            scan()
            return
        }
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        status.value = "Warte auf ${Prefs.name(c) ?: address}"
        gatt = runCatching {
            device.connectGatt(c, true, callback, BluetoothDevice.TRANSPORT_LE)
        }.onFailure { Log.w("Treadmill", "Verbinden fehlgeschlagen", it) }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun scan() {
        val c = ctx ?: return
        if (scanning || gatt != null) return
        val adapter = readyAdapter(c) ?: return
        val scanner = adapter.bluetoothLeScanner ?: return
        scanning = true
        status.value = "Suche Laufband …"
        runCatching { scanner.startScan(scanCallback) }
        scope.launch {
            delay(SCAN_MS)
            if (!scanning) return@launch
            stopScan()
            if (gatt == null) {
                status.value = "Band nicht gefunden"
                // Ohne bekannte Adresse nicht im Kreis suchen: das kostet nur Strom.
                nextArmAt = System.currentTimeMillis() + SCAN_RETRY_MS
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        val c = ctx ?: return
        val adapter = c.getSystemService(BluetoothManager::class.java)?.adapter
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!recording.value || gatt != null) return
            val c = ctx ?: return
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
            Prefs.setDevice(c, result.device.address, name)
            status.value = "Verbinde mit $name …"
            stopScan()
            nextArmAt = 0
            arm()
        }
    }

    // ----------------------------------------------------------------- GATT

    private val callback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, stateStatus: Int, newState: Int) {
            if (gatt !== g) return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected.value = true
                bluetoothOff.value = false
                writeChar = null
                status.value = "Verbunden, ermittle Dienste …"
                acquireWakeLock()
                engine.onConnected()
                runCatching { g.discoverServices() }
                updateNotification()
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt = null
                onLinkLost()
                runCatching { g.close() }
                status.value = "Band getrennt"
                // Ein sauberer Abschied (Band aus) darf schnell wieder warten,
                // ein gescheiterter Versuch braucht Abstand.
                val wait = if (stateStatus == BluetoothGatt.GATT_SUCCESS) RECONNECT_MS else ARM_BACKOFF_MS
                nextArmAt = System.currentTimeMillis() + wait
                updateNotification()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, stateStatus: Int) {
            if (gatt !== g) return
            val svc = g.getService(SVC_FFF0)
            val notify = svc?.getCharacteristic(CHR_FFF1)
            writeChar = svc?.getCharacteristic(CHR_FFF2)
            if (notify == null || writeChar == null) {
                status.value = "Laufband-Dienst fehlt"
                return
            }
            runCatching { g.setCharacteristicNotification(notify, true) }
            val descriptor = notify.getDescriptor(CCCD)
            if (descriptor == null) {
                status.value = "Bereit"
                startPolling()
                return
            }
            val ok = runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                        BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        g.writeDescriptor(descriptor)
                    }
                }
            }.getOrDefault(false)
            if (!ok) startPolling()
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            stateStatus: Int,
        ) {
            status.value = "Bereit"
            startPolling()
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            stateStatus: Int,
        ) {
            writePending = false
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val value = c.value ?: return
            scope.launch { onFrame(value) }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            // Die Rahmen kommen auf einem Binder-Thread an; der Zustand gehört dem Main-Thread.
            scope.launch { onFrame(value) }
        }
    }

    // ----------------------------------------------------------------- Poll

    @SuppressLint("MissingPermission")
    private fun startPolling() {
        if (pollJob != null) return
        pollJob = scope.launch {
            while (true) {
                val g = gatt
                val ch = writeChar
                if (g == null || ch == null) break
                if (!writePending) {
                    writePending = true
                    if (!runCatching { write(g, ch) }.getOrDefault(false)) writePending = false
                }
                delay(POLL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        writePending = false
        writeChar = null
    }

    @SuppressLint("MissingPermission")
    private fun write(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(
                characteristic,
                POLL_FRAME,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                characteristic.value = POLL_FRAME
                g.writeCharacteristic(characteristic)
            }
        }
    }

    // --------------------------------------------------------------- Rahmen

    private fun onFrame(raw: ByteArray) {
        // Ein Rahmen kann eintreffen, während die Aufzeichnung gerade ausgeschaltet wird.
        if (!recording.value) return
        val frame = parse(raw) ?: return
        live.value = frame
        dispatch(engine.onFrame(frame, System.currentTimeMillis()))
    }

    /** Der Zustand wird vor den Ereignissen übernommen, damit die Benachrichtigung
     *  nicht mit dem Stand von eben überschrieben wird. */
    private fun dispatch(events: List<Event>, notify: Boolean = true) {
        now.value = engine.now
        handle(events, notify)
    }

    private fun handle(events: List<Event>, notify: Boolean = true) {
        for (event in events) {
            when (event) {
                is Event.Started -> {
                    // Nur eine Fortsetzung macht die stille Speicherung überholt.
                    if (event.replaces != null) pendingNotice = null
                    status.value = if ((engine.now?.seconds ?: 0) > 5) "Session übernommen" else "Band läuft"
                    updateNotification()
                }
                is Event.Finished -> save(event.session, event.adopted, event.replaces, notify)
            }
        }
    }

    private fun save(session: Session, adopted: Boolean, replaces: Session?, notify: Boolean) {
        val c = ctx ?: return
        if (replaces != null) {
            Sessions.replace(c, replaces, session)
        } else {
            Sessions.append(c, session)
        }
        engine.remember(session)
        status.value = if (adopted) "Session übernommen" else "Session gespeichert"
        updateNotification()
        if (notify) {
            // Wurde die vorgemerkte Session fortgesetzt, ist ihre Nachricht überholt.
            if (replaces != null && replaces == pendingNotice) pendingNotice = null
            postSessionNotice(c, session)
        } else {
            // Mitten in einer Session abgerissen: still halten und erst melden,
            // wenn die Session sicher vorbei ist.
            pendingNotice = session
            pendingSince = System.currentTimeMillis()
        }
    }

    private fun postSessionNotice(c: Context, session: Session) {
        if (!AppVisible.visible) Notifications.showSession(c, session)
    }

    private fun tick() {
        val c = ctx ?: return
        dispatch(engine.onTick(System.currentTimeMillis()))
        val adapter = bluetoothAdapter(c)
        bluetoothOff.value = adapter == null || !adapter.isEnabled
        if (recording.value && gatt == null && !scanning && System.currentTimeMillis() >= nextArmAt) arm()
        if (engine.now != null) updateNotification()
        val pending = pendingNotice
        if (pending != null && engine.now == null &&
            System.currentTimeMillis() - pendingSince >= PENDING_NOTICE_MS
        ) {
            pendingNotice = null
            postSessionNotice(c, pending)
        }
    }

    // -------------------------------------------------------- Benachrichtigung

    private fun notificationText(): String = Recording.notification(state, now.value)

    private fun updateNotification() {
        val c = ctx ?: return
        if (!serviceRunning.value) return
        Notifications.updateForeground(c, notificationText())
    }

    // ------------------------------------------------------------------ Ende

    private fun onLinkLost() {
        stopPolling()
        releaseWakeLock()
        connected.value = false
        // Bricht die Verbindung mitten in einer Session ab, ist das meist ein
        // Funkloch und nicht das Ende. Dann still speichern und erst am echten
        // Ende melden.
        val midRun = (engine.now?.speedKmh ?: 0.0) >= RUNNING_SPEED_KMH
        dispatch(engine.onDisconnected(System.currentTimeMillis()), notify = !midRun)
    }

    private fun close() {
        stopScan()
        stopPolling()
        val g = gatt
        gatt = null
        onLinkLost()
        if (g != null) {
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
    }

    // -------------------------------------------------------------- Wakelock

    /**
     * Ohne Wakelock schläft die CPU und der Abfragetakt steht. Doze ignoriert
     * Wakelocks; die Akku-Ausnahme im Cockpit ist der zweite Teil der Absicherung.
     */
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val c = ctx ?: return
        if (wakeLock == null) {
            val pm = c.getSystemService(PowerManager::class.java) ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
                .apply { setReferenceCounted(false) }
        }
        val lock = wakeLock ?: return
        if (!lock.isHeld) runCatching { lock.acquire() }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
    }

    // --------------------------------------------------------------- Rahmen

    private fun parse(b: ByteArray): Live? {
        if (b.size < 17) return null
        if (b[0] != 0x02.toByte() || b[1] != 0x51.toByte()) return null
        if (b[b.size - 1] != 0x03.toByte()) return null
        var xor = 0
        for (i in 1 until b.size - 2) xor = xor xor (b[i].toInt() and 0xFF)
        if (xor != (b[b.size - 2].toInt() and 0xFF)) return null
        return Live(
            speedKmh = (b[3].toInt() and 0xFF) / 10.0,
            timeS = u16(b, 5),
            distM = u16(b, 7),
            kcal = u16(b, 9) / 10.0,
            hr = b[13].toInt() and 0xFF,
        )
    }

    private fun u16(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
}
