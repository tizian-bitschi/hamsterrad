# Papier statt Neon

Das Cockpit war bisher schwarz mit fünf Neon-Tönen. Es wird hell: Papier `#F7F3EE` als Seite, Sand `#EAE2DA` als Fläche, Oxblood `#5C262E` als Tinte, Grün `#3B5C54` als zweite Datenfarbe, Messing `#C4934B` als Zierde. Weil die Palette nur zwei lesbare Textfarben trägt, fällt die alte Fünferteilung zusammen: Hierarchie entsteht über Größe, nicht über Farbe, und die Herzfrequenz verliert ihre Sonderfarbe. Messing bleibt auf Papier bedeutungslos (2,5:1 auf Creme, unter der 3:1-Schwelle für Flächen) und erscheint nur auf Oxblood (4,3:1); Balken und Flächen tragen Grün. Die App folgt dem System-Dunkelmodus nicht, sie ist immer hell.

## Considered options

- **Zweites Theme neben dem Neon**: hätte Tokens, einen Umschalter, ein Prefs-Feld und zwei dauerhaft zu pflegende Farbsätze gekostet, für einen Fall, der nicht verlangt war. Verworfen, aber nachholbar — dann mit Token-Schicht.
- **System-Dunkelmodus respektieren**: hätte verlangt, die Papier-Palette ein zweites Mal für Dunkel zu lösen, also dieselbe Arbeit wie ein zweites Theme, nur ohne Umschalter.
- **Neon behalten**: der Zustand vor dieser Entscheidung.

## Consequences

- `AGENTS.md` beschreibt die App als „Neon-Dunkel-Cockpit"; das stimmt nicht mehr und wird mit dem Umbau korrigiert.
- Status- und Navigationsleiste brauchen dunkle Symbole, `android:windowBackground` wird Creme — sonst blitzt beim Kaltstart erst Schwarz, dann das Papier.
- Das App-Icon behält das Rad, wird aber neu eingefärbt (Oxblood-Grund, Creme-Ring, Messing-Nabe) und bekommt eine monochrome Ebene für die Themed Icons ab Android 13.
- Die Neon-Konstanten `Lime`, `Cyan`, `Magenta` und `Amber` sind danach falsch benannt; sie werden zu Rollen (`Paper`, `Sand`, `Ink`, `Forest`, `Muted`, `Brass`).
