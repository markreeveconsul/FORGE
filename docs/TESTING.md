# FORGE testing evidence

## Current verification on 24 October 2026

Windows, JDK 26.0.1, Java 21 source release and JavaFX 21.0.6. `build.ps1 Test` completed with **BUILD SUCCESS**: **175 JUnit tests passed**, zero failures, errors or skipped tests. Seven native JavaFX suites passed **121 assertions**. UI assertion counts are separate from JUnit test counts.

All databases were newly created test fixtures. UI suites used isolated user-home directories. No production database, stored API key or live AI request was used. Deterministic mock responses exercise provider payloads and failures; this does not test live model accuracy or availability.

| JUnit suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| AccountAndRepeatTest | 9 | 0 | 0 | 0 |
| CommerceWorkflowTest | 28 | 0 | 0 | 0 |
| service.AiManagerServiceTest | 25 | 0 | 0 | 0 |
| service.AiQuotaTest | 13 | 0 | 0 | 0 |
| service.AiReliabilityTest | 19 | 0 | 0 | 0 |
| service.CatalogAssistantTest | 24 | 0 | 0 | 0 |
| service.GeminiAssistantTest | 7 | 0 | 0 | 0 |
| service.RevenueAnalyticsTest | 9 | 0 | 0 | 0 |
| ShopTest | 35 | 0 | 0 | 0 |
| TechCategoriesTest | 3 | 0 | 0 | 0 |
| ui.AssistantPanelTest | 3 | 0 | 0 | 0 |

| Native JavaFX suite | Assertions | Result |
|---|---:|---|
| StorefrontUiSmoke | 19 | PASS |
| AiManagerUiSmoke | 19 | PASS |
| AiQuotaUiSmoke | 12 | PASS |
| CommerceUiSmoke | 15 | PASS |
| CustomerNavigationUiSmoke | 22 | PASS |
| LoginGlassSmoke | 18 | PASS |
| AdminDashboardUiSmoke | 16 | PASS |

## Evidence locations

- [Verification summary](evidence/2026-10-24/verification.json)
- [Maven output](evidence/2026-10-24/maven-test.log)
- [UI logs and screenshot provenance](evidence/2026-10-24/)
- [Current report screenshots](screenshots/submission/)
- [Source signatures and hashes](uml/source-inventory.json)

The AI Manager screenshot additionally uses the archived `AiManagerDocumentationCapture.java` harness to expand the draft and scroll it into view. It runs the same 19 assertions; this repeat is not added to the 121 total. Screenshot data and AI drafts are synthetic, while controls and service operations are real.

Old Surefire XML files may remain in target after a removed test. The summary includes only suites whose Java test source still exists and asserts the total is 175. Old verification notes are preserved separately in [the archive](evidence/archive/TESTING-before-2026-10-24.md); their screenshots and counts are historical.

## Reproduce the JUnit run

From the project root:

```powershell
.\build.ps1 Test
```

This action does not import the saved key. It compiles current source and runs Maven Surefire against isolated SQLite test fixtures. The output goes to target/surefire-reports. The existing build helper pins JavaFX to the detected JDK; it does not depend on the broken Oracle java shortcut found during startup diagnosis.

## Reproduce a native UI suite

Run the following from the project root after setting JAVA_HOME to a working JDK 21 or newer. Use an absolute path to its java.exe. `build.ps1 Test` prepares target/classes and target/test-classes. Dependencies below are from this project's Maven cache on Windows; substitute the matching JavaFX platform classifier on another OS.

```powershell
$forgeJava = Join-Path $env:JAVA_HOME 'bin/java.exe'
$forgeRepo = Join-Path $PWD '.tools/m2'
$forgeFx = (Get-ChildItem "$forgeRepo/org/openjfx" -Recurse -Filter '*-21.0.6-win.jar').FullName -join ';'
$forgeLibs = @(
  "$forgeRepo/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar",
  "$forgeRepo/org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.jar",
  "$forgeRepo/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar",
  "$forgeRepo/org/slf4j/slf4j-nop/2.0.17/slf4j-nop-2.0.17.jar"
) -join ';'
$forgeCp = "target/classes;target/test-classes;$forgeFx;$forgeLibs"
$forgeOut = Join-Path $PWD ('target/ui-evidence-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $forgeOut | Out-Null
& $forgeJava --enable-native-access=ALL-UNNAMED `
  "-Djavafx.cachedir=$forgeOut/fx-cache" "-Duser.home=$forgeOut/home" `
  -cp $forgeCp edu.course.shop.StorefrontUiSmoke $forgeOut
if ($LASTEXITCODE -ne 0) { throw 'UI verification failed.' }
```

Other suite entry points are `edu.course.shop.service.AiManagerUiSmoke`, `edu.course.shop.service.AiQuotaUiSmoke`, `edu.course.shop.CommerceUiSmoke`, `edu.course.shop.CustomerNavigationUiSmoke`, `edu.course.shop.ui.LoginGlassSmoke` and `edu.course.shop.service.AdminDashboardUiSmoke`. Each takes its own evidence directory. These classes use injected mock AI transports and create their own databases. They do not invoke the key-importing Run action. Direct JUnit XML should remain local; publish the curated summaries rather than arbitrary system-property dumps.

## Coverage

Success and failure cases cover roles, product persistence, money and stock validation, immutable snapshots, checkout rollback, saved-cart ownership and restoration, cancellation and stock restoration, receipt-preserving deletion, specification parsing, local catalog retrieval, product cards without specifications, category corrections, AI argument validation and reviewed writes, cooldowns and structured quota errors, exact revenue aggregation and current interface navigation.

## Manual presentation checks still pending

- Launch the batch file on the presentation computer and check window sizing and display scaling.
- Test native file chooser select/cancel and representative photo imports.
- Verify keyboard navigation, long input and reduced motion with the actual display and GPU.
- Test representative live image matching, review summary and AI drafting when the configured project has quota. Automated provider fixtures do not establish live accuracy.
- Confirm instructor approval of the title and rehearse the code walkthrough individually.

These items are not marked as automated passes. Adding or changing Java code after this verification invalidates the snapshot hashes and requires rerunning relevant tests.
