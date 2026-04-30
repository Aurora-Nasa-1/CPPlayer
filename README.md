# CPPlayer

CPPlayer 是一个 Android 平台的视频播放器应用。

## 简介

本项目包含核心的视频播放功能，提供流畅的用户体验和丰富的功能特性。

## 开发指南

关于模块开发的详细说明，请参考 [MODULE_DEV_GUIDE.md](MODULE_DEV_GUIDE.md)。

## 构建说明

本项目使用 Gradle 构建系统。

### 本地编译

1. 克隆仓库:
   ```bash
   git clone <repository-url>
   cd CPPlayer
   ```

2. 编译 Debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

3. 编译 Release APK:
   ```bash
   ./gradlew assembleRelease
   ```

### 自动化构建 (GitHub Actions)

本项目已配置 GitHub Actions 工作流，每次推送到 `main` 分支或提交 Pull Request 时将自动进行编译检查，同时也可以生成可安装的 APK 文件。

## 贡献

欢迎提交 Issue 和 Pull Request 来帮助改进此项目。

## 许可证

本项目采用 [MIT 许可证](LICENSE)。
