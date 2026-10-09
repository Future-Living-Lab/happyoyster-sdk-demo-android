# Happy Oyster Android SDK Demo

[中文](README.zh-CN.md) | English

`happyoyster-sdk-demo-android` is an end-to-end Android sample for the Happy Oyster product flow. It demonstrates how a host app combines:

- A third-party server gateway exposing `/server-api/*`
- The Maven Central dependency `cn.happyoyster:opensdk:0.2.3`
- World creation, play, history, artifacts, and in-travel controls

The SDK resolves from Maven Central; the Alibaba RTC dependency resolves from the Alibaba Cloud Maven repository configured in `settings.gradle.kts`.

`/server-api/*` endpoints belong to the demo's third-party server; they are not part of the Android SDK public API.

## Requirements

- Android Studio, using its bundled JDK 17 or a compatible JDK, can open this directory directly
- A local Android SDK installation; this project uses `compileSdk 36`, `targetSdk 36`, and `minSdk 24`
- Gradle wrapper included: Gradle `8.13`, Android Gradle Plugin `8.13.2`, Kotlin `2.0.21`
- An Android device or emulator that can reach your third-party server gateway
- A companion server demo (Node or Python), or any third-party server that implements the compatible `/server-api/*` contract

## Run in Android Studio

1. Start a companion server demo first (Node or Python), or start your own third-party server in any language that exposes compatible `/server-api/*` endpoints.
2. Open the `happyoyster-sdk-demo-android/` directory in Android Studio; you do not need to open the repository root.
3. Wait for Gradle sync to finish. This directory includes `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, and the Gradle wrapper.
4. Select the `happyoyster-sdk-demo-android` run configuration, connect a device or start an emulator, and click Run.
5. On first launch, open Settings and fill in `Gateway Base URL`, `SDK API Host`, and make sure the models used by the demo are enabled for your account.

## Configuration

The Android app does not embed a third-party server and cannot run the complete flow by itself. Start a companion server demo or a compatible third-party server before running the app, then configure the gateway and API Host. The demo selects its built-in SDK models by world mode:

- `Gateway Base URL`: the server demo or your third-party server address. It must expose `/server-api/*` for issuing temporary API Keys (tokens), exchanging one-time tickets, and providing the server-side World, history, artifact, and in-travel update endpoints used by this demo.
  The gateway must route by world mode (`1` Adventure, `2` Directing, `3` Acting): world creation sends it in the body, world lists query each mode, and all other World/Travel requests carry `X-Happy-Oyster-Mode`. Temporary API Key requests need no mode. Keep the gateway region and SDK API Host in the same environment.
- `SDK API Host`: the bare Bailian API Host shown on the console API Key page.
- `SDK models`: the demo maps Adventure, Directing, and Acting to `happyoyster-1.0-adventure`, `happyoyster-1.0-directing`, and `happyoyster-1.0-acting` internally, then passes the matching name to `SDKConfig(model = ...)`. No model input is needed in Settings. The models must be enabled for the account and region of the API Host and temporary API Key. The SDK builds `/api/v2/apps/{model}/openapi/v1` from the selected name.

Example `Gateway Base URL`:

```text
http(s)://<gateway-host>/server-api
```

Example `SDK API Host`:

```text
llm-xxxxxxxx.cn-beijing.maas.aliyuncs.com
```

Both addresses are empty by default; the three public model names are fixed in the demo. The SDK selects the model for the world mode before starting a Travel, and reinitializes only while no Travel is active. A wrong model or account pairing can produce `AccessDenied`.

The companion Node and Python demos are for local integration and reference only. For production, implement `/server-api/*` in your preferred server-side language and add authentication, authorization, and rate limiting. Do not expose either demo service directly as a production third-party server.

If the gateway runs on a machine in your LAN, use that machine's LAN IP when testing on a physical device, for example:

```text
http://LAN_IP:3000/server-api
```

`Gateway Base URL` must be a valid `http://` or `https://` URL. `SDK API Host` accepts only a bare host, not a full URL with a scheme or path. If `Gateway Base URL` is invalid, the app does not call `/server-api/*`. SDK requests fail if `SDK API Host` is invalid or does not match the token's environment.

## Build and install

```bash
cd happyoyster-sdk-demo-android
./gradlew assembleDebug
adb install -r build/outputs/apk/debug/happyoyster-sdk-demo-android-debug.apk
```

Standalone builds still require a local Android SDK installation. Android Studio maintains the local `local.properties` file; for command-line builds, provide `sdk.dir` through the `ANDROID_HOME` environment variable or a local `local.properties` file.

## App flow

1. Open Settings, enter `Gateway Base URL` and `SDK API Host`.
2. The app obtains a temporary API Key (token) via `/server-api/temp-api-key` and a one-time ticket via `/server-api/travel-credential`; both are required to start a Travel. A ticket is valid for 30 minutes and can be used only once. Refreshing the token does not require exchanging for a new ticket.
3. Create a world on the Create tab, or pull to refresh existing worlds on the Play tab. Wander and Acting creation support only `first_frame`, and both require a first-frame image. Both Wander and Acting require a prompt. The demo offers 480p and 720p for Wander; availability depends on the connected service. Story Simple accepts up to six reference images via URL, file selection, or Base64; ScriptList retains its separate first-frame input. Acting images must match the selected `9:16` or `16:9` canvas, and the companion Gateway must support `mode=3`. The demo accepts an HTTP(S) URL, raw base64, or a data URI. You can select a local image directly or use the dedicated clipboard button to bypass IME truncation of long Base64 text. The Open API supports JPG/JPEG, PNG, and WebP images; use file selection rather than the clipboard for very large images.
4. The creation UI offers `Wander` (`mode=1`, SDK Adventure), `Story` (`mode=2`, SDK Directing), and `Acting` (`mode=3`). Story creation supports `Simple (Prompt)` and `ScriptList`; ScriptList creation requires exactly 45 `acts` with continuous turns 1–45. Bundled script presets fill in a first-frame image URL, which you can replace.
5. Creation shows a busy state and prevents duplicate submission. Build status is queried independently of cover availability every four seconds until ready/failed, for up to 30 queries; pull to refresh to check again after that window. Tap "Start Travel" on a ready world card. A failed build card shows the stable server error code with public-safe UI copy.
6. Adventure supports tap, hold, and combined movement, camera, and interaction controls. Directing supports Instruct and, for `storyV2`, pause, resume, and rewind (multiples of 4 seconds). Acting supports Instruct and, for `actingV2`, pause and resume; it has no rewind or Adventure commands. The player sizes from the `startTravel` result's `aspectRatio` before attaching video.
7. Review Travel status on the History tab. Failed records show a reason; completed records can play or download their artifact. Failed video loads show a retry button.

Both Play and History offer All / Wander / Story / Acting filters and Newest first / Oldest first sorting. Each tab keeps its own selection while navigating or refreshing. Sorting uses world creation time or travel start time (falling back to end time when unavailable); records without a valid time appear last. These controls apply to the loaded records (up to 100 per mode).

SDK failures with a structured `raw` payload are mapped to localized safe messages using `errorCode`; raw diagnostic messages are not displayed. Pause, resume, and rewind remain pending until the matching paused/running SDK callback. A request failure leaves the result uncertain; retry the same operation or end the experience.

The temporary API Key is stored in the app's private storage solely for demo convenience. The app refreshes it automatically before starting a Travel when it is empty or about to expire.

For a running ScriptList Travel, the app requests `POST /server-api/travels/update-script` on the third-party server, which performs the update server-side. This is not an Android SDK API, and the client must not call the upstream OpenAPI directly. Every update must contain the complete ScriptList with exactly 45 `acts`.

## Cleartext HTTP

To support `http://` debug gateways, this demo allows cleartext HTTP. Do not copy this configuration directly into a production app.

## Boundaries

- `gateway/` demonstrates third-party server `/server-api/*` calls.
- `sdk/` is a thin wrapper over the public `HappyOyster` entry points only.
- `happyoyster-sdk-demo-android` consumes `cn.happyoyster:opensdk:0.2.3` from Maven Central.
- The Android app does not contain a third-party server service. Production apps must use their own secured third-party server, implemented in any suitable server-side language.
