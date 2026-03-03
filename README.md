# OneClaw Shadow

A mobile AI Agent runtime for Android -- chat with AI models, execute tools, automate tasks, and connect via messaging bridges.

## What It Does

OneClawShadow turns your Android device into an AI agent platform. It connects to multiple AI providers (OpenAI, Anthropic, Gemini), gives the AI 39+ built-in tools to interact with your device, and bridges conversations to messaging platforms like Telegram, Discord, and Slack.

## Key Features

- **Multi-provider AI chat** -- Stream responses from OpenAI, Anthropic, and Gemini with tool calling support
- **39+ built-in tools** -- Web browsing, shell execution, PDF processing, file management, JavaScript evaluation, and more
- **Custom JavaScript tools** -- Create your own tools with a sandboxed QuickJS engine
- **Scheduled tasks** -- Run AI agents on a schedule (daily, weekly, one-time)
- **Messaging bridge** -- Receive and respond to messages from Telegram, Discord, Slack, LINE, Matrix, and WebChat
- **Persistent memory** -- Cross-session memory with hybrid search (BM25 + vector embeddings)
- **Custom skills** -- Reusable prompt templates invoked via slash commands
- **File attachments** -- Send images and files to the AI
- **Google Drive integration** -- Backup data and access Drive files
- **Token tracking** -- Monitor usage across providers and models

## Quick Start

### Build

```bash
git clone <repository-url>
cd oneclaw-shadow-1
./gradlew assembleDebug
```

### Test

```bash
# JVM unit tests
./gradlew test

# Instrumented tests (requires emulator)
ANDROID_SERIAL=emulator-5554 ./gradlew connectedAndroidTest

# Screenshot tests
./gradlew verifyRoborazziDebug
```

### Requirements

- Android Studio Hedgehog+
- JDK 17
- Android SDK (compileSdk 35, minSdk 26)

## Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose, Material 3 |
| DI | Koin |
| Database | Room |
| Network | OkHttp, Retrofit |
| Serialization | kotlinx.serialization |
| JS Engine | QuickJS |
| Testing | JUnit 5, MockK, Roborazzi |

## Documentation

Detailed documentation is available in the [wiki](docs/wiki/Home.md):

- [Architecture Overview](docs/wiki/Architecture.md) -- Clean Architecture layers and data flow
- [Feature Overview](docs/wiki/Features.md) -- All 49 features with PRD/RFC links
- [Tool Reference](docs/wiki/Tool-Reference.md) -- All built-in tools with parameters
- [API Adapters](docs/wiki/API-Adapters.md) -- OpenAI, Anthropic, and Gemini integration
- [Messaging Bridge](docs/wiki/Bridge-Module.md) -- Multi-channel messaging setup
- [Memory System](docs/wiki/Memory-System.md) -- Persistent cross-session memory
- [Skill System](docs/wiki/Skill-System.md) -- Custom prompt templates
- [Getting Started](docs/wiki/Getting-Started.md) -- Developer setup guide
- [Development Workflow](docs/wiki/Development-Workflow.md) -- Documentation-driven development
- [Project Structure](docs/wiki/Project-Structure.md) -- Annotated directory layout

## Project Philosophy

This project follows a documentation-driven development approach:

1. **PRD first** -- Define what to build and why
2. **RFC second** -- Design how to build it
3. **Code third** -- Implement from the RFC
4. **Test fourth** -- Verify against acceptance criteria

All 49 features have corresponding PRD and RFC documents in `docs/prd/` and `docs/rfc/`.

## License

All rights reserved.
