# JUnit N&N screenshots (opt-in SWTBot test)

This standalone test bundle is intentionally not included in the normal JDT UI reactor.
It adds one SWTBot test, with no production changes and no new dependencies for existing tests.
It runs against a published Eclipse 4.42 integration build containing JDT UI PRs #3145 and #3163,
not against locally rebuilt JDT UI bundles. The installed bundle versions are recorded in `provenance.txt`.

## Run

With JDK 25, Maven, GTK 3 and a graphical display:

```sh
mvn -B -f org.eclipse.jdt.ui.tests.screenshots/pom.xml clean verify
```

On Linux without a desktop:

```sh
xvfb-run -a -s '-screen 0 1360x900x24' \
  mvn -B -f org.eclipse.jdt.ui.tests.screenshots/pom.xml clean verify
```

Use `-Declipse.repository=https://download.eclipse.org/eclipse/updates/4.42-I-builds/<build-id>`
to pin a particular integration build. It must include both features.
The dedicated GitHub Actions workflow runs on pushes to `screenshots/junit-nn-swtbot`
and uploads the screenshots, reports and provenance as an artifact.

## What the single test does

The test runs on a non-UI thread, while SWTBot interacts with the real workbench.
It creates an isolated example project and launches actual JUnit 4 tests in a test JVM.
A CPU-bound test and a sleeping test produce measured timing data.
The test enables `Show Execution Time Details` through the view menu and checks the visible values.
It also checks that the elapsed-time and detail switches are independent.

For reload, the test exports two actual test runs: an intentionally failing example and a passing variant.
The difference is the example's `example.fixed` VM argument; no XML results or timing values are invented.
It imports the failing report, replaces the source file with the passing report,
and clicks the `Reload Test Run` toolbar button through SWTBot.
It checks the new result, retained source file, unchanged history size and replacement position.
It does not call the reload implementation directly.

Successful output under `target/screenshots/`:

- `junit-execution-time-details.png`
- `junit-imported-results-before-reload.png`
- `junit-imported-results-after-reload.png`
- `provenance.txt`, `verification.txt`, and the exported XML reports

A failed test additionally captures `capture-failure.png` and fails the build.
The workbench view is captured directly with SWTBot's screenshot utility; no UI is recreated or drawn.
The N&N website branches are not modified automatically: screenshots must be visually checked before publication.
