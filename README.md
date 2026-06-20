# CPPlayer

<p align="center">
  <strong>Android 平台的高性能音乐播放器</strong><br>
  插件化架构 · 双引擎播放 · 交叉淡入淡出 · USB DAC 独占模式
</p>

---

## 简介

CPPlayer 是一款功能丰富的 Android 音乐播放器，采用**插件化 Provider 架构**，支持接入多种音乐数据源。内置 ExoPlayer 和 FlickPlayer（基于 Rust 原生引擎）双播放引擎，提供高品质音频播放体验。

> **使用了 [FLICK](https://github.com/moss-apps/Flick) 引擎，感谢**

---

## 核心功能

### 🎵 播放引擎

| 功能 | 说明 |
|------|------|
| **双引擎切换** | ExoPlayer（Media3）与 FlickPlayer（Rust JNI）自由切换 |
| **交叉淡入淡出** | 两首歌之间平滑过渡，支持自定义淡入淡出时长 |
| **USB DAC 独占模式** | UAC 2.0 协议直连 USB 音频设备，绕过 Android 重采样 |
| **流媒体代理** | 内置本地代理服务器，支持 progressive streaming |
| **多音质支持** | 标准 / 较高 / 极高 / 无损 / Hi-Res / 杜比全景声 |

### 🔌 Provider 插件系统

CPPlayer 采用**模块化插件架构**，音乐数据源以 `.zip` 模块形式导入，无需修改应用代码即可接入不同音乐平台。

- **三种传输方式**：HTTP REST / 独立可执行文件 / JNI 本地库
- **API 路由映射**：通过 `apiMap` 自定义端点映射
- **功能屏蔽**：不支持的 API 可标记为 `unsupported`，用户操作时自动提示
- **热更新**：支持自动检查更新，更新时保留用户数据
- **健康监控**：内置 API 兼容性检查系统，自动检测响应格式问题

详见 [Provider 开发指南](PROVIDER_DEV_GUIDE.md)。

### 🔍 搜索与发现

- 综合搜索：歌曲、专辑、歌手、歌单
- 热搜列表与搜索建议
- 每日推荐歌曲与歌单
- 私人 FM 模式
- 智能播放列表（心动模式）

### 📋 歌单管理

- 创建 / 删除 / 编辑歌单
- 收藏歌单
- 歌单内歌曲添加 / 删除
- 云盘音乐管理

### 🎤 歌词系统

- LRC 逐行歌词
- YRC 逐字歌词（逐字高亮）
- 翻译歌词
- AMLL TTML 歌词支持（从 AMLL TTML Database 自动获取）
- 多平台自动识别（网易云 / QQ 音乐 / Apple Music / Spotify）

### 👤 用户系统

- 多种登录方式：二维码扫码 / 邮箱 / 手机号 / 游客
- 用户歌单同步
- 喜欢列表
- 云盘歌曲管理

### 💬 社交功能

- 歌曲 / 歌单 / 专辑评论
- 评论点赞与回复
- 私信系统
- 未读消息提醒

### ⬇️ 下载管理

- 歌曲下载
- 多音质下载选择
- 下载队列管理

### ⚙️ 设置与调试

- 引擎切换设置
- 音频焦点自动管理
- 模块管理（导入 / 更新 / 删除）
- 日志查看器
- 健康状态监控面板（概览 / 方法统计 / 调用日志 / API 测试）

---

## 技术架构

```
┌──────────────────────────────────────────────────────────────┐
│                    UI Layer (Jetpack Compose)                 │
│   Material 3 · Navigation Compose · Dynamic Theme            │
├──────────────────────────────────────────────────────────────┤
│                    ViewModel Layer                            │
│   StateFlow / MutableStateFlow · collectAsState()            │
├──────────────────────────────────────────────────────────────┤
│                    Repository Layer                           │
│   PlaybackRepository · JSON → Domain Model                   │
├──────────────────────────────────────────────────────────────┤
│              MusicApiService（统一 API 入口）                  │
│   路由 · cookie 注入 · 错误包装 · 多 Provider 容灾           │
├──────────────────────────────────────────────────────────────┤
│                    Provider Layer                              │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│   │JniProvider│ │BinaryProvider│ │HttpProvider│              │
│   └──────────┘  └──────────┘  └──────────┘                  │
│   ModuleManager → 模块加载 (manifest.json → Provider 实例)    │
├──────────────────────────────────────────────────────────────┤
│                    Audio Engine Layer                          │
│   ExoPlayer (Media3) · FlickPlayer (Rust JNI)                │
│   CrossfadeManager · UsbAudioManager · StreamProxy           │
└──────────────────────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术 |
|------|------|
| **UI** | Jetpack Compose, Material 3 Expressive, Coil3, Palette |
| **架构** | MVVM, StateFlow, Navigation Compose |
| **播放** | Media3 ExoPlayer, Rust (CPAL + Symphonia) |
| **网络** | OkHttp, Gson |
| **存储** | SharedPreferences, 文件缓存 |
| **原生** | Rust (JNI), Cargo NDK |
| **构建** | Gradle, Version Catalog |

---

## 构建

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 21
- Rust 工具链 + `cargo-ndk`（用于编译原生 Rust 引擎）
- 目标 ABI：`arm64-v8a`

### 构建命令

```bash
# Debug APK（自动编译 Rust 原生库）
./gradlew assembleDebug

# Release APK（启用 R8 优化）
./gradlew assembleRelease

# Fast Release（无 R8，debug 签名，用于 Compose 性能分析）
./gradlew assembleFastRelease
```

### 安装到设备

```bash
# 编译并安装到连接的设备
./gradlew assembleFastRelease && \
adb install -r app/build/outputs/apk/fastRelease/app-fastRelease.apk && \
adb shell monkey -p cp.player -c android.intent.category.LAUNCHER 1
```

### 自动化构建

本项目已配置 GitHub Actions 工作流，每次推送到 `main` 分支或提交 Pull Request 时将自动进行编译检查，同时也可以生成可安装的 APK 文件。

---

## 项目结构

```
CPPlayer/
├── app/src/main/java/cp/player/
│   ├── api/              # API 层 (MusicApiService, MusicApiMethod)
│   ├── engine/           # 播放引擎 (ExoPlayer, FlickPlayer, Crossfade)
│   ├── model/            # 数据模型 (Song, Playlist, UserProfile)
│   ├── monitor/          # 健康监控 (HealthMonitor)
│   ├── provider/         # Provider 系统 (ModuleManager, ProviderManager)
│   ├── repository/       # 数据仓库
│   ├── service/          # 后台服务 (MusicService, BackendDataSource)
│   ├── ui/               # Compose UI (Screen, Component, Theme)
│   ├── util/             # 工具类 (JsonUtils, DebugLog)
│   └── viewmodel/        # ViewModel 层
├── app/src/main/rust/    # Rust 原生代码
│   ├── audio/            # 音频引擎 (CPAL, Symphonia, WavPack, Opus)
│   ├── uac2/             # USB Audio Class 2.0
│   └── library_scan/     # 本地音乐扫描
├── docs/                 # 开发文档
└── gradle/               # Gradle 配置
```

---

## 开发文档

| 文档 | 说明 |
|------|------|
| [Provider 开发指南](PROVIDER_DEV_GUIDE.md) | 第三方 Provider 完整开发流程 |
| [CLAUDE.md](CLAUDE.md) | 项目架构与开发指引 |

---

## 许可证

本项目采用 [MIT 许可证](LICENSE)。

---

## 贡献

欢迎提交 Issue 和 Pull Request 来帮助改进此项目。
