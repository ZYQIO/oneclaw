# OneClaw Handover Roadmap

## Purpose

This document defines the implementation roadmap I would use when taking over the current OneClaw project.

The roadmap is optimized for the user's stated priorities:
- Android-first delivery
- Remote control running on Android phones
- OneClaw-like agent features running on Android phones
- GPT-series access planned around Codex authorization
- Plan first, code second

This is a handover and execution plan, not an RFC. It does not replace feature RFCs. It defines sequencing, priorities, acceptance gates, and fallback decisions.

## Guiding Decisions

### 1. Ship the Android path first

The immediate goal is not cross-platform generality. The immediate goal is to make the Android-based system run end-to-end on real devices.

### 2. Treat remote control as a product line, not a side feature

Remote control is no longer a small add-on. It now spans:
- `:app`
- `:remote-host`
- `:remote-core`
- `remote-broker`
- `remote-console-web`

This means it must be planned, tested, and hardened as a first-class subsystem.

### 3. Prioritize rooted-host remote control before non-root compatibility

The current codebase and documents already indicate that the rooted path is the most complete and closest to usable. The non-root path remains scaffolded and should not block the first usable release.

### 4. Plan GPT access around Codex authorization, but validate the integration boundary first

The local Codex CLI available in the development environment exposes both:
- `codex login --device-auth`
- `codex login --with-api-key`

This is strong evidence that Codex authorization is a real authentication mode in the current tooling. However, the Android app should not assume that the CLI login flow can be reused directly inside the app without a dedicated integration design.

So the rule is:
- Codex authorization is the target direction
- A technical probe must verify how it can be reused or bridged for Android
- The app authentication layer must remain replaceable

### 5. Freeze scope per phase

Each phase should have a clear exit gate. No phase should grow by absorbing adjacent future work.

## Current State Summary

Based on the current repository state:
- OneClaw already runs as an Android app with provider integration, tools, memory, skills, scheduled tasks, bridge support, and session management.
- Remote control foundation has been added, including:
  - manual remote control screen in `:app`
  - remote tool group for AI agent access
  - standalone Android `:remote-host`
  - shared `:remote-core`
  - Node.js `remote-broker`
  - browser-based `remote-console-web`
- The rooted remote-control path is implemented at a foundation level.
- The non-root path is scaffolded but not complete end-to-end.
- Build and test verification are currently blocked in this environment because Java is missing.

## Phase Plan

## Phase 0: Project Baseline and Recovery

### Goal

Create a stable starting point where the project can be built, installed, and reasoned about confidently.

### Tasks

- Install and verify Java 17, Android SDK, Gradle, and emulator prerequisites
- Run the current Android build and test commands
- Confirm the minimal boot path for:
  - `:app`
  - `:remote-host`
  - `remote-broker`
- Reconcile document drift between current code and older project summaries
- Produce a "known-good baseline" report

### Acceptance Criteria

- `./gradlew test` runs successfully or produces a concrete fix list
- `./gradlew assembleDebug` succeeds
- `./gradlew :remote-host:assembleDebug` succeeds
- `remote-broker` starts and passes health check
- Current limitations are documented as code-backed facts, not guesses

### Output

- Build baseline report
- Environment setup notes
- Corrected implementation status snapshot

## Phase 1: Codex Authorization Technical Probe

### Goal

Determine how GPT-series access should be integrated into this Android architecture when Codex authorization is the preferred direction.

### Questions to Answer

- Can Android reuse Codex device authorization directly?
- If not directly, can Android safely bridge through a local or external helper?
- What state must be stored locally after authorization?
- What renewal and logout behavior are required?
- What is the fallback if the Codex-auth path is not app-embeddable?

### Tasks

- Inspect the local Codex login flow and observable auth artifacts
- Define an app-facing `AuthProvider` abstraction
- Compare at least these options:
  - embedded device-auth flow in app
  - companion bridge to Codex-authenticated environment
  - fallback provider auth using API key
- Define the minimum token/session model needed by the app
- Define security constraints for on-device storage and refresh

### Acceptance Criteria

- A written integration decision exists:
  - `direct`
  - `bridged`
  - `deferred with fallback`
- App auth interfaces are designed before GPT integration coding starts
- No model/provider code depends on a single hardcoded auth path

### Output

- Codex authorization probe note
- Auth abstraction design
- Security and fallback decision record

## Phase 2: Rooted Remote Control MVP Hardening

### Goal

Turn the current remote-control foundation into a real Android-usable rooted-host MVP.

### Scope

In scope:
- broker connection
- device discovery
- pairing
- session open/close
- snapshot refresh
- tap/swipe/text/key/home/back
- file upload/download within allowed sandbox scope
- AI agent access to the remote tool group

Out of scope:
- non-root unattended mode
- full video streaming
- cloud multi-tenant architecture

### Tasks

- Verify rooted-host execution on real Android devices
- Harden error handling around broker disconnects and session timeouts
- Improve remote status visibility in both manual UI and tool responses
- Add missing tests around the remote repository and remote tools
- Add manual verification flows for real-device rooted usage

### Acceptance Criteria

- A rooted Android host can be paired and controlled end-to-end
- Snapshot and input commands work reliably in repeated sessions
- Remote tool calls from the AI agent complete with actionable results
- Failure modes produce understandable user-visible errors

### Output

- Rooted remote-control MVP
- Hardened tests and manual verification report

## Phase 3: OneClaw-to-Remote Integration Completion

### Goal

Make remote control feel native inside OneClaw rather than a newly added sidecar feature.

### Tasks

- Align manual remote UI and remote tool behavior around the same session model
- Clarify when AI agents are allowed to control a device
- Add guardrails and confirmations for high-impact remote actions
- Improve navigation and state recovery between chat and remote control
- Record remote execution outcomes in a way that is inspectable later

### Acceptance Criteria

- Manual control and AI control share consistent device/session state
- User consent and device-control boundaries are explicit
- Remote action outcomes are traceable during debugging and user review

### Output

- Integrated remote-control user experience inside OneClaw

## Phase 4: GPT Integration Through the Auth Layer

### Goal

Connect GPT-series models using the authentication strategy validated in Phase 1.

### Tasks

- Introduce the selected auth provider implementation
- Route OpenAI/GPT requests through the auth abstraction
- Keep model/provider interfaces compatible with future auth changes
- Verify streaming, tool calling, and error handling under the chosen auth model
- Confirm the interaction with existing provider management UI

### Acceptance Criteria

- GPT-series requests work through the approved auth path
- Existing chat, tool loop, and message streaming continue to function
- Authentication failures and re-auth flows are user-visible and recoverable

### Output

- Android GPT integration aligned with Codex-auth strategy

## Phase 5: Non-Root Compatibility Path

### Goal

Make remote control usable on non-root Android hosts.

### Tasks

- Implement MediaProjection authorization flow
- Implement AccessibilityService-backed gesture and input execution
- Define supported capabilities under compatibility mode
- Update the host UI and controller UI to present capability differences clearly
- Add tests and manual verification for compatibility mode

### Acceptance Criteria

- Non-root host can complete the supported subset of remote actions end-to-end
- Unsupported actions fail clearly instead of silently
- Capability reporting matches real behavior

### Output

- Non-root compatibility release

## Architecture Work Required Before Major Coding

Before major implementation resumes, these interfaces should be treated as design anchors:

- `AuthProvider`
  - Purpose: abstract Codex authorization, API-key auth, and any future auth mode
- `RemoteSessionManager`
  - Purpose: keep manual UI and AI tool calls aligned around the same remote session state
- `RemoteCapabilityPolicy`
  - Purpose: gate what actions are allowed in root vs compatibility mode
- `RemoteAuditLog`
  - Purpose: provide traceability for device-control operations

These do not all need to ship at once, but they should inform the implementation boundaries.

## Testing Strategy by Phase

## Baseline
- Build checks
- JVM tests
- Instrumented tests where feasible
- Broker health checks

## Remote MVP
- Real rooted Android device verification
- Session open/close repetition
- Snapshot polling stability
- Input command reliability
- Tool-call integration checks

## GPT Integration
- Auth success path
- Auth expired path
- Streaming response path
- Tool-calling path
- Re-auth path

## Non-Root
- MediaProjection permission flow
- Accessibility permission flow
- Gesture delivery checks
- Capability mismatch handling

## Risk Register

### High Risk

- Codex authorization may require a bridge instead of direct Android embedding
- Rooted-host behaviors may vary significantly across devices
- Current environment cannot yet verify Android builds

### Medium Risk

- Broker state is currently memory-based and may not survive restart
- Snapshot-polling may be too slow for some workflows
- Manual UI and AI tool flows may drift if session logic is not unified early

### Low Risk

- Documentation drift can be corrected incrementally once the baseline is established

## Fallback Rules

If Phase 1 concludes that Codex authorization cannot be embedded in the app safely in the short term:
- keep Codex authorization as the preferred long-term direction
- implement the auth abstraction anyway
- allow a temporary fallback auth provider for development and verification
- do not entangle GPT access with app-internal assumptions about one auth mode

If non-root compatibility slips:
- ship rooted-host MVP first
- keep compatibility mode clearly labeled as limited or experimental

## Immediate Next Actions

If I were to take over execution immediately, I would do these next:

1. Restore buildability and run the current Android commands
2. Produce the baseline report
3. Run the Codex authorization technical probe
4. Freeze the auth abstraction decision
5. Start rooted remote-control hardening

## Definition of Success

The project is on the right track when all of the following are true:
- OneClaw runs reliably on Android phones
- remote control works end-to-end on Android rooted hosts
- AI agents can use remote tools safely and predictably
- GPT access is integrated through a replaceable auth layer aligned with Codex authorization goals
- non-root support becomes a scheduled expansion, not a blocker to the first usable system
