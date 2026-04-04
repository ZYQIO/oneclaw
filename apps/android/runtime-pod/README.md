# Embedded Runtime Pod

This directory is the source of truth for the Android embedded-runtime packaging spike.

The `prepare` command reads `pod-spec.json`, stages the declared asset roots into a build artifact, and emits stable `manifest.json` plus `layout.json` files for later Android integration.

The staged pod metadata plus `browser/`, `desktop/`, `workspace/`, `toolkit/`, and `runtime/` assets in this directory are now consumed by `pod.browser.describe`, `pod.desktop.materialize`, `pod.manifest.describe`, `pod.runtime.describe`, `pod.runtime.execute`, `pod.workspace.scan`, and `pod.workspace.read`.

`pod.runtime.execute` currently runs the packaged `runtime-smoke` task, the first packaged desktop-tool task `tool-brief-inspect`, and the narrow allowlisted plugin task `plugin-allowlist-inspect` through the bounded Android runtime carrier: it materializes `filesDir/openclaw/embedded-runtime-home/<version>/`, hydrates packaged config into the app-private runtime home, and persists structured state plus logs without exposing an unrestricted shell.

`pod.browser.describe` and `pod.browser.auth.start` now expose the first bounded browser lane as well: they package a single allowlisted OpenAI Codex external-browser auth flow instead of a generic browser runtime, and the describe surface now reports whether that packaged lane has replayable state/log evidence under `filesDir/openclaw/embedded-runtime-home/<version>/`.

`pod.desktop.materialize` is the first direct step back toward the branch's full-desktop objective: it packages the desktop-environment bundle as one `desktop/` stage in the APK, then materializes `filesDir/openclaw/embedded-desktop-home/<version>/` with engine, environment, browser, tools, plugins, supervisor manifests, and an active profile.
