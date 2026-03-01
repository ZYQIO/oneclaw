# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OneClawShadow is an Android app serving as a mobile AI Agent runtime, built with a documentation-driven development approach. Documentation (PRD, RFC) is the single source of truth -- code is generated from RFCs and can be fully regenerated at any time.

## Build and Test Commands

```bash
# Build
./gradlew assembleDebug

# Layer 1A: JVM unit tests (JUnit 5 with vintage engine for JUnit 4 compat)
./gradlew test

# Layer 1B: Instrumented tests (requires emulator-5554)
ANDROID_SERIAL=emulator-5554 ./gradlew connectedAndroidTest

# Layer 1C: Roborazzi screenshot tests
./gradlew recordRoborazziDebug     # record baselines
./gradlew verifyRoborazziDebug     # verify against baselines

# Compile checks only
./gradlew compileDebugUnitTestKotlin
./gradlew compileDebugAndroidTestKotlin

# Run a single test class
./gradlew test --tests "com.oneclaw.shadow.data.repository.ProviderRepositoryImplTest"

# Run a single test method
./gradlew test --tests "com.oneclaw.shadow.data.repository.ProviderRepositoryImplTest.someTestMethod"
```

## Architecture

Single `:app` module, package `com.oneclaw.shadow`. Clean Architecture layers:

- **`core/`** -- Domain models (`model/`), repository interfaces (`repository/`), `AppResult<T>` sealed class (`util/`)
- **`data/`** -- Room DB (`local/entity/`, `local/dao/`, `local/db/`, `local/mapper/`), API adapters (`remote/adapter/`), provider-specific DTOs (`remote/dto/{openai,anthropic,gemini}/`), repository implementations (`repository/`), encrypted key storage (`security/`)
- **`feature/`** -- Features organized by domain: `chat/`, `provider/`, `agent/`, `session/`. Each has screens, ViewModels, UI state, and `usecase/` subdirectory
- **`tool/`** -- AI tool system: `Tool` interface, `ToolRegistry`, `ToolExecutionEngine`, `PermissionChecker`, built-in tools (time, file read/write, HTTP)
- **`di/`** -- Six Koin modules: `appModule`, `databaseModule`, `networkModule`, `repositoryModule`, `toolModule`, `featureModule`
- **`navigation/`** -- Compose Navigation with sealed `Route` class
- **`ui/theme/`** -- Material 3 theme with gold/amber accent `#6D5E0F`

### Key Technical Decisions (do not change without discussion)

- **DI**: Koin (not Hilt)
- **Serialization**: Kotlinx Serialization (not Gson)
- **Error handling**: `AppResult<T>` sealed class for all fallible operations
- **ProviderType**: only `OPENAI`, `ANTHROPIC`, `GEMINI` -- no `CUSTOM`
- **API keys**: `EncryptedSharedPreferences` only, never stored in Room
- **UI style**: Material 3, Google Gemini app style

### Data Flow

Repository interfaces live in `core/repository/`. Implementations in `data/repository/` use Room DAOs + API adapters. Use cases in `feature/*/usecase/` orchestrate business logic. ViewModels expose UI state as StateFlow. Screens are Composable functions.

### API Adapter Pattern

`ModelApiAdapter` interface in `data/remote/adapter/` with three implementations: `OpenAiAdapter`, `AnthropicAdapter`, `GeminiAdapter`. `ModelApiAdapterFactory` creates the right adapter by `ProviderType`. `sendMessageStream()` is deferred to RFC-001 implementation.

## Documentation Rules

- All docs must be bilingual: English (`filename.md`) and Chinese (`filename-zh.md`), kept in sync
- Every feature follows: PRD first, then RFC, then code
- ID system: `FEAT-XXX` (PRD), `RFC-XXX` (design), `ADR-XXX` (decisions), `TEST-XXX` (scenarios)
- Writing workflow: write English doc first, then spawn a subagent (model: `claude-sonnet-4-6`) to translate it into Chinese

## Testing Protocol (After Every RFC Implementation)

Must run in order after completing an RFC:
1. **Layer 1A** -- `./gradlew test` (all must pass)
2. **Layer 1B** -- `ANDROID_SERIAL=emulator-5554 ./gradlew connectedAndroidTest` (skip if no emulator)
3. **Layer 1C** -- Roborazzi screenshot tests for new/modified Composables (skip if no UI changes)
4. **Layer 2** -- adb visual verification flows from `docs/testing/strategy.md` (skip if no device/keys)
5. **Write test report** -- `docs/testing/reports/RFC-XXX-<name>-report.md` (EN + ZH)
6. **Update manual test guide** -- `docs/testing/manual-test-guide.md` (EN + ZH)

## Communication

Communicate with the user in Chinese.
