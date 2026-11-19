# Hamsterrad

Android-App fürs Laufband. Sie liest per Bluetooth mit, was das Band sendet, erkennt eine Session automatisch am laufenden Band und speichert sie lokal. Kein Konto, keine Cloud, keine Fernsteuerung.

## Language

**Session**:
Eine Fahrt auf dem Band, so wie das Band sie zählt. Sie beginnt, wenn das Band läuft, und endet nach zwanzig Sekunden Stillstand, beim Zurücksetzen der Zähler oder wenn die Verbindung zum Band abbricht.
_Avoid_: Lauf, Trainingseinheit, Workout

**Pause**:
Ein Stillstand des Bands, der kürzer dauert als das Ende einer Session. Die Session läuft dabei weiter.
_Avoid_: Halt, Stopp

**Aufzeichnung aus**:
Der Zustand, in dem der Dienst nicht läuft. Dann gibt es auch keine Dauerbenachrichtigung.
_Avoid_: gestoppt, deaktiviert

**Aufzeichnung pausiert**:
Der Zustand, in dem der Dienst läuft, die App aber nicht mitschreibt und keine Verbindung zum Band hält. Das Band läuft dabei weiter; steht es später noch, übernimmt die App die Session beim Wiedereinschalten.
_Avoid_: Standby, angehalten

**Übernommene Session**:
Eine Session, deren Anfang die App nicht mitgesehen hat. Sie ergibt sich beim Verbinden aus den Zählern des Bands. Steht das Band dabei schon, gilt als Ende der Zeitpunkt des ersten Lesens; läuft es noch, endet sie wie jede andere.
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
