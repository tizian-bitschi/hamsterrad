# Wayfinder: Laufband-Stats-App

Dieses Repo nutzt den lokalen Markdown-Tracker: diese Datei ist die Karte, `wayfinder/tickets/NN-*.md` sind die Tickets. Abhängigkeiten stehen als `blocked_by` in der Frontmatter, ein gesetzter `assignee` markiert den Claim.

## Destination

Eine auf dem OnePlus 9 Pro installierte App, die eine Laufband-Session automatisch erkennt — Start, sobald das Band läuft, Ende bei Stillstand — und live Zeit, Distanz, Tempo, Geschwindigkeit, Steigung und Herzfrequenz im Neon-Dunkel-Cockpit zeigt. Jede Session wird lokal gespeichert, als Liste plus Wochensumme. Nichts manuell eintragen, keine Cloud, keine Fernsteuerung des Bands.

## Notes

**Gerät und Bauen.** OnePlus 9 Pro, Android 14 (API 34), per USB verbunden. Android SDK bis API 36, JDK 21 und Gradle-Wrapper aus Nachbarprojekten sind vorhanden; Google Maven und Maven Central antworten. Bauen und Installieren per adb funktioniert.

**Laufband.** Sportstech, Bluetooth vorhanden, laut Hersteller per App steuerbar. Bedient wird es über die analoge Fernbedienung. Die App soll nur mitlesen.

**Herzfrequenz.** Der Polar H10 ist per BLE gekoppelt. Auf dem Laufband-Display erscheint eine Herzfrequenz, vermutlich vom WHOOP-Armband. Die Quelle ist noch offen (Ticket 03).

**Fundstück.** Die Sportstech-App v5.8.1 (`com.sportstechbrands.sportstechlive`) liegt als APK unter `.scratch/sportstech-base.apk`. Sie ist nativ (Kotlin/Java, 5 DEX-Dateien) und enthält den Standarddienst FTMS (0x1826), die Herstellerdienste FFF0/FFF1/FFF2, FEB0 und FFB0, Nordic UART (6e400001) sowie den Herzfrequenzdienst (0x180D). Damit sind die Protokollkandidaten bekannt.

**Vorlieben.** Deutsch, Neon-Dunkel (Richtung 1), Hochformat, Bildschirm bleibt an, alles lokal, kein Konto. Kalorien bewusst weggelassen.

**Sicherheit.** Die App liest nur. Sie schickt nie Kommandos ans Band, solange jemand darauf läuft.

**Skills.** Jede Sitzung: `ponytail` (kleinste vollständige Lösung) und `clear-writing-de`.

## Decisions so far

<!-- eine Zeile pro geschlossenem Ticket -->

## Not yet specified

- Verlaufsliste im Detail: welche Spalten, wie die Wochensumme gezählt wird (Kalenderwoche?) und was mit Sessions passiert, die über Mitternacht laufen.
- Verhalten, wenn die Verbindung mitten in der Session abreißt, oder wenn das Band läuft, während die App im Hintergrund ist.
- Wie das Cockpit genau aufgeteilt ist: welche Zahl groß in der Mitte steht, ob es einen Bogen oder Balken gibt, welche Farbe was bedeutet.
- App-Name und Icon. Der Ordnername „hamsterrad" wäre naheliegend.
- Fehlerfälle: falsches Gerät in Reichweite, Band aus, Bluetooth aus, verweigerte Berechtigungen.

## Out of scope

- Fernsteuerung von Geschwindigkeit oder Steigung. Sicherheitsrand: die App liest nur.
- Cloud, Konto, Sync zu Strava oder Health Connect.
- Kalorien und Schrittzählung.
- Laufen draußen mit GPS.
