# Aufzeichnung läuft in einem dauerhaften Vordergrunddienst

Die App muss Sessions erfassen, während das Handy gesperrt ist oder die App geschlossen wurde; bisher hängt die Aufzeichnung am Activity-Prozess und endet mit ihm. Eine Bluetooth-Verbindung stirbt mit dem App-Prozess, und Android hält einen Prozess nur als Vordergrunddienst am Leben. Deshalb läuft ab dem Neustart ein Dienst der Art `connectedDevice`, der sich die Adresse des Bands merkt und Android auf das Band warten lässt. Die stille Dauerbenachrichtigung ist der sichtbare Preis dafür. Ein Verbindungsverlust beendet die Session, weil sonst ein stromlos gewordenes Band nie einen Abschluss melden würde; kehrt die Verbindung zurück und laufen die Zähler weiter, ersetzt die App den gespeicherten Eintrag.

## Considered options

- **Gerätekopplung über den CompanionDeviceManager**: Android weckt die App erst, wenn das Band erscheint. Verworfen, weil die Verknüpfung einen einmaligen Dialog braucht und ihr Überleben eines Neustarts nicht dokumentiert ist.
- **Weckruf per WorkManager in festem Takt**: kommt ohne dauerhaften Dienst aus, verpasst aber den Sessionbeginn und wird im Ruhezustand verschoben.
- **Bluetooth-Scan, der den Dienst startet**: auf Android 12 und neuer unzulässig, die Ausnahmeliste zum Starten eines Vordergrunddienstes enthält keinen Bluetooth-Eintrag mehr.
- **Aufzeichnung im Activity-Prozess**: der Zustand vor dieser Entscheidung, erfasst nichts, wenn die App geschlossen ist.

## Consequences

- Nach einem erzwungenen Stopp startet die App nicht von selbst wieder; die Aufzeichnung bleibt aus, bis sie geöffnet wird.
- Androids Ruhezustand ignoriert Wakelocks. Solange das Band verbunden ist, hält die App deshalb einen Wakelock, und die einmalige Ausnahme von der Akku-Optimierung sichert das zusätzlich ab.
