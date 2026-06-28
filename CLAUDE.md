# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
./gradlew assembleDebug          # Debug APK (auto-runs cargo ndk for Rust)
./gradlew assembleRelease        # Release APK with R8
./gradlew assembleFastRelease    # No R8, debug signing — for Compose perf profiling
```

Rust toolchain + `cargo-ndk` required. Builds `arm64-v8a` only. No tests exist.

## Entry Points

- [MainActivity.kt](app/src/main/java/cp/player/MainActivity.kt) — Single Activity, hosts all Compose Navigation routes (`setup`, `main`, `search`, `library`, `playlist/{id}`, `settings`, `downloads`, `livesort`, etc.)
- [CPApplication.kt](app/src/main/java/cp/player/CPApplication.kt) — Application init, loads provider modules
- [MusicService.kt](app/src/main/java/cp/player/service/MusicService.kt) — `MediaSessionService`, foreground playback, dual-player crossfade

## Audio Engine

- [CPPlayerManager.kt](app/src/main/java/cp/player/engine/CPPlayerManager.kt) — Engine selector: ExoPlayer (0) vs FlickPlayer (1)
- [FlickPlayer.kt](app/src/main/java/cp/player/engine/FlickPlayer.kt) — Media3 `SimpleBasePlayer` wrapping Rust JNI engine
- [RustEngine.kt](app/src/main/java/cp/player/engine/RustEngine.kt) — JNI bridge to native Rust
- [CrossfadeManager.kt](app/src/main/java/cp/player/engine/CrossfadeManager.kt) — Crossfade between two player instances
- [UsbAudioManager.kt](app/src/main/java/cp/player/engine/UsbAudioManager.kt) — USB DAC exclusive mode via UAC 2.0
- [StreamProxy.kt](app/src/main/java/cp/player/engine/StreamProxy.kt) — Progressive streaming proxy for remote audio
- [BackendDataSource.kt](app/src/main/java/cp/player/service/BackendDataSource.kt) — Multi-provider failover DataSource for ExoPlayer

## Provider Plugin System

Modules are `.zip` files with `manifest.json`. Three transport types: `jni`, `binary`, `http`.

- [BackendProvider.kt](app/src/main/java/cp/player/provider/BackendProvider.kt) — Provider interface
- [ProviderManager.kt](app/src/main/java/cp/player/provider/ProviderManager.kt) — `callApi()` routing, apiMap remapping, active provider management
- [ModuleManager.kt](app/src/main/java/cp/player/provider/ModuleManager.kt) — Load/unload modules from zip
- [JniProvider.kt](app/src/main/java/cp/player/provider/JniProvider.kt) — JNI shared library provider
- [BinaryProvider.kt](app/src/main/java/cp/player/provider/BinaryProvider.kt) — Subprocess binary provider
- [HttpProvider.kt](app/src/main/java/cp/player/provider/HttpProvider.kt) — HTTP REST provider

See [PROVIDER_DEV_GUIDE.md](PROVIDER_DEV_GUIDE.md) for API surface and manifest format.

## API Layer

- [MusicApiService.kt](app/src/main/java/cp/player/api/MusicApiService.kt) — Unified interface (sole entry point for all music API calls)
- [MusicApiServiceImpl.kt](app/src/main/java/cp/player/api/MusicApiServiceImpl.kt) — Implementation with cookie injection, error wrapping, response validation
- [MusicApiMethod.kt](app/src/main/java/cp/player/api/MusicApiMethod.kt) — API method name constants

## Health Monitor

- [HealthMonitor.kt](app/src/main/java/cp/player/monitor/HealthMonitor.kt) — Singleton tracking API call metrics (success rate, response time, fallbacks, response compatibility warnings)
- [HealthScreen.kt](app/src/main/java/cp/player/ui/screen/HealthScreen.kt) — Unified health/debug UI (6 tabs: overview, method stats, call log, API test, apiMap, provider status)
- Monitoring is automatic via `MusicApiServiceImpl.callApi()` — no manual instrumentation needed in consumers
- `validateResponse()` checks each response for expected fields, codes, and structure compatibility

## Data Models

- [Song.kt](app/src/main/java/cp/player/model/Song.kt) — Song model
- [Playlist.kt](app/src/main/java/cp/player/model/Playlist.kt) — Playlist model
- [UserProfile.kt](app/src/main/java/cp/player/model/UserProfile.kt) — User profile
- [LyricLine.kt](app/src/main/java/cp/player/model/LyricLine.kt) — Lyric line model
- [PlayerState.kt](app/src/main/java/cp/player/model/PlayerState.kt) — Player state enum/model

JSON parsing uses Gson with flexible deserializers in [JsonUtils.kt](app/src/main/java/cp/player/util/JsonUtils.kt).

## ViewModels

All extend [BaseViewModel.kt](app/src/main/java/cp/player/viewmodel/BaseViewModel.kt) for API access.

- [PlaybackViewModel.kt](app/src/main/java/cp/player/viewmodel/PlaybackViewModel.kt) — Playback state, controls, queue
- [UserViewModel.kt](app/src/main/java/cp/player/viewmodel/UserViewModel.kt) — User data, playlists, cloud songs
- [SearchViewModel.kt](app/src/main/java/cp/player/viewmodel/SearchViewModel.kt) — Search
- [LoginViewModel.kt](app/src/main/java/cp/player/viewmodel/LoginViewModel.kt) — Authentication
- [SettingsViewModel.kt](app/src/main/java/cp/player/viewmodel/SettingsViewModel.kt) — Preferences
- [DownloadViewModel.kt](app/src/main/java/cp/player/viewmodel/DownloadViewModel.kt) — Downloads
- [SocialViewModel.kt](app/src/main/java/cp/player/viewmodel/SocialViewModel.kt) — Comments, messaging

## UI Screens

- [MainScreen.kt](app/src/main/java/cp/player/ui/screen/MainScreen.kt) — Home screen with tabs
- [PlayerScreen.kt](app/src/main/java/cp/player/ui/screen/PlayerScreen.kt) — Full player view
- [SearchScreen.kt](app/src/main/java/cp/player/ui/screen/SearchScreen.kt) — Search
- [LibraryScreen.kt](app/src/main/java/cp/player/ui/screen/LibraryScreen.kt) — Local library
- [PlaylistDetailScreen.kt](app/src/main/java/cp/player/ui/screen/PlaylistDetailScreen.kt) — Playlist detail
- [SettingsScreen.kt](app/src/main/java/cp/player/ui/screen/SettingsScreen.kt) — Settings
- [LoginScreen.kt](app/src/main/java/cp/player/ui/screen/LoginScreen.kt) — Login
- [DownloadsScreen.kt](app/src/main/java/cp/player/ui/screen/DownloadsScreen.kt) — Downloads
- [LyricsScreen.kt](app/src/main/java/cp/player/ui/screen/LyricsScreen.kt) — Lyrics overlay
- [CloudMusicScreen.kt](app/src/main/java/cp/player/ui/screen/CloudMusicScreen.kt) — Cloud music
- [SetupScreen.kt](app/src/main/java/cp/player/ui/screen/SetupScreen.kt) — First-run setup
- [ChatScreen.kt](app/src/main/java/cp/player/ui/screen/ChatScreen.kt) — Messaging

## Rust Native (`app/src/main/rust/`)

- `jni_api.rs` — JNI bridge functions
- `audio/` — Playback engine (CPAL, Symphonia, WavPack, Opus)
- `uac2/` — USB Audio Class 2.0 host
- `library_scan/` — Local music scanning
- `vendor/` — Vendored cpal, opus-sys, wavpack-sys

## Conventions

- Code comments and docs are primarily in Chinese
- JVM target: Java 21. Target ABI: `arm64-v8a`. Locales: `en`, `zh`, `ja`
- State: `StateFlow`/`MutableStateFlow` in ViewModels, `collectAsState()` in Compose
- Images: Coil3 + OkHttp backend, Palette for dynamic theming
- Version catalog: `gradle/libs.versions.toml`

任务完成后需要推送至手机测试./gradlew assemblefastRelease && adb install -r app/build/outputs/apk/fastRelease/app-fastRelease.apk && adb shell monkey -p cp.player -c android.intent.category.LAUNCHER 1
最后提交git commit