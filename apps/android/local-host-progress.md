# Android Local Host Progress

Scope: track the Android effort to run OpenClaw locally on the phone, authenticate with Codex, and expose a safe remote-control surface.

## Objective

Ship an Android-hosted MVP that can:

- run OpenClaw locally on the phone in `Local Host` mode
- authenticate with `openai-codex`
- send and receive local chat turns through GPT
- expose a guarded remote-control API for trusted remote clients

## Definition Of Done

This effort is considered done when all of the following are true:

- Android can start `Local Host` without relying on an external Gateway
- Codex sign-in works from the app and survives token refresh
- local-host chat succeeds end-to-end on a real phone
- a trusted remote client can call `/status`, `/chat/send-wait`, and at least one `/invoke` command successfully
- remote command tiers are explicitly gated and visible in UI/API
- real-device validation evidence exists for the happy path and key failure paths
- Android build, unit tests, and smoke validation pass in a reproducible environment

## Non Goals

The current MVP does not aim to deliver all desktop Gateway features.

- full plugin runtime parity with the Node Gateway
- full control UI parity with the web Control UI
- unrestricted remote execution or shell access
- public internet exposure without explicit network hardening

## Milestones

### M1. Local Host Foundation

- [x] Add `Local Host` runtime mode in Android UI
- [x] Persist local-host settings and Codex credential state
- [x] Route chat and voice code through transport abstractions so local and remote backends can share UI
- [x] Stand up a local-host runtime with session, history, and chat methods

### M2. Codex Auth And Model Calls

- [x] Add native Codex OAuth login flow
- [x] Persist Codex credential and support refresh
- [x] Call Codex Responses from local-host chat
- [ ] Verify the complete login and refresh flow on a real Android device

### M3. Remote Access MVP

- [x] Add bearer-token protected local-host remote API
- [x] Add chat routes, event polling, and sync chat wait
- [x] Add self-describing routes: `/status`, `/examples`, `/invoke/capabilities`
- [x] Split remote commands into read, camera-advanced, and write-capable tiers
- [ ] Verify a real remote client can drive the phone over the network

### M4. Validation And Hardening

- [ ] Run Android Gradle compile and unit tests in an environment with Java installed
- [ ] Install on a real Android phone and validate the happy path
- [ ] Capture failure behavior for expired Codex auth, missing permissions, and disabled remote tiers
- [ ] Review remote access defaults, token rotation flow, and network guidance
- [ ] Decide whether any additional remote commands should be enabled for MVP

## Current State

Completed implementation highlights:

- `Local Host` mode exists in the Connect tab and is exposed as a first-class runtime option.
- Codex OAuth login exists with browser flow and manual paste fallback.
- Local-host chat is wired to Codex Responses using `gpt-5.4`.
- Remote access exposes `/health`, `/status`, `/examples`, `/chat/*`, `/events`, and `/invoke`.
- Remote invoke commands are tiered so risky actions are not enabled by default.

Current gaps:

- No end-to-end real-device proof has been captured yet.
- Command-line Android validation is blocked until Java is available in the working environment.
- The remote API is usable for tooling, but it is not yet packaged as a polished remote-control product.

## Evidence Checklist

Use this section as an operator log when validating the feature on a phone.

### Build Evidence

- [ ] `./gradlew :app:assembleDebug`
- [ ] `./gradlew :app:testDebugUnitTest`
- [ ] `./gradlew :app:installDebug`

### Device Evidence

- [ ] App starts in `Local Host` mode
- [ ] Codex sign-in succeeds
- [ ] Local chat returns a GPT response
- [ ] `/status` returns `codexAuthConfigured=true`
- [ ] `/chat/send-wait` completes from a remote client
- [ ] One read-only `/invoke` command succeeds
- [ ] One gated command succeeds only when its tier is enabled

### Failure Evidence

- [ ] Invalid bearer token returns `401`
- [ ] Disabled write tier blocks write commands
- [ ] Expired or missing Codex auth fails clearly
- [ ] Permission-dependent commands fail clearly when permission is missing

## Recommended Next Slice

Do not treat the project as fully complete yet.

The next best step is real-device validation, in this order:

1. install the Android build on a real phone
2. sign in with Codex in `Local Host` mode
3. run a local chat turn
4. call `/status` and `/chat/send-wait` from another device
5. exercise one safe `/invoke` command and one gated `/invoke` command

If that passes, the follow-up slice should be validation hardening and user-facing setup guidance, not more feature sprawl.
