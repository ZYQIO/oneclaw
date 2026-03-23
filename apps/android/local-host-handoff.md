# Android Local Host Handoff / Android 本机 Host 接续手册

Purpose / 用途: make it easy to resume the Android local-host effort in a fresh chat without re-discovering the current state. / 让新会话可以快速接手 Android 本机 Host 项目，不必重新摸索当前状态。

## Current Objective / 当前目标

The active goal is still the same: / 当前目标仍然不变：

- 在 Android 手机上以 `Local Host` 模式本机运行 OpenClaw。Run OpenClaw on the phone itself in `Local Host` mode.
- 使用 Codex 授权访问 GPT。Use Codex auth for GPT access.
- 通过受保护的远程 API 从另一台设备控制手机上的本机 Host。Control the on-device local host from another trusted device through the guarded remote API.

## Current Status Snapshot / 当前状态快照

As of March 23, 2026, the MVP happy path is working. / 截至 2026 年 3 月 23 日，MVP 的成功路径已经跑通。

- `Local Host` 可以在真实 Android 手机上启动。`Local Host` can start on a real Android phone.
- Codex 浏览器授权已经完成，并且 `/status` 会返回 `codexAuthConfigured=true`。Codex browser auth completes, and `/status` returns `codexAuthConfigured=true`.
- 远控 `/chat/send-wait` 已成功返回模型回复。Remote `/chat/send-wait` has successfully returned a model reply.
- 远控 `/invoke` 的只读命令已成功执行。Read-only `/invoke` commands have succeeded remotely.
- 高风险命令层在关闭时会被明确拒绝。Higher-risk command tiers are clearly rejected when disabled.
- LAN 冒烟脚本已经成功。The LAN smoke script has succeeded.
- `auth/codex/status` 和 `auth/codex/refresh` 已在真机验证成功。`auth/codex/status` and `auth/codex/refresh` have both been validated successfully on-device.
- 权限缺失脚本已经在真机上成功覆盖四类失败。The permission-failure script has already covered four failure cases successfully on-device.

What is still missing / 仍未完成的部分:

- 远程默认值和网络暴露说明的最后复核。Final review of remote defaults and network-exposure guidance.
- 自检里 streaming-text 那一项是否仍然需要真机补证，需要最后判断。We still need a final decision on whether the streaming-text self-check item requires dedicated on-device evidence.

## Today Work Log / 今日工作日志

### March 23, 2026 / 2026 年 3 月 23 日

- 完成真实设备构建、安装、重启和 `Local Host` 联调。Finished real-device build, install, relaunch, and `Local Host` bring-up.
- 完成 Codex 登录闭环。Finished the Codex sign-in loop.
- 授权成功后定位到 Codex Responses 的 `400 {"detail":"Unsupported content type"}`。After auth succeeded, identified Codex Responses returning `400 {"detail":"Unsupported content type"}`.
- 修正 `apps/android/app/src/main/java/ai/openclaw/app/host/OpenAICodexResponsesClient.kt` 的请求兼容性，并为结构化错误和请求形状补了测试。Fixed request compatibility in `apps/android/app/src/main/java/ai/openclaw/app/host/OpenAICodexResponsesClient.kt` and added tests for request shape plus structured errors.
- 把 `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostRuntime.kt` 的本机会话 ID 改成 UUID，避免继续沿用 `main` 这种非会话标识。Changed local-host session IDs in `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostRuntime.kt` to UUIDs instead of reusing `main`.
- 重新安装 APK 后，远控 `/chat/send-wait` 成功返回 `Android local host is working.`。After reinstalling the APK, remote `/chat/send-wait` returned `Android local host is working.` successfully.
- 验证了无效 token、关闭的相机层、关闭的写命令层这三类边界。Validated three boundary cases: invalid token, disabled camera tier, and disabled write tier.
- 运行 `bash apps/android/scripts/local-host-remote-smoke.sh` 成功。Ran `bash apps/android/scripts/local-host-remote-smoke.sh` successfully.
- 新增 `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostCodexAuthController.kt`，并把 `auth/codex/status`、`auth/codex/refresh` 接入远控面。Added `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostCodexAuthController.kt` and wired `auth/codex/status` plus `auth/codex/refresh` into remote access.
- 真机调用 `POST /api/local-host/v1/auth/codex/refresh` 成功，`expiresAt` 向前推进，之后 `/chat/send-wait` 仍返回 `Codex refresh still works.`。On-device `POST /api/local-host/v1/auth/codex/refresh` succeeded, `expiresAt` moved forward, and `/chat/send-wait` still returned `Codex refresh still works.` afterward.
- 新增并运行 `bash apps/android/scripts/local-host-permission-smoke.sh`，覆盖 `contacts.search`、`calendar.events`、`photos.latest`、`system.notify` 的权限缺失错误。Added and ran `bash apps/android/scripts/local-host-permission-smoke.sh`, covering permission-missing errors for `contacts.search`, `calendar.events`, `photos.latest`, and `system.notify`.

## Verified Commands / 已验证命令

Use placeholders instead of real live values. / 这里统一使用占位符，不写真实值。

Build and test / 构建与测试:

```bash
cd apps/android
./gradlew --no-daemon --console=plain :app:assembleDebug
./gradlew --no-daemon --console=plain :app:testDebugUnitTest \
  --tests ai.openclaw.app.SecurePrefsTest \
  --tests ai.openclaw.app.host.LocalHostCodexAuthControllerTest \
  --tests ai.openclaw.app.host.LocalHostRuntimeTest \
  --tests ai.openclaw.app.host.LocalHostRemoteAccessServerTest \
  --tests ai.openclaw.app.host.OpenAICodexResponsesClientTest
```

Install and relaunch / 安装与重启:

```bash
adb install -r -d apps/android/app/build/outputs/apk/debug/openclaw-2026.3.14-debug.apk
adb shell am force-stop ai.openclaw.app
adb shell am start -n ai.openclaw.app/.MainActivity
```

Remote status / 远控状态:

```bash
curl -sS \
  -H 'Authorization: Bearer <token-from-connect-tab>' \
  http://<phone-ip>:3945/api/local-host/v1/status
```

Remote Codex auth / 远控 Codex 授权:

```bash
curl -sS \
  -H 'Authorization: Bearer <token-from-connect-tab>' \
  http://<phone-ip>:3945/api/local-host/v1/auth/codex/status

curl -sS -X POST \
  -H 'Authorization: Bearer <token-from-connect-tab>' \
  http://<phone-ip>:3945/api/local-host/v1/auth/codex/refresh
```

Remote chat / 远控聊天:

```bash
curl -sS -X POST \
  -H 'Authorization: Bearer <token-from-connect-tab>' \
  -H 'Content-Type: application/json' \
  http://<phone-ip>:3945/api/local-host/v1/chat/send-wait \
  -d '{"message":"Reply with exactly: Android local host is working.","waitMs":60000}'
```

Remote invoke / 远控调用:

```bash
curl -sS -X POST \
  -H 'Authorization: Bearer <token-from-connect-tab>' \
  -H 'Content-Type: application/json' \
  http://<phone-ip>:3945/api/local-host/v1/invoke \
  -d '{"command":"device.status","args":{}}'
```

Smoke script / 冒烟脚本:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL='http://<phone-ip>:3945' \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
bash apps/android/scripts/local-host-remote-smoke.sh
```

Permission smoke / 权限冒烟:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL='http://<phone-ip>:3945' \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
bash apps/android/scripts/local-host-permission-smoke.sh
```

## Known Good Code Areas / 当前可信代码区域

- `apps/android/app/src/main/java/ai/openclaw/app/host/OpenAICodexResponsesClient.kt`
- `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostCodexAuthController.kt`
- `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostRuntime.kt`
- `apps/android/app/src/test/java/ai/openclaw/app/host/LocalHostCodexAuthControllerTest.kt`
- `apps/android/app/src/test/java/ai/openclaw/app/host/OpenAICodexResponsesClientTest.kt`
- `apps/android/app/src/test/java/ai/openclaw/app/host/LocalHostRuntimeTest.kt`
- `apps/android/local-host-progress.md`
- `apps/android/local-host-self-check.md`

## Known Quirks / 已知注意事项

- `./gradlew :app:installDebug` 在这台设备上可能触发 ddmlib `InstallException: -99`，但直接 `adb install -r -d ...apk` 是可行的。`./gradlew :app:installDebug` may hit ddmlib `InstallException: -99` on this device, but direct `adb install -r -d ...apk` works.
- `pnpm android:local-host:smoke` 依赖当前 shell 能找到 `pnpm`；如果环境里 `pnpm` shim 不可用，直接调用脚本本体即可。`pnpm android:local-host:smoke` depends on a working `pnpm` shim; if `pnpm` is unavailable in the shell, run the script directly instead.
- 这台 Android 15 设备拒绝 shell 侧 `pm revoke` 和 `appops set`；权限脚本会在权限已被拒绝时直接验证失败路径，只在权限已授予时才尝试临时撤回。This Android 15 device rejects shell-side `pm revoke` and `appops set`; the permission script validates already-denied cases directly and only attempts temporary revocation when a permission starts granted.
- 不要把真实 token、真实手机 IP、或个人设备标识写进提交。Do not commit real tokens, the real phone IP, or personal device identifiers.

## Next Tasks / 接下来要做的事

### P0 / 最高优先级

1. 复核远程访问默认值和 token 轮换说明。Review remote defaults and token-rotation guidance.
2. 判断 streaming-text 自检项是否仍需真机补证。Decide whether the streaming-text self-check item still needs dedicated real-device evidence.

### P1 / 次优先级

1. 判断 MVP 是否还要开放更多命令。Decide whether the MVP needs any more commands at all.
2. 如需补证，再做一次更强的 expired-auth 验证。If more evidence is needed, run a stronger expired-auth validation.

## Suggested Next-Session Plan / 下一会话建议推进方式

1. 先读 `apps/android/local-host-progress.md` 的 `Resume Plan`。Start with the `Resume Plan` in `apps/android/local-host-progress.md`.
2. 先跑一次 `/status` 或冒烟脚本，确认手机仍在 `Local Host`。Run `/status` or the smoke script to confirm the phone is still in `Local Host`.
3. 优先收尾远程默认值和自检结论，再决定是否需要新增功能。Close out remote defaults and the self-check verdict before deciding on new feature work.
4. 如果要继续补证，优先看 `apps/android/local-host-self-check.md` 里唯一未勾选的项。If more evidence is needed, start with the only unchecked item in `apps/android/local-host-self-check.md`.

## Related Docs / 相关文档

- `apps/android/local-host-progress.md`
- `apps/android/local-host-self-check.md`
- `apps/android/README.md`
