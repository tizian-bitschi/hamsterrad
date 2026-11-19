# Sportstech-App: BLE-Protokoll der Laufbänder

Stand: 2026-11-19 · Grundlage: `sportstech-base.apk` (112 MB, 5 DEX), jadx 1.5.7 (je DEX einzeln) und ein eigener
DEX-String-Parser als Gegenprobe
Ziel: nur lesende Android-App, die Geschwindigkeit, Distanz, Steigung und Herzfrequenz mitliest.

## Kurzfazit

Die App liest Laufband-Live-Daten über das **FitBle/FitHome-Protokoll** (SDK `com.fithome.bluetooth`, DEX 3) auf
**Service `FFF0`, Notify `FFF1`, Write `FFF2`** — nicht über FTMS. Der Rahmen ist immer
`02 <CMD> <Payload…> <XOR> 03`, Live-Daten kommen als `02 51 <Status> <Speed·10> <Incline> <Zeit u16> <Distanz u16>
<Kalorien u16> <Schritte u16> <HF> … XOR 03` und müssen **aktiv alle ~500 ms mit `02 51 51 03` abgefragt werden**;
ein unaufgeforderter Datenstrom ist im Code nicht belegt. FTMS (0x1826) existiert in der App, wird aber nur als
Geräteklassifikation und als **virtueller Peripheral für das eingebaute Unity-Spiel** benutzt — ein Parser für
`0x2ACD` (Treadmill Data) fehlt. Für eine Lese-App heißt das: FitBle-Verbindung plus Status-Poll implementieren
(ein Schreibzugriff, der nur abfragt, keine Steuerung), FTMS optional als Bonus für Fremdgeräte.

## 1. `assets/device-names.json` — was drin steht (und was nicht)

Struktur (2,48 MB, UTF-8, Validiert mit `json.load`):

```
{ "schemaVersion": 1, "description": "...", "totalCount": 26915,
  "types":   [ { "typeId": "f10", "type": "F10", "count": 207, "namePrefixes": ["F10-"] }, … 16 Stück ],
  "devices": [ { "name": "sWalk-01234", "type": "sWalk", "typeId": "swalk" }, … 26915 Stück ] }
```

16 Typen mit Anzahl und Namenspräfixen:

| typeId | type | count | namePrefixes |
|---|---|---|---|
| swalk | sWalk | 11251 | `FIT-`, `sWalk-` |
| unmapped | Unmapped | 8808 | (Fremdgeräte: Polar, Garmin, Waagen, `TM-`, `TX-`, `TREADMILL`, `RZ_TreadMill`, …) |
| sprorun | sProRun | 943 | `sProRun-` |
| walkmate | WalkMate | 923 | `WalkMate-` |
| swalk-lite | sWalk Lite | 829 | `sWalk Lite-` |
| spad1000 | sPad1000 | 667 | `sPad1000-` |
| swalk-lite-plus | sWalk Lite Plus | 655 | `sWalk Lite Plus-` |
| swalk-plus | sWalk Plus | 631 | `sWalk Plus-` |
| f37s | F37s | 582 | `F37s-` |
| spad500 | sPad500 | 468 | `sPad500-` |
| stread-fold-pro | sTread Fold Pro | 345 | `sTread Fold Pro-` |
| f31s | F31s | 269 | `F31s-` |
| f10 | F10 | 207 | `F10-` |
| swalk-fold | sWalk Fold | 193 | `sWalk Fold-` |
| woodpad-pro | WoodPad Pro | 143 | `WoodPad-` |
| f75 | F75 | 1 | `F75` |

Laufband-Modelle sind also reichlich enthalten (alle `sWalk*`, `sPad*`, `sProRun`, `WalkMate`, `WoodPad Pro`,
`sTread Fold Pro`, `F10`, `F31s`, `F37s`; Treffer auf „tread“, „run“, „F31“, „TM/TX“ wie oben).

**Es gibt darin kein Protokollfeld, keine UUID, keinen Dienst- oder Charakteristik-Hinweis.** Die Datei wird
ausschließlich von `com.sportstechbrands.deviceSdkManager.GamingDeviceRegistry` gelesen
(`ASSET_NAME = "device-names.json"`, `readSupportedEntries()` → Map `lowercase(name) → typeId`), und zwar nur für
Einträge, deren `typeId` in `GamingDeviceCategory` vorkommt (10 der 15 echten Typen: swalk, swalk-lite, walkmate,
woodpad-pro, swalk-fold, sprorun, swalk-plus, swalk-lite-plus, spad500, spad1000). Zweck: Gaming-Freigabe und
Maximalgeschwindigkeit je Modell (6/8/12 km/h in `GamingDeviceCategory`), `wireValue` für die Anzeige.

Die eigentliche Laufband-Erkennung der App läuft über **Namenpräfixe** in
`QuickStartBluetoothScreenKt.isTreadmillByModelName()`:
`{"FIT-TM-", "F37s-", "sProRun-", "sTread Fold Pro-", "FS-", "F10-"}` plus Regex `^FIT-\d+-`.

→ Für die Protokollfrage ist das JSON **unbrauchbar**; es ist eine Namens→Modell-Tabelle fürs Spiel.

## 2. Welche BLE-Stacks in der APK liegen

Die im Auftrag gefundenen UUID-Strings stammen aus **vier verschiedenen SDKs**, nicht aus einem Protokoll:

| SDK | DEX | Zweck | UUIDs |
|---|---|---|---|
| `com.fithome.bluetooth` (FitBle/FitHome) | classes3 | **Laufbänder** (Treadmill), Bikes, Rower | `FFF0`, `FFF1`, `FFF2`, `F100`, `F101`, `F105` |
| `com.sheway.library.bluetooth` (EWAY) | classes4 | EW-BK/EW-ST/EW-EP = Bike/Rower/Elliptical | `FFF0`, `FFF1`, `FFF2`, `180D/2A37` (HF) |
| `cn.icomon.icdevicemanager` | classes3 | Körperwaagen, Küchenwaagen, Messlatten | `FEB0/FEB1/FEB2`, `FFB0…FFB3`, `FFF1`, NUS `6E400001…`, `180A/2A23–2A29` |
| Gerätekit / Spiel-Bridge | classes4 | FTMS-Klassifikation + virtueller FTMS-Peripheral fürs Unity-Spiel | `1826`, `2ACD`, `2ADA`, `2AD9`, `F100/F101/F105` |

Die beiden restlichen UUID-Gruppen sind ebenfalls Icomon und damit gewichts-/messbezogen, nicht Laufband:
`0783B03E-…-5CB7/5CB8/5CBA` = Dienst/Notify/Write der **Messlatte** (`ICRulerGemWorker.java:24-26`),
`5833FF01-…` = Dienst des **Waagen-OTA** (`ICSkipOTAWorker.java:31`).

### Wer macht was (Dispatch, belegt)

`DeviceManager.connectDevice(Object)` (classes4, `com/sportstechbrands/deviceSdkManager/DeviceManager.java:534`):

- `deviceType instanceof BLEDevice` und `type == 2` → `SBikeLiteKlifeDeviceManager` (KLife-Bike)
- `deviceType instanceof BLEDevice` sonst → **`F37DeviceManager`** → das ist der Laufband-Pfad
- `deviceType instanceof DeviceInfo` (EWAY) → `SBikeDeviceManager` (nur EW-BK/EW-ST, siehe unten)
- `deviceType instanceof BluetoothDevice` → `SpluseManager` (HF-Gurt)
- `deviceType instanceof ICScaleDeviceInfo` → `WeightScaleManager` (Icomon)

`F37DeviceManager` (49 KB) ist ein `BLEDevice.Delegate<BLEDevice>` und benutzt
`BLEManager.manager(context, BLEDevice.class)` aus dem FitBle-SDK; `didUpdateData()` schreibt die Werte in
`DeviceData` (`F37DeviceManager.java:454-476`).

EWAY wird dagegen **nur für Bikes/Rower** angeboten: `SBikeDeviceManager.startScan()` ruft
`ewBluetoothManager.startLeScan("")` und filtert die Treffer danach auf Namen mit `EW-BK`/`EW-ST`
(`SBikeDeviceManager.java:377-395`). Der Laufband-Code im EWAY-SDK (`TMController`, `device_type == 1`) ist
vorhanden, wird von der App aber nicht mit Laufbändern benutzt.

## 3. FitBle/FitHome — das Laufband-Protokoll (Hauptbefund)

Belegstellen (decompiliert): `.scratch/jadx-out/d3/sources/com/fithome/bluetooth/BLEDevice.java`,
`BLEObject.java`, `BLEManager.java`; App-Seite `.../deviceSdkManager/F37DeviceManager.java`;
Klassifikation `.scratch/jadx-out/d4/sources/ob/M.java`, `nb/a.java`, `nb/c.java`.

### Dienste und Charakteristiken

| Rolle | UUID | Beleg |
|---|---|---|
| Dienst | `0000fff0-0000-1000-8000-00805f9b34fb` | `BLEDevice.java:117` (`sService`) |
| Notify (Gerät → App) | `0000fff1-…` | `BLEDevice.java:118` (`sRecv`) |
| Write (App → Gerät) | `0000fff2-…` | `BLEDevice.java:119` (`sSend`) |
| optionaler Dienst | `0000f100-…` | `BLEDevice.java:120` (`FITHOME_SVC`) |
| optionaler Daten-Char | `0000f101-…` | `BLEDevice.java:121` (`FITHOME_DAT`) |
| optionaler Tasten-Char | `0000f105-…` | `BLEDevice.java:1006`, `didKeyEvent` |
| Notify-Enable | CCCD `00002902-…` | `BLEObject.java:571-577` |
| MTU | Request 283 | `BLEObject.java:344` |

Nuance aus `onService()` (`BLEDevice.java:989-1032`): Existiert im Dienst `F100` eine **Charakteristik mit der
UUID `F100`** (nicht `F101`), wird `protocol = 3` gesetzt; existiert nur `F101`, laufen auf `F101` dieselben
`0x50/0x51/0x53`-Rahmen wie auf `FFF1`/`FFF2` (`protocol` bleibt 0 → `1` bei Treadmill). Ein reines `F100`-Gerät
spricht dagegen `0x52`/`0x54` (`FITHOME_PARAM`/`FITHOME_TARGET`).

Kein Pairing/Bonding nötig, kein Handshake-Zwang: nach `onService()` wird `setCharacteristicNotification(mRxd, true)`
gesetzt, danach wird gepollt (`BLEDevice.java:1024-1032`).

### Rahmenformat (belegt)

```
02 | CMD | Payload … | XOR | 03
XOR = XOR über CMD und alle Payload-Bytes (d. h. XOR aller Bytes zwischen 02 und 03 ergibt 0)
```

Prüfung eingehend: `onRecvData()` verlangt `len ≥ 4`, `b[0] == 2`, `b[len-1] == 3`, XOR über `b[1..len-2] == 0`
(`BLEDevice.java:948-958`). Bau ausgehend: `poolSend()` (`BLEDevice.java:1048-1075`).

Kommandos (`BLEDevice.java:73-91`):

| CMD | Bedeutung | Payload |
|---|---|---|
| `0x50` (80) | TREADMILL_INFO | `0x00` Modell, `0x02` Speed-Bereich, `0x03` Incline-Bereich |
| `0x51` (81) | TREADMILL_STATE | Live-Daten (siehe unten) |
| `0x53` (83) | TREADMILL_CONTROL | `01` Start, `02` Speed+Incline, `03` Stop, `0A` Pause |
| `0x54` (84) | FITHOME_TARGET | nur Protokoll 3 (Geräte mit `F100`-Dienst) |

### Live-Daten `0x51` (Protokoll 1 = Treadmill) — Byte-Layout

Beleg: `BLEDevice.deviceState()` `BLEDevice.java:441-480` (nur dieser Zweig, `protocol == 1`).

| Offset | Typ | Bedeutung | Skalierung |
|---|---|---|---|
| 0 | u8 | Frame-Start | immer `0x02` |
| 1 | u8 | Command | immer `0x51` |
| 2 | u8 | Status | 0/1 idle · 2 start · 3 running · 4 stop · 10 pause · 5 error (Code in Byte 3) · 6 safe · 7 sleep |
| 3 | u8 | Speed | `* 0.1` → km/h |
| 4 | u8 (signed gelesen) | Incline | ganzzahlig, % |
| 5–6 | u16 LE | Zeit | Sekunden |
| 7–8 | u16 LE | Distanz | Meter (siehe Skalierungsbeleg unten) |
| 9–10 | u16 LE | Kalorien | `* 0.1` → kcal |
| 11–12 | u16 LE | Schritte/Count | ganzzahlig |
| 13 | u8 | Herzfrequenz | bpm |
| 14 | u8 | XOR | Prüfsumme |
| 15 | u8 | Ende | immer `0x03` |

Bedingungen im Code: Speed/Incline/HF werden ab `len > 15` und Status ∈ {running, paused, stopping} geparst;
Zeit/Distanz/Kalorien/Schritte nur bei `status == running`. Ein Frame ist also mindestens 16, typisch ~20 Bytes
lang; das Beispiel im Code (`bArr.length > 15`) passt zu `02 51 st … 03` mit 16 Nutzbytes.

Skalierungsbelege:
- Speed `* 0.1`: `value.speed = (bArr[3] & 255) * 0.1f` (`BLEDevice.java:471`); Fahrradzweig analog `*0.01`
  (`:506`).
- Distanz in **Metern**: `DeviceUtils.getMilesDeviceDistance() = getDeviceDistance() / 1609.34`
  (`DeviceUtils.java:652-654`); keine weitere Umrechnung in `DeviceData`.
- Kalorien `* 0.1`: `value.calories = (u16) * 0.1f` (`BLEDevice.java:477`).
- Incline: `value.incline = bArr[4]` ohne Division (`:472`) → ganzzahlige Prozent.

### Ausgehende Rahmen (nur zur Info, für uns irrelevant)

| Zweck | Bytes | Beleg |
|---|---|---|
| Status abfragen (Poll) | `02 51 51 03` | `updateState()` sendet `{81}` (`BLEDevice.java:886-890`), XOR = `0x51` |
| Speed-Bereich abfragen | `02 50 02 52 03` | `updateParam()` (`:833-834`) |
| Incline-Bereich abfragen | `02 50 03 53 03` | `updateParam()` (`:833`) |
| Start | `02 53 01 00 00 00 00 00 00 00 00 52 03` | `start()` (`:1192-1197`) |
| Stop | `02 53 03 50 03` | `stop()` (`:1216`) |
| Pause | `02 53 0A 59 03` | `pause()` (`:1039`) |
| Ziel Speed/Incline | `02 53 02 <speed·10> <incline> <XOR> 03` | `setSpeed()`/`setIncline()` (`:1099`, `:1142`) |

Poll-Takt: `tmrState.schedule(updateState, 50 ms, 50 ms)` und `mTick == 9` → **alle ~450 ms** ein `0x51`-Poll
(`BLEDevice.java:751-758`, `:873-891`). Der Datenfluss ist also **request/response**, kein Push.

### Erkennung eines FitBle-Laufbands

`BLEDevice.fithome(BLEModule)` (`BLEDevice.java:776-818`):

1. Advertisement enthält Dienst `FFF0` **oder** `F100`, und
2. es gibt Service-Data für `0xF100` (AD-Typ `0x16`, `getServiceData(61696)`) **oder** Manufacturer Data, und
3. dieser Blob besteht die XOR-Probe: 12-Byte-Variante XOR über alle 12 = 0; ≥8-Byte-Variante XOR über die
   ersten 8 = 0 (dann werden die ersten 4 Bytes gespiegelt).

Aus demselben Blob liest `nb/c.java` die Geräteklasse: Nibble `(b[3] & 0xFF) >> 4` → `0` Treadmill, `1` Trainer,
`2` Bike, `3` Rower, `4` Stepper, `5` Stairer, `6` Strength, `7` Vibrator (`nb/c.java:13-45`, konsistent mit
`BLEDevice.TYPE_*`).

Zusätzliche, weiche Erkennung: Namenpräfixe `F37s-`, `sProRun-`, `sWalk…`, `sPad…`, `FIT-…` (JSON + Regex
`^FIT-\d+-`) sowie die Klassifikationskette in `ob/M.java` (Name enthält `walk`/`pad`/`tread`/`run` → Treadmill).

### Konsequenz für die Lese-App (FitBle)

1. Scan auf Dienst `FFF0` (plus optional `F100`), Anzeigename als weiches Zusatzkriterium.
2. Verbinden, MTU ~283 anfragen, Notifications auf `FFF1` über CCCD `0x2902` einschalten.
3. Alle 400–500 ms `02 51 51 03` auf `FFF2` schreiben — **der einzige Write**, reine Abfrage, kein Zustandswechsel
   (die App selbst macht das durchgehend, auch im Standby). `FFF2` ist im Code mit Default-Write (mit ACK)
   beschrieben; Write-Without-Response ist zu testen.
4. Eingehende `02 51 …`-Frames nach Tabelle oben parsen, Plausibilität über XOR prüfen (XOR aller Bytes zwischen
   `02` und `03` muss 0 sein).
5. Sobald `0x50`-Antworten eintreffen, Grenzen (Speed/Incline) aus Byte 3/4 ableiten — nötig nur fürs HMI, nicht
   fürs Lesen.

## 4. EWAY (`com.sheway`) — zweite Variante, für Bikes/Rower, nicht für unsere Bänder

Gleiche Dienst-/Charakteristik-„Hülle“ (`FFF0`/`FFF1`/`FFF2`), aber **völlig anderes Rahmenformat**:

```
F0 | CMD | LEN | Payload (LEN Bytes) | CHK        (Gesamtlänge LEN + 4)
CHK = (Summe der ersten LEN+3 Bytes) & 0xFF
```
Beleg: `ByteUtil.checkOutSum()` (`.scratch/jadx-out/d4/sources/com/sheway/library/bluetooth/eway/utils/ByteUtil.java:29-43`),
`TMCommand.isProtocol()` (`.../command/TMCommand.java:101-108`).

Live-Daten kommen als CMD `0xD3` (211) — Parser in `EwBluetoothManager.parseTmEquipmentData()`
(`.../manager/EwBluetoothManager.java:366-428`), Feldreihenfolge laut `TreadmillSportData`-Konstruktor:

| Offset | Bedeutung | Skalierung |
|---|---|---|
| 3 | Sekunden | – |
| 4 | Minuten | – |
| 5–6 | Distanz = `b[5]*100 + b[6]` | Rohwert, App nutzt ihn als Meter |
| 7–8 | Kalorien = `b[7]*100 + b[8]` | – |
| 9 | Puls | bpm |
| 10 | Speed | `* 0.1` km/h (belegt über Bike-Pfad `SBikeDeviceManager.java:353`: `speed / 10.0f`) |
| 11 | Incline | % |
| 12–13 | Pace = `b[12]*100 + b[13]` | – |
| 14 | Status (0/1 stop, 2 start, 3 running, 4 pause, 5 stop) | – |
| 15 | Sportmodus | – |
| 16 | Count | – |
| 17 | Pace-Frequenz | – |
| 18 | HardKey | – |

Steuerbefehle (nur zur Einordnung): `0xC0` Link, `0xC3` Control (Sub 1 Start, 2 Stop, 3 Speed+Incline, 4 Pause),
`0xC1` Memory, `0xC5` Sportdaten setzen, `0xC6` Modus, `0xC7/0xC8` Program-Speed, `0xC9/0xCA` Program-Incline
(`TMCommand.java`).

**Einordnung:** Für Sportstech-Laufbänder nicht relevant (App filtert EWAY auf `EW-BK`/`EW-ST`). Wenn ein
Mitschnitt eines Bikes ansteht, wäre das die Referenz. Für unsere Lese-App nur als Fallback interessant, falls ein
Band doch mit `F0`-Rahmen antwortet.

## 5. Wird FTMS wirklich benutzt? — Ja, aber nicht zum Lesen echter Bänder

UUID-Tabelle: `Eb/C0197q.java` (`classes4`): Dienst `1826`, Daten `2ACD`, Status `2ADA`, Control Point `2AD9`,
Vendor `F100/F101/F105`; Mengen `g = {1826, F100}`, `h = {2ACD, 2ADA, 2AD9, F101, F105}`.

Verwendet wird diese Tabelle **ausschließlich in `Eb/*` und `Cb/RunnableC0119p`** — und `Eb` ist die
**Unity-Spiel-Bridge**: `Eb/D0.java` enthält das komplette JS (`window.AndroidBleBridge`, `GameBleUuids`,
`window.__nativeBleInstalled`), `Eb/C0195o.java` baut aus App-Zustand FTMS-/FitBle-artige Frames und schickt sie
per `c(C0197q.b, …)` bzw. `c(C0197q.e, …)` an das Spiel. Die App **emuliert** dort ein Gerät, sie liest keines.

Belege für „kein echter FTMS-Lesepfad“:
- Die ausgeschriebene UUID `00002acd-…` kommt in keiner der fünf DEX vor; `2acd` existiert nur als kurzer
  String in `Eb/C0197q` und wird dort zur Laufzeit zur 128-Bit-UUID ergänzt.
- Keine FTMS-Skalierung `* 0.01` (Suche nach `0.01f`/`0.01d` liefert nur UI-/Mathe-Stellen).
- `DeviceKitVendorConnector.connect()` behandelt `FTMS` als „nichts zu tun“ (Enum-Switch `i == 2` → kein
  Vendor-Connect), während `FITBLE`/`EWAY` einen Vendor-Scan starten
  (`devicekit/DeviceKitVendorConnector.java:869-886`).
- `ob/M.java` nutzt `1826` nur als Klassifikationsmerkmal (Familie `FTMS`) mit Namens-Heuristik für die
  Geräteklasse.

**Vermutung (klar als solche markiert):** FTMS dient (a) der Erkennung fremder FTMS-Geräte im Scan und (b) der
Versorgung des Spiels mit einem virtuellen FTMS-Peripheral. Für ein echtes Sportstech-Laufband ist FitBle der Weg.

## 6. Offene Punkte — was ein Live-Mitschnitt bestätigen muss

1. **Rahmenlänge/Payload des `0x51`-Frames** am echten Gerät (Parser verlangt `> 15`; vermutlich 16 Nutzbytes).
2. **Distanz-Einheit**: Meter ist nur über die Miles-Umrechnung belegt → 1 km Lauf muss ~1000 ergeben.
3. **Incline**: ganzzahlig und vorzeichenbehaftet? Negative Steigung (Gefälle) vorhanden?
4. **Push-Verhalten**: Sendet das Band von selbst `02 51 …`, oder ausschließlich auf Poll? (Code sagt Poll.)
5. **Wird `FFF0` immer beworben?** Geräte mit nur `F100` nutzen Protokoll 3 mit anderen Frames (`0x52`/`0x54`).
6. **Herzfrequenz**: kommt sie im `0x51`-Frame (Byte 13) oder nur vom separaten Gurt (`0x180D`/`0x2A37`)?
7. **Modellzuordnung**: Welche der 15 Modelltypen (sWalk…, F37s, sPad…) sprechen FitBle, welche FTMS?
8. **Write-Properties** von `FFF2` (Write ohne Response?) — beeinflusst die Poll-Rate.
9. Ob die App zusätzlich `0x50`-Infos (Speed-/Incline-Grenzen) braucht, bevor sie Daten schickt (im Code: nein,
   sie fragt sie nur ab).

## 7. Prüfplan für den Mitschnitt (kurz, in dieser Reihenfolge)

1. **Scan-Log** (nRF Connect oder eigenes Tool): Name, MAC, RSSI, alle Service-UUIDs, Service-Data `0x16` mit
   `F100`, Manufacturer Data, `0x1826` ja/nein. → Erkennungsregel aus Abschnitt 3 gegen die Realität prüfen.
2. **GATT-Dump** nach Verbindung: Dienste/Charakteristiken/Properties, insbesondere `FFF1` (Notify) und `FFF2`
   (Write/WriteNR).
3. **Passiv 10 s**: Notifications auf `FFF1` (CCCD `0x2902`) aktivieren, **nichts senden**, Band läuft.
   → Kommt ohne Poll etwas? (Erwartung: nein.)
4. **Poll 30 s**: `02 51 51 03` alle 500 ms auf `FFF2`, Band mit 3 km/h und 2 % Steigung.
5. **Info-Abfragen** einmalig: `02 50 02 52 03` und `02 50 03 53 03` → Grenzen/Flags notieren.
6. **Gegenprobe Skalierung**: 1 km am Band laufen, App-Anzeige und Mitschnitt vergleichen (Distanz, Kalorien,
   Schritte).
7. **Negativprobe**: falls nur `F0`-Rahmen kommen → EWAY-Layout aus Abschnitt 4 prüfen; falls nur `2ACD`-Notifies
   → FTMS-Pfad, dann `2ACD` nach Standard parsen (Flags u32; Instantaneous Speed u16 immer, `* 0.01` km/h;
   Total Distance u24 in m bei Flag-Bit 2; Inclination s16 `* 0.1` % bei Bit 3).

## 8. Belege und Reproduktion

- Decompiliert nach `.scratch/jadx-out/d1…d5` (`jadx --no-res -d … -j 4 <dex>`); die Zuordnung
  `classes3.dex → d3` (FitBle, Icomon), `classes4.dex → d4` (App, EWAY, Gerätekit, Spiel-Bridge).
- Zentrale Dateien: `.scratch/jadx-out/d3/sources/com/fithome/bluetooth/BLEDevice.java`,
  `.../BLEObject.java`, `.scratch/jadx-out/d4/sources/com/sheway/library/bluetooth/eway/**`,
  `.scratch/jadx-out/d4/sources/com/sportstechbrands/deviceSdkManager/{DeviceManager,F37DeviceManager,DeviceUtils,GamingDeviceRegistry}.java`,
  `.scratch/jadx-out/d4/sources/ob/M.java`, `nb/{a,b,c}.java`, `Eb/{C0195o,C0197q,D0}.java`.
- String-Volltextsuche über die DEX-Dateien mit eigenem Python-Parser (`.scratch/tools/dexgrep.py`,
  `dexstr.py`), Ergebnis gegen die decompilierten Quellen geprüft.
- Einschränkung: jadx meldet je DEX 47–117 Decompile-Fehler (Kotlin-Coroutinen/Compose); die hier zitierten
  Klassen sind vollständig lesbar. Alles „Vermutung“ Markierte ist **nicht** durch Code belegt.
