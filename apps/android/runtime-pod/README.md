# Embedded Runtime Pod

This directory is the source of truth for the Android embedded-runtime packaging spike.

The `prepare` command reads `pod-spec.json`, stages the declared asset roots into a build artifact, and emits stable `manifest.json` plus `layout.json` files for later Android integration.

The staged pod metadata plus `workspace/` assets in this directory are now consumed by `pod.manifest.describe`, `pod.runtime.describe`, `pod.workspace.scan`, and `pod.workspace.read`, which read the extracted app-private pod inside Android and return manifest/layout metadata, the desktop-runtime mainline gap map, workspace inventory, text previews, and packaged document content through a bounded helper surface.
