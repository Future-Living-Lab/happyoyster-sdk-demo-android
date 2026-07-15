# Happy Oyster Android SDK Demo

[中文](README.zh-CN.md) | English

This project is an end-to-end sample app for the Happy Oyster Android SDK. It shows how a host app combines:

- A third-party server gateway exposing `/server-api/*`
- The Maven dependency `cn.happyoyster:opensdk:0.1.3` (Maven Central)
- World creation, play, history, artifacts, and in-travel controls

`/server-api/*` endpoints belong to the demo's third-party server; they are not part of the Android SDK public API.

## Requirements

- Android Studio (with its bundled JDK 17 or a compatible JDK)
- A local Android SDK installation; `compileSdk 36`, `targetSdk 36`, `minSdk 24`
- Gradle wrapper included: Gradle `8.13`, Android Gradle Plugin `8.13.2`, Kotlin `2.0.21`
- SDK and AndroidX dependencies resolve from Maven Central; the realtime engine (ARTC) resolves from Alibaba Cloud Maven
- An Android device or emulator that can reach your third-party server gateway
- A companion server demo (Node or Python), or any third-party server that implements the compatible `/server-api/*` contract

## Run

1. Start a companion server demo first (see `happyoyster-sdk-demo-node` or `happyoyster-sdk-demo-python`), or start your own third-party server in any language that exposes compatible `/server-api/*` endpoints.
2. Open this directory in Android Studio and wait for Gradle sync to finish.
3. Connect a device or start an emulator, select the `app` run configuration, and click Run.
4. On first launch, open the Settings tab and fill in `Gateway Base URL` and `SDK API Host`.

## Configuration

The Android app does not embed a third-party server and cannot run the complete flow by itself. It needs two addresses, both of which default to empty. Start a companion server demo or a compatible third-party server before running the app, then fill in these values on the Settings tab:

- `Gateway Base URL`: the server demo or your third-party server address. It must expose `/server-api/*` for issuing temporary API Keys (tokens), exchanging one-time tickets, and providing the server-side World, history, artifact, and in-travel update endpoints used by this Demo. For example:

```text
http(s)://<gateway-host>/server-api
```

- `SDK API Host`: the Bailian API Host passed to `HappyOyster.initialize(SDKConfig(apiHost = ...))`. Enter the bare host shown on the Bailian console API Key page, for example:

```text
llm-xxxxxxxx.cn-beijing.maas.aliyuncs.com
```

The SDK appends the full OpenAPI path internally. The Settings page only accepts the bare host and rejects full URLs with a scheme or path.

Note: the `SDK API Host` must belong to the **same account and region** as the API key the app fetches via `/server-api/temp-api-key`; a mismatch is rejected by the gateway with `AccessDenied`.

The companion Node and Python demos are for local integration and reference only. For production, implement `/server-api/*` in your preferred server-side language and add authentication, authorization, and rate limiting. Do not expose either demo service directly as a production third-party server.

If the gateway runs on a machine in your LAN, use that machine's LAN IP when testing on a physical device, for example:

```text
http://192.168.1.23:3000/server-api
```

## Command-line build

```bash
./gradlew assembleDebug
adb install -r build/outputs/apk/debug/happyoyster-sdk-demo-android-debug.apk
```

For command-line builds, provide `sdk.dir` via the `ANDROID_HOME` environment variable or a local `local.properties` file.

## App flow

1. Open Settings and fill in `Gateway Base URL` and `SDK API Host`.
2. The app obtains a temporary API Key (token) via `/server-api/temp-api-key` and a one-time ticket via `/server-api/travel-credential`; both are required to start a Travel. A ticket is valid for 30 minutes and can be used only once. Refreshing the token does not require exchanging for a new ticket.
3. Create a world on the Create tab (image input is optional), or pull to refresh existing worlds on the Play tab. The Demo accepts an HTTP(S) URL, raw base64, or a data URI; the Open API supports JPG/JPEG, PNG, and WebP images.
4. The creation UI uses `Wander` and `Story`, while the SDK runtime uses `Adventure` and `Directing`: `Wander` maps to `Adventure` (`mode=1`), and `Story` maps to `Directing` (`mode=2`). Story creation supports `Simple (prompt)` and `ScriptList`; ScriptList creation requires exactly 45 `acts` with continuous turns 1–45.
5. Tap "Start Travel" on a ready world card.
6. Adventure mode supports tap, hold, and combined movement, camera, and interaction controls. Directing mode offers pause, resume, rewind (multiples of 4 seconds), Instruct, or ScriptList update controls.
7. Review completed travels and artifacts on the History tab.

The temporary API Key is stored in the app's private storage purely for demo convenience. It is refreshed automatically before starting a Travel when empty or about to expire.

For a running ScriptList Travel, the app requests `POST /server-api/travels/update-script` on the third-party server, which performs the update server-side. This is not an Android SDK API, and the client must not call the upstream OpenAPI directly. Every update must contain the complete ScriptList with exactly 45 `acts`.

## Cleartext HTTP

To support `http://` debug gateways, this demo allows cleartext HTTP. Do not copy this configuration into a production app.

## Code boundaries

- `gateway/`: demonstrates third-party server `/server-api/*` calls.
- `sdk/`: a thin wrapper over the public `HappyOyster` entry points only.
- The SDK is consumed from Maven Central as `cn.happyoyster:opensdk:0.1.3`.
- The Android app does not contain a third-party server service. Production apps must use their own secured third-party server, implemented in any suitable server-side language.
