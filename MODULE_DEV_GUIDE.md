# CPPlayer 模块开发指南

欢迎阅读 CPPlayer 模块开发指南！CPPlayer 采用灵活的插件架构，允许用户定义自己的后端数据源（音乐提供者）。提供者（Provider）打包为 `.zip` 文件，并可直接从应用界面导入。

## 模块结构

一个模块简单来说就是一个包含在根目录下的 `manifest.json` 文件的 `.zip` 文件，以及所需的任何可执行二进制文件、共享对象（`.so`）或配置文件。

### 示例结构
```
my_provider.zip
├── manifest.json
└── libcp_api.so  (适用于 JNI 类型)
    或
└── server_bin    (适用于 Binary 二进制类型)
```

## `manifest.json` 定义

每个模块都必须包含一个 `manifest.json` 文件。格式如下：

```json
{
  "id": "com.yourname.provider.id",
  "name": "My Custom Music Provider",
  "version": "1.0.0",
  "type": "jni",
  "entryPoint": "libcp_api.so",
  "apiMap": {}
}
```

### 字段说明：
- **`id`**: 模块的唯一标识符（例如 `cp.provider.rust.default`）。它将用作所有应用缓存、配置文件和首选项的命名空间。
- **`name`**: 在设置中显示的易于阅读的名称。
- **`version`**: 版本字符串。
- **`type`**: 后端连接类型。支持的类型有：
  - `"jni"`: 原生 Android C/C++ 或 Rust 共享库（`.so`）。
  - `"binary"`: 为 Android 编译的独立可执行二进制文件（`aarch64`/`arm64`）。
  - `"http"` / `"websocket"`: 连接到正在运行的本地或远程 HTTP 服务。
- **`entryPoint`**: 
  - 对于 `jni` 和 `binary`，这是包含在 zip 文件中的文件名（例如 `libcp_api.so`）。
  - 对于 `http`，这是基础 URL（例如 `http://127.0.0.1:3000/api`）。

## 开发 JNI 提供者

当使用 `type: "jni"` 时，CPPlayer 将通过 `System.load()` 加载您的 `.so` 文件。您的共享库必须精确导出以下 JNI 函数：

```c
// 启动您的后台服务器或初始化资源
void Java_cp_player_provider_JniProvider_startNativeServer(JNIEnv *env, jobject thiz, jstring host, jint port);

// 处理主要的 API 路由/RPC
jstring Java_cp_player_provider_JniProvider_nativeCallApi(JNIEnv *env, jobject thiz, jstring method, jstring paramsJson);

// 分析音频文件特征（如 BPM）
jstring Java_cp_player_provider_JniProvider_analyzeAudioFile(JNIEnv *env, jobject thiz, jstring path);
```

> 如果您使用 Rust，请参考代码中包含的 `src/util/jni.rs` 作为实现参考。

## 开发 Binary (二进制) 提供者

当使用 `type: "binary"` 时，CPPlayer 将解压您的二进制文件，对其执行 `chmod +x`，并使用 `ProcessBuilder` 将其作为子进程执行。

- **端口协商**: 应用在启动时会将 `--port <PORT>` 传递给您的二进制文件。您必须在 `127.0.0.1:<PORT>` 启动一个 HTTP 服务器。
- **API 映射**: 应用将向 `http://127.0.0.1:<PORT>/api/<method>` 发送 HTTP POST 请求，请求参数作为 JSON body 传递。

## 开发 HTTP 提供者

当使用 `type: "http"` 时，您无需提供二进制文件。只需将 `entryPoint` 指向远程或本地运行的服务器（例如 `https://my-music-api.example.com`）。

应用将向 `<entryPoint>/<method>` 发送标准的 HTTP POST 请求。

## 打包和测试

1. 确保所有资源文件（`manifest.json` 以及可选的二进制/.so 文件）都位于文件夹的根目录中。
2. 选择所有文件并将它们压缩成一个 `.zip` 存档。
3. 在 Android 设备上打开 CPPlayer。前往 设置 (Settings) -> 提供者管理 (Provider Management) -> "导入新模块 (.zip) (Import New Module)"。
4. 尽情使用您的自定义音乐源吧！
