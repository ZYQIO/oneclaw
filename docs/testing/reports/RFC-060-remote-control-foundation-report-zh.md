# 测试报告：RFC-060 — 远程控制基础版

## 报告信息

| 字段 | 内容 |
|------|------|
| RFC | RFC-060 |
| 关联功能 | 远程控制基础版 |
| Commit | `46101c12` |
| 日期 | 2026-03-16 |
| 测试人 | AI（Codex） |
| 状态 | PARTIAL |

## 摘要

本次实现打通了第一版远程控制基础链路，覆盖：
- `:remote-core` 共享 Android 库
- `:remote-host` 独立被控端 Android App 脚手架
- `remote-broker/` WebSocket Broker
- `remote-console-web/` 浏览器控制台
- `:app` OneClaw 集成（页面、仓库、DI、导航、工具组、测试）

由于当前执行环境未安装 Java 运行时，Android 侧 Gradle 测试无法启动。Broker 和 Web 控制台已通过 JavaScript 静态语法校验。

| 层级 | 步骤 | 结果 | 说明 |
|------|------|------|------|
| 1A | JVM 单元测试 | SKIP | `./gradlew test` 无法启动：缺少 Java Runtime |
| 1B | Instrumented DAO 测试 | SKIP | 因 Gradle 无法启动，未执行模拟器测试 |
| 1B | Instrumented UI 测试 | SKIP | 因 Gradle 无法启动，未执行模拟器测试 |
| 1C | Roborazzi 截图测试 | SKIP | 已新增截图测试代码，但 Gradle 无法启动 |
| 2 | adb 可视化验证 | SKIP | 当前环境无法构建/安装 Android APK |

## Layer 1A：JVM 单元测试

**命令：** `./gradlew test`

**结果：** SKIP

**跳过原因：** 当前主机环境没有 Java 运行时，Gradle 在配置前直接退出，报错为：

`Unable to locate a Java Runtime.`

本次新增的重点测试类：
- `app/src/test/kotlin/com/oneclaw/shadow/tool/builtin/remote/RemoteToolsTest.kt`
- `app/src/test/kotlin/com/oneclaw/shadow/screenshot/RemoteControlScreenshotTest.kt`

## Layer 1B：Instrumented Tests

**命令：** `ANDROID_SERIAL=emulator-5554 ./gradlew connectedAndroidTest`

**结果：** SKIP

**跳过原因：** 由于缺少 Java，Gradle 无法启动。

**测试数量：** 0

## Layer 1C：Roborazzi 截图测试

**命令：**
```bash
./gradlew recordRoborazziDebug
./gradlew verifyRoborazziDebug
```

**结果：** SKIP

**跳过原因：** 由于缺少 Java，Gradle 无法启动。

### 截图覆盖

本次已新增的目标截图：
- `RemoteControlScreen` — 默认已连接状态

## Layer 2：adb 可视化验证

**结果：** SKIP

**跳过原因：** 当前环境无法启动 Gradle，因此无法构建、安装和执行 adb 流程。

## 额外静态校验

- `node --check remote-broker/server.mjs` — PASS
- `node --check remote-console-web/app.js` — PASS
- `npm install --prefix remote-broker --no-package-lock` — PASS
- `node remote-broker/server.mjs` + `curl http://127.0.0.1:8080/healthz` — PASS（返回 `{"ok":true,"devices":0,"sessions":0}`）
- `curl http://127.0.0.1:8080/api/state` — PASS（按预期返回空设备/控制端/会话状态）

## 发现的问题

| # | 描述 | 严重级别 | 状态 |
|---|------|----------|------|
| 1 | 执行环境缺少 Java 运行时，导致 Android 构建和测试整体被阻塞 | 高 | Open |
| 2 | 非 Root 兼容路径目前只有脚手架，完整无人值守能力仍依赖 Root 被控设备 | 中 | 已知限制 |

## 变更历史

| 日期 | 变更 |
|------|------|
| 2026-03-16 | 初始版本 |
