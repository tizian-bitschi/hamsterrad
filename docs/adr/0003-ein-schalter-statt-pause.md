# Ein Schalter statt Pause

Die Aufzeichnung hatte zwei Zustände, in denen sie nicht mitschrieb: „aus" (Dienst beendet) und „pausiert" (Dienst läuft, aber ohne Verbindung zum Band). Bedient wurde nur der zweite, und zwar über eine Zeile im Cockpit, die wie eine Anzeige aussah und beim Antippen schaltete. Jetzt gibt es einen Schalter mit zwei Stellungen: Aus beendet den Dienst, die Dauerbenachrichtigung verschwindet, und der Zustand überlebt einen Neustart. Den Begriff „Aufzeichnung pausiert" gibt es nicht mehr.

## Considered options

- **Pause behalten und nur besser platzieren**: hätte zwei Aus-Zustände erklärt, die sich im Erleben nicht unterscheiden — in beiden Fällen schreibt die App nicht mit und übernimmt beim Wiedereinschalten die Session, die das Band inzwischen selbst gezählt hat. Verworfen, weil der Unterschied nur den Dienst betrifft und nicht den Nutzer.
- **Dienst am Leben lassen, während der Schalter aus ist**: die Dauerbenachrichtigung hätte „pausiert" gezeigt, während der Schalter auf aus steht. Verworfen, weil das genau die Verwirrung verlängert, die den Umbau ausgelöst hat.
- **Ausschalten mitten in einer Session erlauben**: hätte beim Wiedereinschalten einen zweiten Eintrag erzeugt, weil die Übernahme die Zähler des Bands liest und die den ganzen Lauf enthalten. Verworfen; der Schalter bleibt während einer Session gesperrt.

## Consequences

- `RecordingState.PAUSED`, `Prefs.paused` und die Aktion der Dauerbenachrichtigung fallen weg. Aus dem gespeicherten Pausenzustand wird der Schalterzustand mit dem Standard „an".
- Der Boot-Empfänger startet den Dienst nur noch, wenn der Schalter an ist; die Bluetooth-Berechtigung wird erst beim Einschalten gefragt.
- Die Entscheidungen in `.scratch/issues/06-hintergrundaufzeichnung.md` zur Pause und zur schaltenden Cockpit-Zeile sind damit überholt.
