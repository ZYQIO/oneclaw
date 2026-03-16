# Test Report: RFC-060 — Remote Control Foundation

## Report Information

| Field | Value |
|-------|-------|
| RFC | RFC-060 |
| Related FEAT | Remote Control foundation |
| Commit | `46101c12` |
| Date | 2026-03-16 |
| Tester | AI (Codex) |
| Status | PARTIAL |

## Summary

Implemented the first end-to-end remote control foundation across:
- `:remote-core` shared Android library
- `:remote-host` standalone Android host app scaffold
- `remote-broker/` WebSocket broker
- `remote-console-web/` browser control console
- `:app` OneClaw integration (screen, repository, DI, navigation, tool group, tests)

Android test execution was blocked in this environment because no Java runtime is installed, so Gradle could not start. Static JavaScript syntax verification for the broker and web console passed.

| Layer | Step | Result | Notes |
|-------|------|--------|-------|
| 1A | JVM Unit Tests | SKIP | `./gradlew test` could not start: no Java Runtime installed |
| 1B | Instrumented DAO Tests | SKIP | Emulator not attempted because Gradle could not start |
| 1B | Instrumented UI Tests | SKIP | Emulator not attempted because Gradle could not start |
| 1C | Roborazzi Screenshot Tests | SKIP | UI screenshots added, but Gradle could not start |
| 2 | adb Visual Flows | SKIP | Android build/install unavailable in this environment |

## Layer 1A: JVM Unit Tests

**Command:** `./gradlew test`

**Result:** SKIP

**Reason for skip:** The host environment does not have a Java runtime. Gradle exited before configuration with:

`Unable to locate a Java Runtime.`

Notable test classes added in this RFC:
- `app/src/test/kotlin/com/oneclaw/shadow/tool/builtin/remote/RemoteToolsTest.kt`
- `app/src/test/kotlin/com/oneclaw/shadow/screenshot/RemoteControlScreenshotTest.kt`

## Layer 1B: Instrumented Tests

**Command:** `ANDROID_SERIAL=emulator-5554 ./gradlew connectedAndroidTest`

**Result:** SKIP

**Reason for skip:** Gradle could not start because the environment lacks Java.

**Test count:** 0 tests executed

## Layer 1C: Roborazzi Screenshot Tests

**Commands:**
```bash
./gradlew recordRoborazziDebug
./gradlew verifyRoborazziDebug
```

**Result:** SKIP

**Reason for skip:** Gradle could not start because the environment lacks Java.

### Screenshots

Intended screenshot coverage added:
- `RemoteControlScreen` — default connected state

## Layer 2: adb Visual Verification

**Result:** SKIP

**Reason for skip:** APK build/install was not possible because Gradle could not start.

## Additional Static Verification

- `node --check remote-broker/server.mjs` — PASS
- `node --check remote-console-web/app.js` — PASS
- `npm install --prefix remote-broker --no-package-lock` — PASS
- `node remote-broker/server.mjs` + `curl http://127.0.0.1:8080/healthz` — PASS (`{"ok":true,"devices":0,"sessions":0}`)
- `curl http://127.0.0.1:8080/api/state` — PASS (empty device/controller/session state returned as expected)

## Issues Found

| # | Description | Severity | Status |
|---|-------------|----------|--------|
| 1 | Android build and tests were blocked by missing Java runtime in the execution environment | High | Open |
| 2 | Non-root compatibility path is scaffolded only; full unattended control currently depends on rooted host devices | Medium | Known limitation |

## Change History

| Date | Change |
|------|--------|
| 2026-03-16 | Initial report |
