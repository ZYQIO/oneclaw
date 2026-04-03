# Embedded Runtime Pod

This directory is the source of truth for the Android embedded-runtime packaging spike.

The `prepare` command reads `pod-spec.json`, stages the declared asset roots into a build artifact, and emits stable `manifest.json` plus `layout.json` files for later Android integration.

The staged pod metadata plus `workspace/`, `toolkit/`, and `runtime/` assets in this directory are now consumed by `pod.manifest.describe`, `pod.runtime.describe`, `pod.runtime.execute`, `pod.workspace.scan`, and `pod.workspace.read`.

`pod.runtime.execute` currently runs both the packaged `runtime-smoke` task and the first packaged desktop-tool task `tool-brief-inspect` through the bounded Android runtime carrier: it materializes `filesDir/openclaw/embedded-runtime-home/<version>/`, hydrates packaged config into the app-private runtime home, and persists structured state plus logs without exposing an unrestricted shell.
