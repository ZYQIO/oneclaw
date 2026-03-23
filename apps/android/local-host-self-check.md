# Android Local Host Self Check

Use this document before calling the Android local-host goal "done".

## Goal Statement

Android should be able to host a local OpenClaw runtime on-device, use Codex authorization for GPT access, and accept safe remote control from a trusted client.

## Exit Criteria

Mark each item only with direct evidence.

### Product Goal

- [ ] `Local Host` mode can be selected and started from the app
- [ ] Codex authorization can be connected from the app without desktop-only steps
- [ ] local-host chat returns a model response on a real phone
- [ ] a remote client can inspect readiness through `/status`
- [ ] a remote client can send chat through `/chat/send-wait`
- [ ] a remote client can execute at least one `/invoke` command successfully

### Safety And Boundaries

- [ ] remote access requires bearer-token auth
- [ ] read-only commands are available without enabling higher-risk tiers
- [ ] camera commands require the advanced tier
- [ ] write-capable commands require the write tier
- [ ] disabled tiers return a clear rejection
- [ ] remote examples and capabilities reflect the actual enabled command tiers

### Codex Integration

- [ ] stored Codex credential survives app restart
- [ ] credential refresh succeeds before expiry
- [ ] missing Codex credential produces a clear error for chat
- [ ] account identifier is resolved correctly for model calls
- [ ] streaming text updates reach the local-host chat pipeline

### Validation Quality

- [ ] unit tests cover the current remote API surface
- [ ] Android build passes in a Java-enabled environment
- [ ] at least one real Android device run has been documented
- [ ] happy-path validation includes networked remote control, not just localhost assumptions

## Self Check Questions

Answer these before adding more features:

1. Can a teammate reproduce the MVP from the repo without tribal knowledge?
2. Do we have proof from a real phone, not just source inspection?
3. If Codex auth breaks, will the user see a clear recovery path?
4. If a remote token leaks, are risky commands still off by default?
5. Is the next planned task reducing uncertainty, or just expanding scope?

If any answer is "no", the goal is not done yet.

## Evidence Log Template

Fill this out during validation runs.

### Environment

- Device:
- Android version:
- Build commit:
- Network setup:

### Local Host

- Local Host start result:
- Codex sign-in result:
- Local chat result:

### Remote Control

- `/status` result:
- `/chat/send-wait` result:
- `/invoke` result:

### Failures

- Missing token behavior:
- Disabled write-tier behavior:
- Missing permission behavior:
- Codex expiry or missing-auth behavior:

### Verdict

- [ ] Go
- [ ] No go

Reason:
