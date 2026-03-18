# Phase 0 Baseline Report

## Summary

This report captures the current execution baseline for OneClaw on the machine used during handover on 2026-03-18.

Current conclusion:

- The repository structure is present and consistent with the newer multi-module Android architecture.
- The Node.js-based `remote-broker` can start and respond correctly.
- Android build verification is currently blocked because Java, Android SDK, and `adb` are not installed on this machine.

This is a baseline report, not a feature test report.

## Repository Snapshot

- Commit: `ff1f861`
- Root modules:
  - `:app`
  - `:bridge`
  - `:remote-core`
  - `:remote-host`
- Additional runtime components outside Gradle modules:
  - `remote-broker`
  - `remote-console-web`

## Code-Backed Environment Requirements

The current Android build files require:

- Java 17
- Android Gradle Plugin `8.7.3`
- Kotlin `2.0.21`
- `compileSdk = 35`
- `targetSdk = 35`
- `minSdk = 26`

The main Android app and the remote host app both target Java 17.

## Local Machine Snapshot

Detected on this machine:

- `node`: `v22.22.0`
- `npm`: `10.9.4`
- `java`: not installed or not on `PATH`
- `adb`: not installed or not on `PATH`
- `JAVA_HOME`: not set
- `ANDROID_HOME`: not set
- `ANDROID_SDK_ROOT`: not set
- `local.properties`: missing

No local Android Studio installation or Android SDK directory was found in the standard locations checked during this baseline pass.

## Executed Checks

### 1. Repository Structure Check

Status: PASS

Confirmed the current root project includes:

- `:app`
- `:bridge`
- `:remote-core`
- `:remote-host`

This matches the current remote-control-aware architecture and confirms the repository is beyond the earlier two-module project state.

### 2. Java Availability Check

Status: FAIL

Command:

```powershell
java -version
```

Result:

- `java` command not found

Impact:

- No Gradle-based Android validation can run yet.

### 3. Gradle Bootstrap Check

Status: FAIL

Command:

```powershell
.\gradlew.bat -q help
```

Result:

- Gradle wrapper exits immediately because `JAVA_HOME` is not set and `java` is not available.

Impact:

- `assembleDebug`
- `test`
- `connectedAndroidTest`
- Roborazzi tasks

All remain blocked in the current environment.

### 4. Android Tooling Check

Status: FAIL

Command:

```powershell
adb version
```

Result:

- `adb` command not found

Impact:

- No emulator/device validation
- No Layer 1B instrumented testing
- No Layer 2 adb visual verification

### 5. Node.js Tooling Check

Status: PASS

Commands:

```powershell
node -v
npm -v
```

Result:

- Node.js is available
- npm is available

### 6. `remote-broker` Boot Check

Status: PASS

Method:

- Started `remote-broker/server.mjs` through a self-test harness
- Queried broker health and state endpoints

Observed results:

- `/healthz` returned HTTP `200`
- `/api/state` returned HTTP `200`
- Broker startup log reported:

```text
Remote broker listening on http://0.0.0.0:8080
```

Response snapshot:

```json
{"ok":true,"devices":0,"sessions":0}
```

This confirms the Node broker can start on the current machine without additional setup beyond the already present repository contents.

### 7. `remote-console-web` Static Serving Check

Status: PASS

Method:

- Queried `/` from the running broker

Observed result:

- HTTP `200`
- HTML response includes the `OneClaw Remote Console` page title

This confirms the broker can serve the browser console entry page from the repository.

## Current Blockers

### Blocker 1: No Java 17 runtime

Without Java 17, no Android Gradle task can run.

### Blocker 2: No Android SDK / platform tools

Without Android SDK and `adb`, the Android apps cannot be built, installed, or tested locally.

### Blocker 3: No local SDK wiring

`local.properties` is missing, and no SDK environment variables are configured.

## What Is Verified Today

Verified:

- Repository layout
- Remote-control-related module layout
- Node runtime availability
- `remote-broker` startup
- Broker health endpoint
- Broker state endpoint
- Broker static serving of `remote-console-web`

Not yet verified:

- `:app` build
- `:remote-host` build
- unit tests
- instrumented tests
- screenshot tests
- emulator flows
- real-device rooted remote-control flow

## Recommended Next Actions

1. Install Java 17 and set `JAVA_HOME`.
2. Install Android SDK components required for API 35 and platform tools.
3. Create `local.properties` or configure `ANDROID_SDK_ROOT`.
4. Re-run the reusable environment check script:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-env.ps1
```

5. Then re-run:

```powershell
.\gradlew.bat -q help
.\gradlew.bat assembleDebug
.\gradlew.bat :remote-host:assembleDebug
.\gradlew.bat test
```

6. After build recovery, move to emulator or real-device verification for the remote-control path.

## Exit Status

Phase 0 is partially complete.

Completed:

- baseline environment detection
- broker boot verification
- blocker identification

Still pending:

- Android build recovery
- Android test execution
- installation validation on device or emulator
