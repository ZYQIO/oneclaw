# 远程控制基础版说明

本文档说明当前已接入 OneClaw 工作区的远程控制基础能力。

## 组成部分

- `:remote-core`
  - 共享 Android 库，包含远控设备模型、Broker 协议、Root 截图/输入后端和文件传输后端。
- `:remote-host`
  - 部署在被控手机上的独立 Android 被控端应用。
- `remote-broker/`
  - 轻量 Node.js WebSocket Broker，同时负责静态文件服务。
- `remote-console-web/`
  - 由 Broker 提供的浏览器控制台。
- `:app`
  - OneClaw 集成层：远控仓库、页面、导航入口和远控工具组。

## 当前能力等级

### Root 主路径

已实现：
- 设备注册
- 短码配对
- 会话开启/关闭
- 使用 `screencap -p` 做周期性截图
- 使用 `input` 注入输入
- 使用 `monkey` 启动应用
- 在 host app 沙箱内做文件列表/上传/下载
- 通过 OneClaw 工具访问远控能力

### 非 Root 兼容路径

当前只有脚手架：
- 无障碍服务
- MediaProjection 采集后端接口
- Host 端 UI 提示

尚未端到端实现：
- MediaProjection 授权流程
- 基于无障碍的手势注入
- 稳定的兼容模式无人值守能力

## 启动顺序

### 1. 启动 Broker

```bash
cd remote-broker
npm install
npm start
```

Broker 提供的端点：
- WebSocket: `ws://<host>:8080/ws`
- 浏览器控制台: `http://<host>:8080/`
- 健康检查: `http://<host>:8080/healthz`
- 运行状态: `http://<host>:8080/api/state`

### 2. 安装并启动 `remote-host`

在 Java 和 Android SDK 可用后执行：

```bash
./gradlew :remote-host:installDebug
```

进入 App 后：
- 填写 Broker URL
- 记录或刷新 Pair Code
- 启动前台服务
- 如果是 Root 设备，确认状态卡里显示 `mode=root`

### 3. 打开浏览器控制台

访问：

```text
http://<broker-host>:8080/
```

然后执行：
- 建立 WebSocket 连接
- 刷新设备列表
- 选择设备
- 用设备上的配对码完成配对
- 打开会话
- 请求截图 / 在截图上点击 / 发送文本 / 上传或下载文件

### 4. 在 OneClaw 中使用

在 OneClaw 内：
- 通过 `Settings -> Remote Control` 做人工控制
- 通过加载 `remote` 工具组让 AI 代理调用远控能力

## 当前限制

- 完整无人值守能力目前仍依赖被控手机具备 Root。
- 远程画面仍是截图轮询，还不是 H.264 视频流。
- 文件传输当前仅限于 host app 沙箱共享目录。
- Broker 中的配对关系当前只保存在内存中，Broker 重启后会丢失。
- 当前执行环境未安装 Java，因此这里无法实际跑 Android 构建和测试。
