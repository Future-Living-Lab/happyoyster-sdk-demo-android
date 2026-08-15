# Happy Oyster Android SDK Demo

中文 | [English](README.md)

本工程是 Happy Oyster Android SDK 的端到端示例 App，演示宿主 App 如何组合：

- 三方服务端网关 `/server-api/*`
- Maven 依赖：`cn.happyoyster:opensdk:0.1.10`（Maven Central）
- 世界创建、游玩、历史、产物与运行中控制

`/server-api/*` 是 Demo 三方服务端接口，不是 Android SDK 公开 API。

## 环境要求

- Android Studio（随 IDE 提供的 JDK 17 或兼容 JDK）
- 本机已安装 Android SDK；`compileSdk 36`、`targetSdk 36`、`minSdk 24`
- 本目录自带 Gradle wrapper：Gradle `8.13`、Android Gradle Plugin `8.13.2`、Kotlin `2.0.21`
- SDK 与 AndroidX 依赖从 Maven Central 解析；实时通信引擎（ARTC）从阿里云 Maven 解析
- Android 设备或模拟器能访问三方服务端 Gateway
- 配套服务端 Demo（Node 或 Python），或以任意服务端语言实现兼容 `/server-api/*` 协议的三方服务端

## 运行

1. 先启动配套服务端 Demo（参见 `happyoyster-sdk-demo-node` 或 `happyoyster-sdk-demo-python`），或启动以任意语言实现、提供兼容 `/server-api/*` 接口的自有三方服务端。
2. 使用 Android Studio 打开本目录，等待 Gradle sync 完成。
3. 连接设备或启动模拟器，选择 `app` 运行配置并点击 Run。
4. 首次启动后进入「设置」页，填写 `Gateway Base URL` 和 `SDK API Host`。

## 配置地址

Android App 不内置三方服务端，无法独立跑通完整流程。App 需要两类地址，默认均为空；运行前先启动配套服务端 Demo 或兼容的三方服务端，再在「设置」页填写：

- `Gateway Base URL`：服务端 Demo 或三方服务端地址。它需提供 `/server-api/*`，用于签发 temporary API Key（token）、换取一次性 ticket，以及提供本 Demo 使用的 World、历史、产物和运行中更新等服务端接口。例如：

```text
http(s)://<gateway-host>/server-api
```

- `SDK API Host`：传给 `HappyOyster.initialize(SDKConfig(apiHost = ...))` 的百炼 API Host，填写百炼控制台 API Key 页展示的裸 host，例如：

```text
llm-xxxxxxxx.cn-beijing.maas.aliyuncs.com
```

完整 OpenAPI path 由 SDK 内部补全；设置页仅接受裸 host，不支持带 scheme 或 path 的完整 URL。

注意：`SDK API Host` 必须与 App 通过 `/server-api/temp-api-key` 获取到的 API Key **同账号、同地域**，不匹配时 SDK 请求会被网关拒绝并返回 `AccessDenied`。

配套 Node、Python Demo 仅用于本地联调和实现参考。生产环境可选择任意服务端语言实现 `/server-api/*`，并补充身份认证、权限校验和限流；不要直接把任一 Demo 服务作为生产三方服务端暴露或部署。

如果网关部署在局域网机器上，真机调试时填写该机器的局域网 IP，例如：

```text
http://192.168.1.23:3000/server-api
```

## 命令行构建

```bash
./gradlew assembleDebug
adb install -r build/outputs/apk/debug/happyoyster-sdk-demo-android-debug.apk
```

命令行构建可通过 `ANDROID_HOME` 环境变量或本地 `local.properties` 提供 `sdk.dir`。

## App 流程

1. 打开「设置」，填写 `Gateway Base URL` 和 `SDK API Host`。
2. App 通过 `/server-api/temp-api-key` 获取 temporary API Key（token），并通过 `/server-api/travel-credential` 获取一次性 ticket；启动 Travel 时两者都需要。ticket 有效期为 30 分钟且只能使用一次；刷新 token 无需重新换取 ticket。
3. 在「创建」页创建世界（图片输入可选），或在「游玩」页下拉刷新已有世界。本 Demo 支持 HTTP(S) URL、raw base64 或 data URI；Open API 支持 JPG/JPEG、PNG 和 WebP 图片。
4. 创建 UI 使用 `Wander` 与 `Story`，SDK 运行态使用 `Adventure` 与 `Directing`：`Wander` 对应 `Adventure`（`mode=1`），`Story` 对应 `Directing`（`mode=2`）。Story 创建支持 `简单（Prompt）` 与 `剧本（ScriptList）`；ScriptList 创建必须包含恰好 45 个 `acts`，且 `turn` 按 1–45 连续。
5. 在 ready 世界卡片上点击「开始 Travel」。
6. Adventure 模式支持点按、长按和组合操作的移动、镜头及交互控制。Directing 模式提供暂停、恢复、回溯（秒数取 4 的整数倍）、Instruct 或 ScriptList 更新控制。
7. 在「历史」页查看已完成 Travel 和产物。

临时 API Key 仅为 Demo 便利保存在 App 私有存储中，为空或即将过期时会在开始 Travel 前自动刷新。

对于运行中的 ScriptList Travel，App 请求三方服务端的 `POST /server-api/travels/update-script`，由三方服务端在服务端完成更新。这不是 Android SDK API，客户端也不能直接调用上游 OpenAPI；每次 update 必须提交完整 ScriptList，且必须包含恰好 45 个 `acts`。

## 本地 HTTP

为了兼容 `http://` 调试网关，本 Demo 允许明文 HTTP。生产 App 不要照搬该配置。

## 代码边界

- `gateway/`：演示三方服务端 `/server-api/*` 调用。
- `sdk/`：只薄封装 `HappyOyster` 公开入口。
- SDK 通过 Maven Central 依赖 `cn.happyoyster:opensdk:0.1.10`。
- Android App 不包含三方服务端；生产 App 必须接入以合适服务端语言实现、且具备安全防护的自有三方服务端。
