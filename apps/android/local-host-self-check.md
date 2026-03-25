# Android Local Host Self Check / Android 本机 Host 自检

Use this document before calling the Android local-host goal "done". / 在宣布 Android 本机 Host 目标“完成”之前，请先对照本文件做自检。

## Goal Statement / 目标说明

Android should be able to host a local OpenClaw runtime on-device, use Codex authorization for GPT access, and accept safe remote control from a trusted client. / Android 应该能够在设备本机托管 OpenClaw 运行时，使用 Codex 授权访问 GPT，并接受来自可信客户端的安全远程控制。

## Exit Criteria / 退出标准

Mark each item only with direct evidence. / 每一项都必须基于直接证据勾选，不能凭推测。

### Product Goal / 产品目标

- [x] App 中可以选择并启动 `Local Host` 模式。`Local Host` mode can be selected and started from the app.
- [x] Codex 授权可以直接在 App 内完成，不依赖桌面专属步骤。Codex authorization can be connected from the app without desktop-only steps.
- [x] 本机 Host 聊天能在真实手机上返回模型响应。Local-host chat returns a model response on a real phone.
- [x] 远端客户端可以通过 `/status` 查看可用性快照。A remote client can inspect readiness through `/status`.
- [x] 远端客户端可以通过 `/chat/send-wait` 发起聊天。A remote client can send chat through `/chat/send-wait`.
- [x] 远端客户端可以成功执行至少一个 `/invoke` 命令。A remote client can execute at least one `/invoke` command successfully.

### Safety And Boundaries / 安全与边界

- [x] 远程访问必须要求 bearer token。Remote access requires bearer-token auth.
- [x] 不开启高风险层时，只读命令可以单独使用。Read-only commands are available without enabling higher-risk tiers.
- [x] 相机命令必须依赖高级命令层。Camera commands require the advanced tier.
- [x] 写操作命令必须依赖写命令层。Write-capable commands require the write tier.
- [x] 关闭对应命令层时会得到清晰拒绝。Disabled tiers return a clear rejection.
- [x] 远程 examples 和 capabilities 与实际启用的命令层一致。Remote examples and capabilities reflect the actual enabled command tiers.

### Codex Integration / Codex 集成

- [x] 已保存的 Codex 凭证在 App 重启后仍可用。Stored Codex credential survives app restart.
- [x] 凭证会在过期前成功刷新。Credential refresh succeeds before expiry.
- [x] 缺失 Codex 凭证时，聊天会给出清晰错误。Missing Codex credential produces a clear error for chat.
- [x] 模型调用所需的 account identifier 会被正确解析。Account identifier is resolved correctly for model calls.
- [x] 流式文本更新能进入本机 Host 聊天管线。Streaming text updates reach the local-host chat pipeline.

Code-level confidence note / 代码级确认:
`OpenAICodexResponsesClient` streams `response.output_text.delta`, `LocalHostRuntime` re-emits assistant delta as both `event:"agent"` and `state:"delta"` chat payloads, `ChatController` maps those into `streamingAssistantText`, and `ChatMessageListCard` renders the streaming bubble. On March 25, 2026, after restoring egress through a trusted LAN proxy and topping up Codex usage, `pnpm android:local-host:streaming` completed successfully with `deltaCount=68` and `terminalState=final`, so the remaining work is no longer proving the streaming path itself. / `OpenAICodexResponsesClient` 已会流式处理 `response.output_text.delta`，`LocalHostRuntime` 会把 assistant delta 同时重发成 `event:"agent"` 和 `state:"delta"` 的聊天事件，`ChatController` 会把它们映射到 `streamingAssistantText`，`ChatMessageListCard` 也会渲染流式气泡。2026 年 3 月 25 日，在通过可信 LAN 代理恢复外网出口并补充 Codex 额度后，`pnpm android:local-host:streaming` 已成功完成，记录到 `deltaCount=68` 和 `terminalState=final`，因此后续工作已不再是证明 streaming 路径本身。

### Validation Quality / 验证质量

- [x] 单测覆盖当前远程 API 面。Unit tests cover the current remote API surface.
- [x] Android 构建能在装有 Java 的环境中通过。Android build passes in a Java-enabled environment.
- [x] 至少已经有一次真实 Android 设备运行记录。At least one real Android device run has been documented.
- [x] 成功路径验证包含真实网络远程控制，而不只是 localhost 假设。Happy-path validation includes networked remote control, not just localhost assumptions.

## Self Check Questions / 自检问题

Answer these before adding more features. / 在继续扩功能之前，先回答下面这些问题。

1. Teammate 是否可以不依赖口头说明，直接从仓库复现这个 MVP？Can a teammate reproduce the MVP from the repo without tribal knowledge?
2. 我们是否已经有真实手机上的证据，而不只是源码分析？Do we have proof from a real phone, not just source inspection?
3. 如果 Codex 授权失效，用户是否能看到清晰的恢复路径？If Codex auth breaks, will the user see a clear recovery path?
4. 如果远程 token 泄露，高风险命令是否仍默认关闭？If a remote token leaks, are risky commands still off by default?
5. 下一项计划任务是在降低不确定性，还是只是在扩大范围？Is the next planned task reducing uncertainty, or just expanding scope?

If any answer is "no", the goal is not done yet. / 只要其中任意一项答案是 “no”，就不能认为目标已经完成。

## Evidence Log Template / 证据记录模板

Fill this out during validation runs. / 在执行验证时填写本节。

### Environment / 环境

- Device / 设备: `<android-device>`
- Android version / Android 版本: `15`
- Build commit / 构建提交: `post-Codex-validation working tree on March 23, 2026`
- Network setup / 网络环境: host -> phone over LAN (`http://<phone-ip>:3945`); `adb forward` connected but did not return `/status`; direct phone egress to `chatgpt.com` timed out on the current network, so the successful streaming proof used a trusted LAN proxy path through the host

### Local Host / 本机 Host

- Local Host start result / 本机 Host 启动结果: `Connected`; app entered `Local Host` after onboarding bypass for validation
- Codex sign-in result / Codex 登录结果: completed in-app through the browser flow; `/status` reports `codexAuthConfigured=true`
- Local chat result / 本地聊天结果: `/chat/send-wait` returned `Android local host is working.`
- Streaming text result / 流式文本结果: on March 25, 2026, a long-prompt run first showed repeated `chat state=delta` events and eventually reached `state=final` in raw `/events`; a follow-up `pnpm android:local-host:streaming` run with a shorter prompt then passed directly with `deltaCount=68` and `terminalState=final`

### Remote Control / 远程控制

- `/status` result / `/status` 结果: `200 OK`, `codexAuthConfigured=true`, remote tiers reflected correctly
- `/chat/send-wait` result / `/chat/send-wait` 结果: `200 OK` with terminal `state=final`, message `Android local host is working.`
- `/invoke` result / `/invoke` 结果: `device.status` returned battery / thermal / storage / network payload
- Smoke script result / 冒烟脚本结果: `bash apps/android/scripts/local-host-remote-smoke.sh` succeeded over LAN and returned `Android local host smoke passed.`

### Failures / 失败场景

- Missing token behavior / token 缺失行为: `/status` returned `401` with `Missing or invalid bearer token`
- Disabled write-tier behavior / 写命令层关闭行为: `/invoke` with `sms.send` returned `command is not enabled for remote access`
- Missing permission behavior / 权限缺失行为: `contacts.search` -> `CONTACTS_PERMISSION_REQUIRED`, `calendar.events` -> `CALENDAR_PERMISSION_REQUIRED`, `photos.latest` -> `PHOTOS_PERMISSION_REQUIRED`, `system.notify` -> `NOT_AUTHORIZED: notifications`
- Codex expiry or missing-auth behavior / Codex 过期或缺失行为: clear missing-auth error observed from `/chat/send-wait`; proactive `POST /auth/codex/refresh` succeeded and advanced `expiresAt`
- Codex usage-limit behavior / Codex 额度耗尽行为: an earlier March 25, 2026 streaming retry reached the service through a trusted LAN proxy, then `/events` returned `The usage limit has been reached | errorType=usage_limit_reached` before any `state=delta`; after usage was topped up, the same validation path succeeded

### Verdict / 结论

- [x] Go / 通过
- [ ] No go / 不通过

Reason / 原因: every exit criterion in this file now has direct evidence, including a real-device `delta -> final` streaming capture.
Reason addendum / 补充说明: a deliberately forced "signed in but truly expired token" repro is still optional hardening, but it is no longer treated as an MVP blocker because missing-auth errors, proactive refresh, and `auth/codex/status` already give a clear recovery path. / 补充说明：刻意构造“已登录但真过期 token”的复现仍可作为可选加固，但它已不再视为 MVP blocker，因为缺失授权错误、主动 refresh 与 `auth/codex/status` 已经提供了清晰恢复路径。

## Next Session Focus / 下一会话重点

Use this as the shortest checklist when resuming work. / 新会话恢复工作时，可直接把这一节当作最短清单。

1. 决定先做 setup/packaging polish，还是切到 UI 自动化下一阶段。Decide whether setup/packaging polish or the next UI-automation phase comes next.
2. 如果还想继续加固，再决定是否值得补一个 debug-only expired-auth 验证入口。If more hardening is still wanted, decide whether a debug-only expired-auth validation path is worth adding.
3. 保持文档对当前 `Go` 结论一致。Keep the docs aligned on the current `Go` verdict.

Recommended evidence updates / 建议补充的证据:

- 在本文件中保留 streaming 已通过的直接证据，并把 `usage_limit_reached` 继续记成 account-quota 边界，而不是实现回归。Keep the direct evidence that streaming has now passed in this file, and continue recording `usage_limit_reached` as an account-quota boundary rather than an implementation regression.
- 把 `apps/android/README.md` 继续作为远程默认值和 token 指引的基线文档。Keep `apps/android/README.md` as the baseline doc for remote defaults and token guidance.
- 在 `apps/android/local-host-progress.md` 追加新的日期日志。Append a new dated log entry to `apps/android/local-host-progress.md`.
- 如果需要将来重新验证 streaming，优先跑 `pnpm android:local-host:streaming`；它会要求同一条 run 在 `final` 前至少出现一次 `chat state=delta`，并保存原始事件与 summary JSON。若结果再次是 `usage_limit_reached`，不要继续当成 Android bug 排查。If streaming needs to be revalidated in the future, prefer running `pnpm android:local-host:streaming`; it requires the same run to emit at least one `chat state=delta` before `final`, and it saves raw events plus a summary JSON. If the result is `usage_limit_reached` again, do not keep treating it as an Android bug hunt.

Avoid drifting scope / 避免范围漂移:

- 不要先扩更多远程命令；当前 MVP 命令面已经视为冻结。Do not expand the remote command surface first; the current MVP command surface is now considered frozen.
- 不要先改公网暴露方案。Do not redesign public exposure first.
- 先把收尾判断和文档边界写清楚，再决定是否继续加功能。Write down the close-out judgment and doc boundaries before deciding on more features.
