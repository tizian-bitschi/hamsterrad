# Domain-Doku

So lesen die Engineering-Skills die Domänendokumentation dieses Repos.

## Vor dem Erkunden lesen

- **`GLOSSARY.md`** im Repo-Root, oder
- **`GLOSSARY-MAP.md`** im Repo-Root, falls vorhanden. Sie verweist auf je ein `GLOSSARY.md` pro Kontext; lies die für das Thema relevanten.
- **`docs/adr/`**: ADRs lesen, die den Bereich betreffen, in dem du arbeiten willst.

Fehlen diese Dateien, mach still weiter. Weis nicht auf ihr Fehlen hin und schlag nicht vor, sie vorab anzulegen. `/domain-modeling` legt sie nach Bedarf an, sobald Begriffe oder Entscheidungen tatsächlich feststehen.

## Aufbau

Dieses Repo ist single-context:

```
/
├── GLOSSARY.md
└── docs/adr/
```

## Wortschatz des Glossars verwenden

Wenn deine Ausgabe einen Domänenbegriff nennt — in einem Issue-Titel, einem Refactoring-Vorschlag, einer Hypothese, einem Testnamen —, nutze den Begriff so, wie das Glossar ihn definiert. Weiche nicht auf Synonyme aus, die das Glossar ausdrücklich vermeidet.

Fehlt der Begriff im Glossar, ist das ein Signal: entweder erfindest du Sprache, die das Projekt nicht benutzt, oder es gibt eine echte Lücke. Die notierst du für `/domain-modeling`.

## ADR-Konflikte melden

Widerspricht deine Ausgabe einem bestehenden ADR, sag es ausdrücklich, statt ihn still zu überschreiben:

> _Widerspricht ADR-0007 (Sessions als Ereignisstrom), aber es lohnt sich, das neu aufzumachen, weil …_
