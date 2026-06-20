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
  },
  "updateUrl": "https://example.com/provider-update.json"
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
- **`updateUrl` (可选)**: 检查更新的端点 URL。当用户在设置中点击"检查更新"时，CPPlayer 会向此 URL 发送 HTTP GET 请求，获取最新版本信息。详见下文"模块更新"章节。

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

## AMLL TTML 歌词平台检测

CPPlayer 支持从 [AMLL TTML Database](https://github.com/amll-dev/amll-ttml-db) 获取 TTML 格式歌词（逐字、翻译、注音）。当用户在设置中将歌词来源设为 "AMLL 优先" 或 "仅 AMLL" 且平台设为 "自动" 时，CPPlayer 会根据 **provider 的 `id` 和 `name`** 自动推断平台。

### 检测规则

| 关键词（不区分大小写） | 匹配平台 |
|---|---|
| `netease`, `ncm`, `云音乐`, `网易` | `ncm` (网易云音乐) |
| `qq`, `tencent`, `qq音乐` | `qq` (QQ 音乐) |
| `apple`, `apple music`, `itunes` | `am` (Apple Music) |
| `spotify` | `spotify` |

### 开发建议

- **`name` 字段更重要**：检测同时检查 `id` 和 `name`，建议在 `name` 中包含平台关键词以确保正确识别。
- 示例：`"id": "cp.provider.rust.default"`, `"name": "Default Rust NCM Provider"` → 识别为 `ncm`（因为 name 包含 "NCM"）
- 若无法自动识别，用户可在设置中手动指定 AMLL 平台。

### 支持的 AMLL 平台文件夹

| 平台标识 | AMLL 数据库文件夹 |
|---|---|
| `ncm` | `ncm-lyrics/` |
| `qq` | `qq-lyrics/` |
| `am` | `am-lyrics/` |
| `spotify` | `spotify-lyrics/` |

TTML 文件命名格式为 `{songId}.ttml`，其中 `songId` 为该平台的原始歌曲 ID。

---

## 格式转换 (Format Conversion)

由于各音源的数据结构不同，**所有提供商必须在自己内部实现格式转换**。提供商的返回 JSON 结构需要尽量对齐网易云音乐 (NeteaseCloudMusic API) 的默认结构。

不过无需担心，CPPlayer 的内部 `JsonUtils` 具有很强的容错和搜索能力。例如对于一首歌曲（Song），应用会自动尝试读取 `songId`, `id`, `songName`, `name`, 并递归搜索其中的 `url` 或 `picUrl`。
你只需确保返回的 JSON 成功状态为 `"code": 200`，并将实体对象放置在 `"data"`, `"result"`, 或 `"songs"` 数组中即可。

---

## 健康监控与兼容性检查 (Health Monitor & Compatibility)

CPPlayer 内置了一套 **API 健康监控系统**，会自动对每次 API 调用进行兼容性检查。当你的 Provider 返回的响应不符合预期时，系统会记录警告并在"健康状态"界面展示。**请确保你的 Provider 通过所有检查以提供最佳用户体验。**

### 响应码要求

CPPlayer 接受以下 `code` 值为成功：

| code 值 | 含义 |
|---------|------|
| `200` | 标准成功（**推荐**） |
| `0` | 成功（部分 API 约定） |
| `201` | 创建成功 |
| `301` | 重定向成功 |

其他 code 值（如 `400`, `500`, `-1` 等）会被标记为失败或警告。特别地，`code: -1` 会被识别为"Provider 不支持"。

### 每个 API 方法的期望数据字段

CPPlayer 会检查响应中是否存在**期望的数据字段**。如果主字段不存在，会尝试回退字段。以下是每个方法的期望字段：

| API 方法 | 期望字段 | 回退字段 | 说明 |
|---------|---------|---------|------|
| `cloudsearch` | `result` | `data` | 搜索结果，内含 `songs`/`albums`/`artists`/`playlists` |
| `user/playlist` | `playlist` | `data` | 用户歌单数组 |
| `user/detail` | `profile` | `data` | 用户资料对象 |
| `user/cloud` | `data` | — | 云盘歌曲数组 |
| `likelist` | `ids` | `data` | 喜欢的歌曲 ID 数组 |
| `recommend/songs` | `data` | — | 推荐歌曲数组 |
| `recommend/resource` | `recommend` | `data` | 推荐歌单数组 |
| `playlist/detail` | `playlist` | `data` | 歌单详情对象 |
| `playlist/track/all` | `songs` | `data` | 歌单内歌曲数组 |
| `album` | `album` | `data` | 专辑详情对象 |
| `artist/detail` | `data` | — | 歌手详情对象 |
| `artist/songs` | `songs` | `data` | 歌手歌曲数组 |
| `artist/album` | `hotAlbums` | `data` | 歌手专辑数组 |
| `song/detail` | `songs` | `data` | 歌曲详情数组 |
| `song/url/v1` | `data` | — | 播放 URL 数组，每项含 `url` 字段 |
| `song/url/v1/302` | `data` | — | 同上（302 重定向版本） |
| `lyric/new` | `lrc` | `data` | 歌词对象，含 `lyric` 字段 |
| `comment/*` | `comments` | `data` | 评论数组 |
| `msg/private` | `msgs` | `data` | 私信数组 |
| `msg/recentcontact` | `data` | — | 最近联系人数组 |

### 常见兼容性警告

| 警告类型 | 原因 | 修复方法 |
|---------|------|---------|
| **缺少数据字段** | 响应中没有期望的字段 | 将数据放在上述期望字段中 |
| **空数据数组** | 数据字段存在但为空 `[]` | 确保有实际数据返回，或在无数据时返回合理提示 |
| **异常响应码** | code 不在 200/0/201/301 范围 | 使用 `"code": 200` 表示成功 |
| **缺少 code 字段** | 响应中没有 `code` 字段 | **必须**包含 `"code": 200` |
| **响应格式异常** | 返回内容不是合法 JSON | 确保返回标准 JSON 字符串 |
| **响应过慢** | 响应时间超过 5 秒 | 优化后端性能或增加缓存 |

### 开发建议

1. **始终返回 `"code": 200`**：这是最可靠的成功标识
2. **数据放在正确的字段中**：参考上表的期望字段，CPPlayer 会优先读取这些字段
3. **错误时返回有意义的 `msg`**：如 `{"code": 500, "msg": "歌曲不存在"}`
4. **不要省略 `code` 字段**：缺少 `code` 会触发警告
5. **测试你的 Provider**：导入后进入 **设置 → 调试 → 健康状态 → 测试** Tab，手动测试各个 API 方法，确认无警告

### 查看健康状态

导入 Provider 后，可通过以下路径查看兼容性检查结果：

**设置 → 调试 → 健康状态**

- **概览 Tab**：查看 Provider 健康评分（0-100 分）和兼容性警告数
- **方法 Tab**：按 API 方法查看调用统计和警告分布
- **日志 Tab**：查看每次调用的详细记录，展开可看到具体的期望字段和实际字段信息
- **测试 Tab**：手动测试单个 API 方法

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

搜索类型常量定义在 `MusicApiMethod` 中，供全应用统一使用：

| 常量名 | 值 | 说明 |
|---|---|---|
| `SEARCH_TYPE_SONG` | `1` | 单曲 |
| `SEARCH_TYPE_ALBUM` | `10` | 专辑 |
| `SEARCH_TYPE_ARTIST` | `100` | 歌手 |
| `SEARCH_TYPE_PLAYLIST` | `1000` | 歌单 |

- **综合搜索**: `cloudsearch`
  - **参数**: `{"keywords": "...", "type": "1"}` (使用上述类型值)
  - **返回** (按 type 不同，结果在 `result` 下的对应数组中):
    ```json
    {
      "code": 200,
      "result": {
        "songs": [ ... ],
        "albums": [ { "id": 1, "name": "Album", "picUrl": "...", "size": 12, "artists": [{"name": "Artist"}] } ],
        "artists": [ { "id": 1, "name": "Artist", "picUrl": "...", "alias": ["别名"], "albumSize": 10 } ],
        "playlists": [ ... ]
      }
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
- **用户歌单**: `user/playlist` (参数 `uid`, `limit`, `offset`)
  - **返回要求**: 必须包含全量歌单（包含创建和收藏的）。为了正确在 UI 中区分，返回的歌单对象必须包含 `subscribed` 字段（布尔值）：`false` 表示自创歌单，`true` 表示收藏歌单。此外可包含 `trackCount` (歌曲数量)、`description` (简介，可为null)、`creator` 字段。
- **云盘歌曲**: `user/cloud` (参数 `limit`)
- **喜欢列表**: `likelist` (参数 `uid`)
- **喜欢某歌曲**: `like` (参数 `id`, `like: "true/false"`)

### 6. 社交与评论 (Social)
- **获取评论**: `comment/floor` (参数 `parentCommentId`, `id`, `type`)
- **点赞评论**: `comment/like` (参数 `id`, `cid`, `t`, `type`)
- **发送/回复评论**: `comment` 
- **私信相关**: `msg/recentcontact`, `msg/private/history`, `send/text`

---

## 模块更新 (Module Update)

CPPlayer 支持模块的自动更新检查和手动更新。更新时会**保留用户数据**（如登录信息、缓存配置等）。

### 自动更新检查

在 `manifest.json` 中配置 `updateUrl` 字段后，用户可以在设置中点击"检查更新"按钮。CPPlayer 会向该 URL 发送 HTTP GET 请求，期望获取以下 JSON 响应：

```json
{
  "version": "1.1.0",
  "downloadUrl": "https://example.com/provider-v1.1.0.zip",
  "changelog": "修复了xxx问题，新增yyy功能"
}
```

#### 响应字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `version` | string | ✅ | 最新版本号（语义化版本格式，如 `1.2.3`） |
| `downloadUrl` | string | ✅ | 新版本 zip 包的下载地址 |
| `changelog` | string | ❌ | 更新日志内容，展示给用户 |

#### 版本比较规则

CPPlayer 使用标准的语义化版本比较：逐段比较主版本号、次版本号、修订号。例如：
- `1.0.0` < `1.0.1` < `1.1.0` < `2.0.0`
- `1.0` 等同于 `1.0.0`

只有当远程版本**高于**本地版本时，才会提示有可用更新。

### 手动更新

用户也可以在提供者管理界面点击"手动更新"，选择一个新版本的 `.zip` 文件进行更新。

### 更新保留的用户数据

更新过程中，模块目录下的 `user_data/` 子目录会被完整保留。建议将用户配置、登录信息等敏感数据存储在此目录下。

### 无需 updateUrl 的模块

如果模块没有配置 `updateUrl`，"检查更新"按钮将不会显示，用户仍可通过"手动更新"按钮选择 zip 文件进行更新。

---

## 打包和测试

1. 确保所有资源文件（`manifest.json` 以及可选的二进制/.so 文件）都位于文件夹的根目录中。
2. 选择所有文件并将它们压缩成一个 `.zip` 存档。
3. 在 Android 设备上打开 CPPlayer。前往 **设置 (Settings) -> 提供者管理 (Provider Management) -> “导入新模块 (.zip) (Import New Module)”**。
4. 尽情使用您的自定义音乐源吧！
5. **检查兼容性**：导入后前往 **设置 → 调试 → 健康状态**，在”测试”Tab 中逐一测试 API 方法，确认无兼容性警告。在”概览”Tab 查看健康评分，确保达到 80 分以上。
6. 出现问题时可使用 **”日志查看器 (LogViewer)”** 或 **”健康状态 → 日志 Tab”** 排查返回数据是否匹配上述格式。
