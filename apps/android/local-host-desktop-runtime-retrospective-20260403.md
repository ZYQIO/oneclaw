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

- Real-device replay for `pod.browser.describe` plus `pod.browser.auth.start`. / 必须补上 `pod.browser.describe` 与 `pod.browser.auth.start` 的真机复跑证据。
- Plugin work remains blocked until that browser-lane proof exists. / 在这份 browser lane 真机证据到位之前，不展开 plugin lane。

## Iteration 4 / 第四个小切片

- `pod.browser.describe` now reports whether the bounded browser lane has replayable state/log evidence on disk, plus the last persisted launch status. / `pod.browser.describe` 现在会显式报告 bounded browser lane 是否已经在磁盘上留下可复跑的 state/log 证据，以及最后一次持久化的 launch 状态。
- `pod.runtime.describe` no longer treats a pre-existing credential as replay proof by itself. The browser lane now becomes `browser_lane_replayed` only after persisted launch evidence exists, and `browser_lane_configured` only after replay evidence and a stored credential both exist. / `pod.runtime.describe` 不再把“已有 credential”本身当成 replay 证据；现在 browser lane 只有在持久化 launch 证据存在时才会进入 `browser_lane_replayed`，而 `browser_lane_configured` 则要求 replay 证据和存储中的 credential 同时存在。

## Do Not Reopen / 不要重开

- Do not reopen the old assumption that helper metadata alone answers the desktop-runtime question. / 不要重新回到“helper metadata 本身就回答了 desktop-runtime 问题”的旧假设。
- Do not widen into generic browser tooling before the bounded auth lane is replayable on-device. / 在 bounded auth lane 拿到真机可复跑证据之前，不要扩成通用 browser tooling。
