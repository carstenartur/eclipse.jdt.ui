# PR #3231: Echter PDE-Neustarttest

27. September 2026. Der PR-Branch wurde nicht verändert.

Prüflauf: https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/36314075063

Prüfskripte: `.github/pde-restart/` auf `verify/pr3231-pde-restart`, ausgeführte Revision `73f1e25a88b2fe5f66ad1234fd60648e47f17d98`.

## Aufbau

Verglichen wurden die quellgebauten JDT-UI-Bundles von Upstream `9cd0c74a96e0344763194ccbb28339846e6d2a45` und PR `97aeeaacad2c1af8bb08d1494ddfd192d5f96f67` im selben vollständigen Eclipse SDK `I20260926-2300` mit echtem PDE Core `3.21.500.v20260925-1924`, Linux/GTK/Xvfb und Java 25.

Neun getrennte Eclipse-Prozesse: drei Einrichtungsstarts und sechs echte Neustarts. Alle neun beendeten sich regulär mit Exitcode 0. Für jede Variante wurde der gleiche erhaltene Workspace-Snapshot am gleichen Pfad mit Upstream und PR wieder geöffnet. Die Ausgangsdateien `workbench-before.xmi` sind innerhalb jedes Vergleichspaars byteidentisch.

Ein kleiner echter PDE-Plug-in-Workspace wurde programmatisch eingerichtet. `example.Example` erweitert `org.eclipse.core.runtime.Plugin`; die `.classpath` verwendet `org.eclipse.pde.core.requiredPlugins`. Im Neustartmodus öffnet die Diagnose-Erweiterung keinen Editor und setzt keinen Hierarchie-Input. Sie prüft mit `getEditor(false)` und `getView(false)` die von Eclipse wiederhergestellten Objekte.

Ein externer JDI-Debugger setzt einen Breakpoint in `RequiredPluginsInitializer.initialize`, mit `SUSPEND_EVENT_THREAD`, nicht `SUSPEND_ALL`. Der erste Treffer je Neustart wird ungefähr drei Sekunden gehalten; gleichzeitig werden UI-Thread-Stacks und ein SWT-UI-Lebenszeichenzähler beobachtet.

## Resultate

| Szenario | Upstream | PR |
| --- | --- | --- |
| Java-Editor mit Breadcrumbs, ohne weitere sichtbare Ansicht | Initialisierer auf `main` über `StandardJavaElementContentProvider.getParent/exists` | Ausschließlich Worker-Threads; UI-Lebenszeichen steigen während der Pause 1 → 11 → 21 → 31 |
| Java-Editor + Package Explorer, Link with Editor aktiv | Initialisierer auf `main` über Working-Copy-Delta-Verarbeitung | Weiterhin `main`, jetzt über Auswahlverfolgung und Root-Elternabfrage |
| Java-Editor + sichtbare Typhierarchie | Initialisierer auf `main` | Weiterhin `main` über `TypeHierarchyViewPart.restoreState` |

Alle sechs Neustarts stellten den Java-Editor wieder her und zeigten am Ende den Breadcrumb `Example`. Die Link-Variante bestätigte aktivierte Editor-Verknüpfung. Die Typhierarchie zeigte am Ende `Example`; der Package Explorer war dort ein nicht instanziierter Hintergrund-Tab, nicht eine gleichzeitig sichtbare zweite Ansicht.

### Verbleibender Link-Pfad im PR

```text
PackageExplorerPart.editorActivated:1010
 -> PackageExplorerPart.showInput:1061
 -> TreeViewer / StructuredViewer.setSelection
 -> AbstractTreeViewer:1807
 -> StandardJavaElementContentProvider.getParent:251
 -> PackageExplorerContentProvider.internalGetParent:381
 -> PackageFragmentRoot.getRawClasspathEntry:616
 -> JavaProject.getResolvedClasspath:2453
 -> RequiredPluginsInitializer.initialize:31
```

### Verbleibender Typhierarchie-Pfad im PR

```text
TypeHierarchyViewPart.createPartControl:992
 -> TypeHierarchyViewPart.restoreState:1646
 -> JavaElement.exists:185
 -> Öffnen des Java-Modells und seiner Vorfahren
 -> JavaProject.buildStructure:498 / getResolvedClasspath:2453
 -> RequiredPluginsInitializer.initialize:31
```

`restoreState` prüft `input.exists()`, bevor es seinen bereits vorhandenen Hintergrundjob startet. Die Methodenbezeichnungen in den gekürzten Ketten wurden den im JDI-Trace aufgezeichneten Klassen und Quellzeilen zugeordnet.

## Einordnung

Der ursprüngliche Editor-/Breadcrumb-Pfad ist in diesem Vergleich behoben. Die beiden erweiterten Varianten zeigen jedoch weiterhin synchrone Klassenpfadinitialisierung im UI-Thread. Der Issue ist damit nicht vollständig gelöst.

Die grüne Workflow-Anzeige bestätigt die vollständige Ausführung und Sammlung der Diagnosebelege, nicht die Abwesenheit der in den Resultaten aufgezeichneten Fehler. Es wurde keine Produktivkorrektur, kein Squash, kein Merge und kein Issue-Kommentar vorgenommen.

Die drei Worker-Treffer im reinen PR-Editorlauf sind kein Nachweis für dreifache Arbeit im unbeeinflussten Normalbetrieb: Das Anhalten des ersten Aufrufs verändert die Nebenläufigkeit. Je Kombination wurde ein Neustart beobachtet. Keine statistische Performance-Messung, kein Windows-/macOS-Test, kein Test des ursprünglichen großen PDE/p2-Workspaces und kein Nachweis zur Batch-Effizienz oder zu einem p2-Deadlock.

Artefakt `pr3231-real-pde-restart` enthält Startkommandos, Buildprotokolle, Bundle-Versionen und Hashes, JSON-Ergebnisse, JDI-Traces, Workbench-Zustände und Screenshots. GTK/AT-SPI- und EGL/DRI3-Warnungen der Xvfb-Umgebung sind in den Rohprotokollen enthalten.
