---
id: "02"
type: prototype
status: open
assignee: null
blocked_by: []
---

## Question

Was sendet das Laufband tatsächlich, wenn das Band läuft?

Ein kleines Diagnose-APK — gleichzeitig das Gerüst der späteren App — scannt BLE, verbindet sich mit dem Laufband, listet alle Dienste und Charakteristiken und schreibt jede Notification mit Zeitstempel mit. Du startest das Band per Fernbedienung, lässt es eine Minute auf mittlerer Geschwindigkeit laufen, erhöhst und senkst einmal die Steigung und stoppt es wieder. Die Aufzeichnung landet als JSON auf dem Gerät.

Ergebnis: Dienst und Charakteristik, die die Live-Werte liefern, dazu das Byte-Layout mit Einheiten — bestätigt an echten Daten statt geraten. Falls das Band FTMS spricht, ist das der ganze Weg; sonst sehen wir hier, welcher Herstellerdienst mitschreibt.

Sicherheitsrand: Das APK schreibt nichts auf das Band. Es abonniert nur Benachrichtigungen.

Ticket 01 liefert die Kandidaten, auf die der Mitschnitt zuerst schaut. Beide können parallel laufen.
