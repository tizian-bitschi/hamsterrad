# Hamsterrad

Android-App fürs Laufband. Sie liest per Bluetooth mit, was das Band sendet, erkennt eine Session automatisch am laufenden Band und speichert sie lokal. Kein Konto, keine Cloud, keine Fernsteuerung.

## Language

**Session**:
Eine Fahrt auf dem Band, so wie das Band sie zählt. Sie beginnt, wenn das Band läuft, und endet bei Stillstand, beim Zurücksetzen der Zähler oder wenn die Verbindung zum Band abbricht.
_Avoid_: Lauf, Trainingseinheit, Workout

**Übernommene Session**:
Eine Session, deren Anfang die App nicht mitgesehen hat. Sie ergibt sich beim Verbinden aus den Zählern des Bands; als Ende gilt der Zeitpunkt, an dem die App sie zum ersten Mal gelesen hat.
_Avoid_: verpasste Session, nachgeholte Session

**Zähler**:
Die drei Werte, die das Band seit seinem letzten Start selbst hochzählt: Zeit, Distanz und Energie. Sie sind die Quelle der Session-Werte.
_Avoid_: Messwerte, Statistik

**Live**:
Die Werte aus dem zuletzt empfangenen Rahmen des Bands, unabhängig davon, ob gerade eine Session läuft.
_Avoid_: Echtzeit, Ist-Werte

**Aufzeichnung**:
Der Betrieb, in dem die App das Band mitliest und Sessions erfasst, ohne dass sie sichtbar sein muss.
_Avoid_: Tracking, Logging, Hintergrunddienst

**Dauerbenachrichtigung**:
Die Benachrichtigung, die den laufenden Dienst begleitet und den Zustand der Aufzeichnung zeigt.
_Avoid_: Dauer-Eintrag, Statusleisten-Eintrag

**Cockpit**:
Der Hauptbildschirm mit Zeit, Distanz, Tempo, Geschwindigkeit und Herzfrequenz.
_Avoid_: Dashboard, Hauptansicht

**Verlauf**:
Die Liste aller gespeicherten Sessions mit Wochensumme und Balkendiagramm der letzten sieben Tage.
_Avoid_: Historie, Log
