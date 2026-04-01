# Embedded Runtime Pod

This directory is the source of truth for the Android embedded-runtime packaging spike.

The `prepare` command reads `pod-spec.json`, stages the declared asset roots into a build artifact, and emits stable `manifest.json` plus `layout.json` files for later Android integration.
