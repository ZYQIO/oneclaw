# Phase 0 基线报告

## 摘要

这份报告记录了 2026-03-18 在当前交接机器上对 OneClaw 做的执行基线检查结果。

当前结论：

- 仓库结构已经是较新的多模块 Android 架构，且与当前代码状态一致。
- 基于 Node.js 的 `remote-broker` 可以成功启动并正确响应。
- 当前机器没有安装 Java、Android SDK 和 `adb`，因此 Android 构建验证暂时被阻塞。

这是一份基线报告，不是功能测试报告。

## 仓库快照

- 提交：`ff1f861`
- 根 Gradle 模块：
  - `:app`
  - `:bridge`
  - `:remote-core`
  - `:remote-host`
- Gradle 模块之外的运行组件：
  - `remote-broker`
  - `remote-console-web`

## 代码层面可确认的环境要求

从当前 Android 构建文件可以确认：

- 需要 Java 17
- Android Gradle Plugin 为 `8.7.3`
- Kotlin 为 `2.0.21`
- `compileSdk = 35`
- `targetSdk = 35`
- `minSdk = 26`

主 App 和 `remote-host` 都要求 Java 17。

## 当前机器环境快照

本机实际探测结果：

- `node`: `v22.22.0`
- `npm`: `10.9.4`
- `java`：未安装或不在 `PATH` 中
- `adb`：未安装或不在 `PATH` 中
- `JAVA_HOME`：未设置
- `ANDROID_HOME`：未设置
- `ANDROID_SDK_ROOT`：未设置
- `local.properties`：不存在

在这次基线检查中，标准路径下没有发现本地 Android Studio 安装目录，也没有发现 Android SDK 目录。

## 已执行检查

### 1. 仓库结构检查

状态：PASS

已确认当前根工程包含：

- `:app`
- `:bridge`
- `:remote-core`
- `:remote-host`

这说明仓库已经不是早期的双模块状态，而是包含远程控制能力的较新架构。

### 2. Java 可用性检查

状态：FAIL

命令：

```powershell
java -version
```

结果：

- 找不到 `java` 命令

影响：

- 目前无法执行任何基于 Gradle 的 Android 校验。

### 3. Gradle 启动检查

状态：FAIL

命令：

```powershell
.\gradlew.bat -q help
```

结果：

- Gradle Wrapper 立即退出，原因是 `JAVA_HOME` 未设置且系统中没有可用的 `java`

影响：

- `assembleDebug`
- `test`
- `connectedAndroidTest`
- Roborazzi 相关任务

这些都无法在当前环境执行。

### 4. Android 工具链检查

状态：FAIL

命令：

```powershell
adb version
```

结果：

- 找不到 `adb` 命令

影响：

- 无法做模拟器或真机验证
- 无法执行 Layer 1B instrumented tests
- 无法执行 Layer 2 adb 可视化验证

### 5. Node.js 工具链检查

状态：PASS

命令：

```powershell
node -v
npm -v
```

结果：

- Node.js 可用
- npm 可用

### 6. `remote-broker` 启动检查

状态：PASS

方式：

- 通过自测脚本启动 `remote-broker/server.mjs`
- 访问 broker 的健康检查和状态接口

实际结果：

- `/healthz` 返回 HTTP `200`
- `/api/state` 返回 HTTP `200`
- broker 启动日志显示：

```text
Remote broker listening on http://0.0.0.0:8080
```

响应快照：

```json
{"ok":true,"devices":0,"sessions":0}
```

这说明在不额外安装依赖的前提下，当前仓库里的 Node broker 已经可以在本机启动。

### 7. `remote-console-web` 静态页面检查

状态：PASS

方式：

- 通过运行中的 broker 请求 `/`

实际结果：

- 返回 HTTP `200`
- HTML 响应里包含 `OneClaw Remote Console` 页面标题

这说明 broker 可以正常把仓库中的浏览器控制台入口页提供出来。

## 当前阻塞项

### 阻塞项 1：缺少 Java 17 运行时

没有 Java 17，就无法运行任何 Android Gradle 任务。

### 阻塞项 2：缺少 Android SDK / platform-tools

没有 Android SDK 和 `adb`，就无法在本机完成 Android App 的构建、安装和测试。

### 阻塞项 3：没有本地 SDK 配置

当前没有 `local.properties`，也没有配置 SDK 相关环境变量。

## 今天已经验证的内容

已验证：

- 仓库布局
- 远程控制相关模块布局
- Node 运行时可用
- `remote-broker` 可以启动
- broker 健康检查接口可用
- broker 状态接口可用
- broker 能静态托管 `remote-console-web`

尚未验证：

- `:app` 构建
- `:remote-host` 构建
- 单元测试
- instrumented tests
- 截图测试
- 模拟器流程
- rooted 真机远控链路

## 建议的下一步

1. 安装 Java 17 并设置 `JAVA_HOME`。
2. 安装 API 35 所需的 Android SDK 组件和 platform-tools。
3. 创建 `local.properties` 或配置 `ANDROID_SDK_ROOT`。
4. 先重新执行可复用的环境检查脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-env.ps1
```

5. 然后重新执行：

```powershell
.\gradlew.bat -q help
.\gradlew.bat assembleDebug
.\gradlew.bat :remote-host:assembleDebug
.\gradlew.bat test
```

6. 在构建恢复后，再进入模拟器或真机上的远程控制链路验证。

## 当前阶段状态

Phase 0 目前属于部分完成。

已完成：

- 基线环境探测
- broker 启动验证
- 阻塞项识别

仍待完成：

- Android 构建恢复
- Android 测试执行
- 模拟器或真机安装验证
