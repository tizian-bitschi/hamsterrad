---
id: "01"
type: research
status: open
assignee: null
blocked_by: []
---

## Question

Welches BLE-Protokoll benutzt die Sportstech-App für ihre Laufbänder, und wie sind die Datenpakete aufgebaut?

Konkret: Für welche Gerätefamilien nutzt die App den Standarddienst FTMS (0x1826), für welche die Herstellerdienste FFF0/FFF1/FFF2, FEB0, FFB0 oder Nordic UART (6e400001)? Welche Charakteristik liefert Geschwindigkeit, Distanz, Steigung und Herzfrequenz, und in welcher Einheit, Skalierung und Byte-Reihenfolge?

Die App liegt als APK unter `.scratch/sportstech-base.apk`. Zusätzlich enthält sie `assets/device-names.json` (2,4 MB), das vermutlich Gerätenamen auf Familien abbildet.

Ergebnis: eine Liste plausibler Protokollvarianten mit Erkennungsmerkmalen (Gerätename, Advertisement, Dienst-UUID) und, soweit aus dem Code lesbar, dem Byte-Layout der Nutzdaten. Ticket 02 weiß danach, wonach es beim Mitschnitt sucht.

Bericht: `wayfinder/research/sportstech-protokoll.md`.
