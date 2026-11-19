# Issue-Tracker: GitHub

Issues und Specs für dieses Repo liegen als GitHub-Issues in `tizian-bitschi/hamsterrad`. Alle Operationen laufen über die `gh`-CLI.

## Konventionen

- **Issue anlegen**: `gh issue create --title "..." --body "..."`. Bei mehrzeiligem Text `--body-file`.
- **Issue lesen**: `gh issue view <nummer> --json number,title,body,labels,comments`.
- **Issues auflisten**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`, dazu passende `--label`- und `--state`-Filter.
- **Sub-Issue**: `gh issue create --parent <eltern-nummer> ...` oder `gh issue edit <eltern> --add-sub-issue <kind>` (gh 2.94+).
- **Blockieren**: native Issue-Dependencies. `gh issue create --blocked-by <n>,<n> ...` oder `gh issue edit <kind> --add-blocked-by <blocker>`. Ein Ticket ist frei, wenn alle Blocker geschlossen sind.
- **Offene Blocker zählen**: `gh api repos/tizian-bitschi/hamsterrad/issues/<n> --jq .issue_dependencies_summary.blocked_by` (nur offene Blocker).
- **Kommentar**: `gh issue comment <nummer> --body "..."`.
- **Labels**: `gh issue edit <nummer> --add-label "..."` beziehungsweise `--remove-label "..."`.
- **Schließen**: erst `gh issue comment`, dann `gh issue close`. Bei einem bereits geschlossenen Issue verwirft `gh issue close --comment` den Kommentar, ohne Fehler zu melden.

Das Repo ergibt sich aus `git remote -v`; `gh` erkennt es im Klon selbst.

## Pull Requests als Anfrage-Fläche

**PRs als Anfrage-Fläche: nein.** Wenn dieses Repo externe PRs als Funktionswünsche behandeln soll, hier auf `ja` setzen; `/triage` liest das Flag.

## Wayfinding

Genutzt von `/wayfinder`. Die **Karte** ist ein Issue mit dem Label `wayfinder:map`, jedes **Ticket** ist ein Sub-Issue davon.

- **Karte**: ein Issue mit Label `wayfinder:map`. Der Body enthält Destination, Notes, Decisions so far, Not yet specified und Out of scope.
- **Ticket**: Sub-Issue der Karte mit dem Label `wayfinder:<typ>` (`research`, `prototype`, `grilling`, `task`). Sobald es übernommen ist, wird es der treibenden Person zugewiesen.
- **Blockieren**: siehe oben unter Konventionen.
- **Frontier**: offene Sub-Issues der Karte ohne offenen Blocker und ohne Assignee; das erste in Kartenreihenfolge gewinnt.
- **Übernehmen**: `gh issue edit <n> --add-assignee @me` als erster Schreibzugriff, vor jeglicher Arbeit.
- **Abschließen**: Antwort als Kommentar, Issue schließen, dann in der Karte unter „Decisions so far" eine Zeile mit Kernaussage und Link ergänzen.
