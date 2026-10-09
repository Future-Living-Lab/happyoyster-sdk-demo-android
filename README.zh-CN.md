# Happy Oyster Android SDK Demo

中文 | [English](README.md)

`happyoyster-sdk-demo-android` 是 Happy Oyster 产品流程的端到端 Android 示例工程，用来演示宿主 App 如何组合：

- 三方服务端网关 `/server-api/*`
- Maven Central 依赖：`cn.happyoyster:opensdk:0.2.3`
- 世界创建、游玩、历史、产物与运行中控制

SDK 依赖从 Maven Central 解析；Alibaba RTC 依赖从 `settings.gradle.kts` 配置的阿里云 Maven 仓库解析。

`/server-api/*` 是 Demo 三方服务端接口，不是 Android SDK 公开 API。

## 环境要求

- Android Studio 可直接打开本目录，使用随 IDE 提供的 JDK 17 或兼容 JDK
- 本机已安装 Android SDK；本工程使用 `compileSdk 36`、`targetSdk 36` 和 `minSdk 24`
- 本目录自带 Gradle wrapper：Gradle `8.13`、Android Gradle Plugin `8.13.2`、Kotlin `2.0.21`
- Android 设备或模拟器能访问三方服务端 Gateway
- 配套服务端 Demo（Node 或 Python），或以任意服务端语言实现兼容 `/server-api/*` 协议的三方服务端

## Android Studio 运行

1. 先启动配套服务端 Demo（Node 或 Python），或启动以任意语言实现、提供兼容 `/server-api/*` 接口的自有三方服务端。
2. 使用 Android Studio 打开 `happyoyster-sdk-demo-android/` 目录，不需要打开仓库根目录。
3. 等待 Gradle sync 完成；本目录已包含 `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties` 和 Gradle wrapper。
4. 选择 `happyoyster-sdk-demo-android` 运行配置，连接设备或启动模拟器，然后点击 Run。
5. 首次启动后进入「设置」页，填写 `Gateway Base URL`、`SDK API Host`，并确认账号已开通对应模式的模型。

## 配置地址

Android App 不内置三方服务端，无法独立跑通完整流程。运行前先启动配套服务端 Demo 或兼容的三方服务端，再配置网关和 API Host。Demo 会按世界模式使用内置的 SDK 模型：

- `Gateway Base URL`：服务端 Demo 或三方服务端地址。它需提供 `/server-api/*`，用于签发 temporary API Key（token）、换取一次性 ticket，以及提供本 Demo 使用的 World、历史、产物和运行中更新等服务端接口。
  网关须按世界模式分流（`1` Adventure、`2` Directing、`3` Acting）：创建请求在 body 中传 mode，世界列表分别按 mode 查询，其他 World/Travel 请求携带 `X-Happy-Oyster-Mode`。临时 API Key 请求不需要 mode。网关地域与 SDK API Host 应属于同一环境。
- `SDK API Host`：百炼控制台 API Key 页展示的裸 host。
- `SDK 模型`：Demo 内置 Adventure、Directing、Acting 对应的 `happyoyster-1.0-adventure`、`happyoyster-1.0-directing`、`happyoyster-1.0-acting`，按世界模式传给 `SDKConfig(model = ...)`，无需在设置页填写。账号须在 API Host、临时 API Key 对应地域开通所用模型；SDK 按选定模型组合 `/api/v2/apps/{model}/openapi/v1`。

`Gateway Base URL` 示例：

```text
http(s)://<gateway-host>/server-api
```

`SDK API Host` 示例：

```text
llm-xxxxxxxx.cn-beijing.maas.aliyuncs.com
```

两个地址默认均为空；三个公开模型名在 Demo 内固定映射。开始 Travel 前，App 按世界模式选择模型，只在没有运行中的 Travel 时重新初始化 SDK。模型或账号不匹配可能返回 `AccessDenied`。

配套 Node、Python Demo 仅用于本地联调和实现参考。生产环境可选择任意服务端语言实现 `/server-api/*`，并补充身份认证、权限校验和限流；不要直接把任一 Demo 服务作为生产三方服务端。

如果网关部署在局域网机器上，真机调试时填写该机器的局域网 IP，例如：

```text
http://LAN_IP:3000/server-api
```

`Gateway Base URL` 必须是合法 `http://` 或 `https://` 地址。`SDK API Host` 只接受裸 host，不支持带 scheme 或 path 的完整 URL。`Gateway Base URL` 非法时，App 不会请求 `/server-api/*`；`SDK API Host` 非法或与 token 所属环境不匹配时，SDK 请求会失败。

## 构建与安装

```bash
cd happyoyster-sdk-demo-android
./gradlew assembleDebug
adb install -r build/outputs/apk/debug/happyoyster-sdk-demo-android-debug.apk
```

独立构建时仍需本机已安装 Android SDK。Android Studio 会维护本地 `local.properties`；命令行构建可通过 `ANDROID_HOME` 环境变量或本地 `local.properties` 提供 `sdk.dir`。

## App 流程

1. 打开「设置」，填写 `Gateway Base URL`、`SDK API Host`。
2. App 通过 `/server-api/temp-api-key` 获取 temporary API Key（token），并通过 `/server-api/travel-credential` 获取一次性 ticket；启动 Travel 时两者都需要。ticket 有效期为 30 分钟且只能使用一次；刷新 token 无需重新换取 ticket。
3. 在「创建」页创建世界，或在「游玩」页下拉刷新已有世界。Wander 和 Acting 创建均仅支持 `first_frame`，必须提供首帧图片；Wander 和 Acting 均必须提供 Prompt。Demo 为 Wander 提供 480p 和 720p 选项，实际可用范围取决于所连接的服务。Story 简单模式支持最多 6 张参考图（URL、文件或 Base64），ScriptList 保持独立的首帧图片输入。Acting 首帧图片须与所选 `9:16` 或 `16:9` 画幅匹配，配套 Gateway 须支持 `mode=3`。本 Demo 支持 HTTP(S) URL、raw base64 或 data URI；可直接选择本地图片，或用专用按钮从剪贴板读取 Base64。Open API 支持 JPG/JPEG、PNG 和 WebP；超大图片宜使用文件选择。
4. 创建 UI 提供 `Wander`（`mode=1`，SDK Adventure）、`Story`（`mode=2`，SDK Directing）和 `Acting`（`mode=3`）。Story 创建支持 `简单（Prompt）` 与 `剧本（ScriptList）`；ScriptList 创建必须包含恰好 45 个 `acts`，且 `turn` 按 1–45 连续。内置剧本预设会自动带入首帧图片 URL，也可自行替换。
5. 创建请求期间显示创建中并防止重复提交。构建状态每 4 秒独立查询，直到 ready/failed，最多查询 30 次；已有封面仍会跟踪构建，超出等待窗口可下拉刷新继续查询。在 ready 世界卡片上点击「开始 Travel」。世界构建失败时，卡片会显示服务端返回的稳定错误码及对应的安全提示文案。
6. Adventure 支持点按、长按和组合操作的移动、镜头及交互控制。Directing 支持 Instruct，`storyV2` 还支持暂停、恢复、回溯（秒数取 4 的整数倍）。Acting 支持 Instruct，`actingV2` 还支持暂停、恢复；不支持回溯或 Adventure 控制。播放器在挂载视频前按 `startTravel` 返回的 `aspectRatio` 布局。
7. 在「历史」页查看 Travel 状态：失败记录显示失败原因，完成记录可播放或下载产物，视频加载失败时可点击重试。

「游玩」和「历史」均支持按全部、漫游、故事、表演筛选，以及最新优先、最早优先排序。两个页面各自保留选择，切换页面或刷新不重置。世界按创建时间、体验记录按开始时间（缺失时使用结束时间）排序，无有效时间的记录置于末尾。筛选和排序作用于已加载记录（每种类型最多 100 条）。

SDK 通过结构化 `raw` 返回失败原因时，Demo 按 `errorCode` 映射安全的中英文提示，不直接展示原始诊断文案。暂停、恢复和回溯等待期间禁用重复操作，收到对应的 paused/running 回调后才结束等待；请求失败后保留待确认状态，可重试同一操作或结束体验。

临时 API Key 仅为了 Demo 便利保存在 App 私有存储中，为空或即将过期时会在开始 Travel 前自动刷新。

对于运行中的 ScriptList Travel，App 请求三方服务端的 `POST /server-api/travels/update-script`，由三方服务端在服务端完成更新。这不是 Android SDK API，客户端也不能直接调用上游 OpenAPI；每次 update 必须提交完整 ScriptList，且必须包含恰好 45 个 `acts`。

## 本地 HTTP

为了兼容 `http://` 调试网关，本 Demo 允许明文 HTTP。生产 App 不要直接照搬该配置。

## 边界

- `gateway/` 演示三方服务端 `/server-api/*` 调用。
- `sdk/` 只薄封装 `HappyOyster` 公开入口。
- `happyoyster-sdk-demo-android` 从 Maven Central 引入 `cn.happyoyster:opensdk:0.2.3`。
- Android App 不包含三方服务端；生产 App 必须接入以合适服务端语言实现、且具备安全防护的自有三方服务端。
