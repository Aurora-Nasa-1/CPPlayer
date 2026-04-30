# CPPlayer 模块开发指南

欢迎阅读 CPPlayer 模块开发指南！CPPlayer 采用灵活的插件架构，允许用户定义自己的后端数据源（音乐提供者）。提供者（Provider）打包为 `.zip` 文件，并可直接从应用界面导入。

---

## 模块结构

一个模块简单来说就是一个包含在根目录下的 `manifest.json` 文件的 `.zip` 文件，以及所需的任何可执行二进制文件、共享对象（`.so`）或配置文件。

### 示例结构
```text
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
  "apiMap": {
    "song/url/v1": "custom_song_url",
    "recommend/songs": "unsupported"
  }
}
```

### 字段说明：
- **`id`**: 模块的唯一标识符（例如 `cp.provider.rust.default`）。它将用作所有应用缓存、配置文件和首选项的命名空间。
- **`name`**: 在设置中显示的易于阅读的名称。
- **`version`**: 版本字符串。
- **`type`**: 后端连接类型。支持的类型有：
  - `"jni"`: 原生 Android C/C++ 或 Rust 共享库（`.so`）。
  - `"binary"`: 为 Android 编译的独立可执行二进制文件（`aarch64`/`arm64`）。
  - `"http"`: 连接到正在运行的本地或远程 HTTP 服务。
- **`entryPoint`**: 
  - 对于 `jni` 和 `binary`，这是包含在 zip 文件中的文件名（例如 `libcp_api.so`）。
  - 对于 `http`，这是基础 URL（例如 `http://127.0.0.1:3000/api` 或 `https://myapi.example.com`）。
- **`apiMap` (可选)**: 允许配置 API 路由映射或屏蔽不支持的功能。详见下文。

---

## 高级特性：API 映射与屏蔽 (API Mapping & Disabling)

CPPlayer 支持通过 `manifest.json` 中的 `apiMap` 字段重新映射请求的 API 路由，或者直接屏蔽无法实现的功能。

**路由映射：**  
如果你的服务将获取播放链接的接口命名为 `get_music_link`，而 CPPlayer 默认请求 `song/url/v1`，你可以配置映射：
```json
"apiMap": {
  "song/url/v1": "get_music_link"
}
```

**屏蔽/删除入口 (Unsupported APIs)：**  
如果你的音源不提供某项功能（例如无法获取用户歌单，或不支持评论），你可以将该接口的映射值设为空字符串 `""` 或 `"unsupported"`。
CPPlayer 识别到后，会主动拦截请求并向用户弹出“该提供商不支持此功能”的提示，防止界面异常。
```json
"apiMap": {
  "comment/floor": "unsupported",
  "user/cloud": ""
}
```

---

## 格式转换 (Format Conversion)

由于各音源的数据结构不同，**所有提供商必须在自己内部实现格式转换**。提供商的返回 JSON 结构需要尽量对齐网易云音乐 (NeteaseCloudMusic API) 的默认结构。

不过无需担心，CPPlayer 的内部 `JsonUtils` 具有很强的容错和搜索能力。例如对于一首歌曲（Song），应用会自动尝试读取 `songId`, `id`, `songName`, `name`, 并递归搜索其中的 `url` 或 `picUrl`。
你只需确保返回的 JSON 成功状态为 `"code": 200`，并将实体对象放置在 `"data"`, `"result"`, 或 `"songs"` 数组中即可。

---

## 开发 HTTP 提供者 (`type: "http"`)

当使用 `type: "http"` 时，CPPlayer 将向您的 `<entryPoint>/<method>` 发送标准 `HTTP POST` 请求。
请求参数 (params) 会被格式化为 JSON 并放置在 Request Body 中 (`application/json`)。

返回结果应当也是 JSON 字符串。
*注意：即使请求由于某些原因失败，您也应当返回带有 `{"code": 500, "msg": "Error"}` 格式的 JSON，避免应用崩溃。*

## 开发 JNI 提供者 (`type: "jni"`)

使用 `type: "jni"` 时，CPPlayer 通过 `System.load()` 加载您的 `.so` 文件。您必须精确导出以下 C 接口：

```c
#include <jni.h>

extern "C" {

// 启动您的后台服务器或初始化资源
JNIEXPORT void JNICALL Java_cp_player_provider_JniProvider_startNativeServer(JNIEnv *env, jobject thiz, jstring host, jint port) {
    // 处理初始化逻辑
}

// 处理主要的 API 路由/RPC
JNIEXPORT jstring JNICALL Java_cp_player_provider_JniProvider_nativeCallApi(JNIEnv *env, jobject thiz, jstring method, jstring paramsJson) {
    // 1. 将 method 转换为您的内部路由
    // 2. 解析 paramsJson
    // 3. 返回包含结果的 JSON 字符串
    return env->NewStringUTF("{\"code\": 200}");
}

// 分析音频文件特征（如 BPM）
JNIEXPORT jstring JNICALL Java_cp_player_provider_JniProvider_analyzeAudioFile(JNIEnv *env, jobject thiz, jstring path) {
    return env->NewStringUTF("{\"code\": 200}");
}

}
```

## 开发 Binary (二进制) 提供者 (`type: "binary"`)

应用在启动时会将 `--port <PORT>` 参数传递给您的二进制文件。您必须在 `127.0.0.1:<PORT>` 启动一个 HTTP 服务器。
CPPlayer 将向 `http://127.0.0.1:<PORT>/api/<method>` 发送 HTTP POST 请求。

---

## 软件使用的所有 API 功能清单及格式

下面是 CPPlayer 发起的所有 API (Method) 请求，以及期待的返回值格式示例。你的服务必须处理这些 `method`，或者在 `apiMap` 中将其设为 `unsupported`。

### 1. 播放与资源获取
- **获取音频播放链接**: `song/url/v1` 或 `song/url/v1/302`
  - **参数**: `{"id": "...", "level": "standard/higher/lossless"}`
  - **返回格式**: 
    ```json
    {
      "code": 200,
      "data": [
        { "id": "...", "url": "http://audio-cdn..." }
      ]
    }
    ```
    *（注：若包含 `redirectUrl`，CPPlayer 将优先使用。）*
- **获取下载链接**: `song/download/url/v1`
  - 与 `song/url/v1` 类似。
- **获取歌词**: `lyric/new`
  - **参数**: `{"id": "..."}`
  - **返回**: `{"code": 200, "lrc": {"lyric": "[00:00.00] 歌词内容"}}`
- **歌曲详情**: `song/detail`
  - **参数**: `{"ids": "id1,id2"}`
  - **返回**: `{"code": 200, "songs": [{ "id": "1", "name": "Song", "ar": [{"name": "Artist"}] }]}`

### 2. 搜索
- **综合搜索**: `cloudsearch`
  - **参数**: `{"keywords": "...", "type": "1"}` (1: 单曲, 10: 专辑, 100: 歌手, 1000: 歌单)
  - **返回**: 
    ```json
    {
      "code": 200,
      "result": { "songs": [ ... ] } 
    }
    ```
- **搜索建议**: `search/suggest`
  - **参数**: `{"keywords": "...", "type": "mobile"}`
- **热搜列表**: `search/hot/detail`

### 3. 首页与发现
- **私人 FM**: `personal_fm`
  - **返回**: `{"code": 200, "data": [ ...songs... ]}`
- **每日推荐歌曲**: `recommend/songs`
- **每日推荐歌单**: `recommend/resource`
- **智能播放列表**: `playmode/intelligence/list`

### 4. 歌单与艺术家
- **歌单详情**: `playlist/detail` (参数 `id`)
- **歌单所有歌曲**: `playlist/track/all` (参数 `id`)
- **艺术家详情**: `artist/detail` (参数 `id`)
- **艺术家热门歌曲**: `artist/songs` (参数 `id`, `limit`)
- **艺术家专辑**: `artist/album` (参数 `id`, `limit`)

### 5. 用户与账户 ( Authentication & User)
如果您的提供商需要登录，需要处理以下登录及状态接口，或者在 `apiMap` 屏蔽登录。
- **二维码获取**: `login/qr/key`
- **二维码生成**: `login/qr/create` (参数 `key`)
- **二维码状态轮询**: `login/qr/check` (参数 `key`)
- **邮箱登录**: `login` (参数 `email`, `password` 或 `md5_password`)
- **手机登录**: `login/cellphone` (参数 `phone`, `password` 或 `md5_password`, 或 `captcha`)
- **游客登录**: `register/anonimous`
- **刷新登录**: `login/refresh`
- **退出登录**: `logout`
- **发送验证码**: `captcha/sent` (参数 `phone`, `ctcode`)
- **验证验证码**: `captcha/verify` (参数 `phone`, `captcha`, `ctcode`)
- **登录状态**: `login/status`
- **用户详情**: `user/detail` (参数 `uid`)
- **用户歌单**: `user/playlist` (参数 `uid`)
- **云盘歌曲**: `user/cloud` (参数 `limit`)
- **喜欢列表**: `likelist` (参数 `uid`)
- **喜欢某歌曲**: `like` (参数 `id`, `like: "true/false"`)

### 6. 社交与评论 (Social)
- **获取评论**: `comment/floor` (参数 `parentCommentId`, `id`, `type`)
- **点赞评论**: `comment/like` (参数 `id`, `cid`, `t`, `type`)
- **发送/回复评论**: `comment` 
- **私信相关**: `msg/recentcontact`, `msg/private/history`, `send/text`

---

## 打包和测试

1. 确保所有资源文件（`manifest.json` 以及可选的二进制/.so 文件）都位于文件夹的根目录中。
2. 选择所有文件并将它们压缩成一个 `.zip` 存档。
3. 在 Android 设备上打开 CPPlayer。前往 **设置 (Settings) -> 提供者管理 (Provider Management) -> "导入新模块 (.zip) (Import New Module)"**。
4. 尽情使用您的自定义音乐源吧！出现问题时可使用侧边栏的 **“日志查看器 (LogViewer)”** 排查返回数据是否匹配上述格式。
