# Remote Control Foundation

This document describes the current remote-control foundation added to the OneClaw workspace.

## Components

- `:remote-core`
  - Shared Android library for remote device models, broker protocol, root capture/input backends, and file transfer backend.
- `:remote-host`
  - Standalone Android host app deployed on the controlled phone.
- `remote-broker/`
  - Lightweight Node.js WebSocket broker and static file server.
- `remote-console-web/`
  - Browser control console served by the broker.
- `:app`
  - OneClaw integration: remote repository, screen, navigation entry, and remote tool group.

## Current Capability Level

### Root path

Implemented:
- Device registration
- Pairing by short code
- Session open / close
- Repeated screenshot capture using `screencap -p`
- Input injection using `input`
- App launch via `monkey`
- File list / upload / download inside host app sandbox
- OneClaw tool access

### Non-root compatibility path

Scaffold only:
- Accessibility service
- MediaProjection capture backend interface
- Host UI messaging

Not yet implemented end-to-end:
- MediaProjection authorization flow
- Gesture injection through accessibility
- Stable unattended compatibility mode

## Startup Order

### 1. Start the broker

```bash
cd remote-broker
npm install
npm start
```

Broker endpoints:
- WebSocket: `ws://<host>:8080/ws`
- Browser console: `http://<host>:8080/`
- Health check: `http://<host>:8080/healthz`
- Runtime state: `http://<host>:8080/api/state`

### 2. Install and launch `remote-host`

Build/install once Java + Android SDK are available:

```bash
./gradlew :remote-host:installDebug
```

In the app:
- Set the broker URL
- Record or rotate the pair code
- Start the foreground service
- For root devices, verify `mode=root` in the runtime status card

### 3. Open the browser console

Visit:

```text
http://<broker-host>:8080/
```

Then:
- Connect the WebSocket
- Refresh devices
- Select a device
- Pair with the device code
- Open a session
- Request snapshots / tap on the snapshot / send text / upload or download files

### 4. Use from OneClaw

Inside OneClaw:
- Open `Settings -> Remote Control` for manual control
- Load the `remote` tool group for AI-assisted control

## Limitations

- Full unattended control currently depends on root access on the host phone.
- Remote viewing is still screenshot-polling, not H.264 video streaming.
- File transfer is limited to the host app sandbox share directory.
- Pairings are in-memory in the broker and are lost on broker restart.
- The environment used during implementation did not have Java installed, so Android builds/tests could not be executed here.
