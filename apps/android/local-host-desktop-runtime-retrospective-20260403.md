# Android Desktop Runtime Retrospective / Android 桌面 Runtime 回顾

Date / 日期: April 3, 2026 / 2026-04-03
Branch / 分支: `android-desktop-runtime-mainline-20260403`

## Scope / 范围

This checkpoint records the first three desktop-runtime slices after the Android mainline pivot. / 这份 checkpoint 记录 Android 主线切换之后，前三个 desktop-runtime 小切片的状态。

## What Landed / 已落地内容

- Slice 1: a packaged runtime carrier plus `pod.runtime.execute(taskId=runtime-smoke)`. / 切片 1：packaged runtime carrier，加上 `pod.runtime.execute(taskId=runtime-smoke)`。
- Slice 2: a packaged desktop tool lane via `pod.runtime.execute(taskId=tool-brief-inspect)`. / 切片 2：通过 `pod.runtime.execute(taskId=tool-brief-inspect)` 落地第一条 packaged desktop tool lane。
- Slice 3: a bounded browser-auth lane via `pod.browser.describe` and `pod.browser.auth.start`. / 切片 3：通过 `pod.browser.describe` 和 `pod.browser.auth.start` 落地第一条 bounded browser-auth lane。

## Drift Check / 偏航检查

- The branch is still aligned with the pivot goal. It moved from visibility, to execution, to tools, and only then to a bounded browser lane. / 这条分支仍然和主线切换目标一致：先做可见性，再做执行，再做工具，最后才推进到有边界的 browser lane。
- This branch must not be described as already bundling a generic desktop browser/runtime/plugin environment. / 这条分支不能被描述成“已经内置了通用桌面 browser/runtime/plugin 环境”。
- The current browser slice is explicitly allowlisted around the OpenAI Codex auth flow. / 当前 browser 切片明确是围绕 OpenAI Codex auth flow 的白名单能力。

## Forced Next Gate / 强制下一门槛

- That earlier browser-lane proof is now complete on the connected OPPO / ColorOS `PFEM10` phone. / 前面那道 browser lane 真机门槛现在已经在接入的 OPPO / ColorOS `PFEM10` 真机上完成了。
- The new gate is no longer "prove the browser lane exists," but "keep the desktop-home replay boringly stable and decide whether the next slice is deeper engine/environment execution or one narrowly allowlisted plugin lane." / 新门槛已经不再是“证明 browser lane 存在”，而是“把 desktop-home replay 维持成无聊地稳定，并明确下一刀到底是更深的 engine/environment 执行，还是一条狭义白名单 plugin lane”。

## Iteration 4 / 第四个小切片

- `pod.browser.describe` now reports whether the bounded browser lane has replayable state/log evidence on disk, plus the last persisted launch status. / `pod.browser.describe` 现在会显式报告 bounded browser lane 是否已经在磁盘上留下可复跑的 state/log 证据，以及最后一次持久化的 launch 状态。
- `pod.runtime.describe` no longer treats a pre-existing credential as replay proof by itself. The browser lane now becomes `browser_lane_replayed` only after persisted launch evidence exists, and `browser_lane_configured` only after replay evidence and a stored credential both exist. / `pod.runtime.describe` 不再把“已有 credential”本身当成 replay 证据；现在 browser lane 只有在持久化 launch 证据存在时才会进入 `browser_lane_replayed`，而 `browser_lane_configured` 则要求 replay 证据和存储中的 credential 同时存在。

## Iteration 5 / 第五个小切片

- A dedicated desktop-runtime doctor command now combines token bootstrap, the packaged-pod baseline smoke, and the browser-lane smoke into one top-level summary so device verification later can start from one stable entrypoint. / 现在又补了一条专门的 desktop-runtime doctor 命令，把 token bootstrap、packaged-pod baseline smoke 和 browser-lane smoke 收成了一个顶层 summary，方便后续真机验证从一个稳定入口开始。

## Do Not Reopen / 不要重开

- Do not reopen the old assumption that helper metadata alone answers the desktop-runtime question. / 不要重新回到“helper metadata 本身就回答了 desktop-runtime 问题”的旧假设。
- Do not widen into generic browser tooling before the bounded auth lane is replayable on-device. / 在 bounded auth lane 拿到真机可复跑证据之前，不要扩成通用 browser tooling。

## Iteration 6 / 第六个小切片

- Payload `0.6.0` now carries a packaged `desktop/` environment stage with engine, environment, browser, tools, plugins, supervisor manifests, and one desktop profile descriptor.
- `pod.desktop.materialize` now materializes `filesDir/openclaw/embedded-desktop-home/<version>/`, so the branch has a real app-private desktop home layout instead of only a gap-map description.
- This iteration is the explicit correction back toward the branch's full-desktop objective after the earlier drift into "selected slice" framing.

## Iteration 7 / 第七个小切片

- Real-device replay now reaches `desktop_home_configured` on the connected OPPO / ColorOS `PFEM10` phone after reinstalling the current debug app and rerunning `pnpm android:local-host:embedded-runtime-pod:doctor -- --json`.
- The first direct `pod.desktop.materialize` replay exposed a real repo mismatch: `/invoke/capabilities` already advertised the command, but `/invoke` returned `INVALID_REQUEST: unknown command` because `InvokeCommandRegistry` had not registered `OpenClawPodCommand.DesktopMaterialize`.
- That registry gap is now fixed, and direct `pod.desktop.materialize` replay writes `profiles/active-profile.json` plus `state/desktop-materialize.json` under `filesDir/openclaw/embedded-desktop-home/0.6.0/`.
- The doctor summary is also corrected now so a `desktop_home_configured` branch state is reported as `classification=desktop_home_configured` instead of being collapsed back into `browser_lane_configured`.
