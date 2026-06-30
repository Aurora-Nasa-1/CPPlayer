# CPPlayer Provider 开发指南

> **面向：** 想要为 CPPlayer 开发音乐数据源（Provider）的第三方开发者
> **版本：** 2.0.0 · **最后更新：** 2026-06-20

---

## 目录

1. [概述](#1-概述)
2. [快速开始：5 分钟创建你的第一个 Provider](#2-快速开始5-分钟创建你的第一个-provider)
3. [Provider 类型详解](#3-provider-类型详解)
4. [manifest.json 完整规范](#4-manifestjson-完整规范)
5. [API 协议规范](#5-api-协议规范)
6. [必选 API 列表](#6-必选-api-列表)
7. [可选 API 列表](#7-可选-api-列表)
8. [每个 API 的请求与响应格式](#8-每个-api-的请求与响应格式)
9. [不支持的功能处理](#9-不支持的功能处理)
10. [健康监控与兼容性检查](#10-健康监控与兼容性检查)
11. [AMLL TTML 歌词平台检测](#11-amll-ttml-歌词平台检测)
12. [模块更新](#12-模块更新)
13. [模块打包与分发](#13-模块打包与分发)
14. [调试与测试](#14-调试与测试)
15. [常见问题](#15-常见问题)

---

## 1. 概述

CPPlayer 通过 **Provider** 系统接入不同的音乐数据源。你只需：

1. 实现一个能处理标准化 API 请求的后端服务
2. 编写一个 `manifest.json` 描述文件
3. 打包为 `.zip` 导入 CPPlayer

**无需修改 CPPlayer 任何代码。**

### 工作原理

```
CPPlayer App
    │
    ├── MusicApiService  ─→  ProviderManager  ─→  你的 Provider
    │                                              │
    │                    ┌─────────────────────────┘
    │                    │
    │              callApi("song/url/v1", {"id":"123","level":"standard"})
    │                    │
    │                    ▼
    │              你的后端服务 (HTTP/JNI/Binary)
    │                    │
    │                    ▼
    │              返回 JSON: {"code":200,"data":[{"url":"https://..."}]}
    │
    └── 解析 URL → 播放音乐
```

### 术语表

| 术语 | 说明 |
|------|------|
| **Provider** | 你开发的音乐数据源适配器 |
| **API 方法名** | CPPlayer 内部标准化的请求标识，如 `song/url/v1` |
| **apiMap** | 将 API 方法名映射到你的后端实际端点的配置表 |
| **module** | 一个 Provider 的打包单元，包含 `manifest.json` + 可执行文件 |

---

## 2. 快速开始：5 分钟创建你的第一个 Provider

### 2.1 最简方式：HTTP Provider

假设你已经有一个 HTTP 音乐 API 服务运行在 `http://localhost:8080`。

**第一步：创建目录**

```bash
mkdir my-music-provider
cd my-music-provider
```

**第二步：编写 manifest.json**

```json
{
    "id": "my-music",
    "name": "My Music Provider",
    "version": "1.0.0",
    "type": "http",
    "entryPoint": "http://localhost:8080",
    "apiMap": {
        "cloudsearch": "search",
        "song/url/v1": "song/url",
        "song/url/v1/302": "song/url",
        "song/download/url/v1": "song/url",
        "song/detail": "song/detail",
        "lyric/new": "lyric",
        "login/qr/key": "auth/qr/key",
        "login/qr/create": "auth/qr/create",
        "login/qr/check": "auth/qr/check",
        "login/status": "auth/status",
        "register/anonimous": "auth/anonymous"
    }
}
```

**第三步：打包**

```bash
zip -r my-music-provider.zip manifest.json
```

**第四步：导入 CPPlayer**

在 CPPlayer 中：**设置 → 模块管理 → 导入模块** → 选择 `my-music-provider.zip`

**完成！** CPPlayer 现在会使用你的 Provider 作为数据源。

---

## 3. Provider 类型详解

### 3.1 HTTP Provider

**适合：** 已有 HTTP API 服务（如 NeteaseCloudMusicApi、自建后端）

| 属性 | 说明 |
|------|------|
| `type` | `"http"` |
| `entryPoint` | 你的 API 基础 URL，如 `"http://localhost:8080"` |
| 启动方式 | 无需 CPPlayer 启动，需要你自己确保服务运行 |
| 通信方式 | HTTP POST |

**请求格式：**
```
POST {entryPoint}/{mapped_method}
Content-Type: application/json

{"id": "123", "level": "standard", "cookie": "..."}
```

**响应格式：** JSON 字符串（见 [第 8 节](#8-每个-api-的请求与响应格式)）

### 3.2 Binary Provider

**适合：** 独立可执行文件形式的后端

| 属性 | 说明 |
|------|------|
| `type` | `"binary"` |
| `entryPoint` | 可执行文件名，如 `"my-backend"` |
| 启动方式 | CPPlayer 自动启动：`./my-backend --port {port}` |
| 通信方式 | 启动后通过 HTTP POST 与 `http://127.0.0.1:{port}/api/{method}` 通信 |

**要求：**
- 可执行文件必须支持 `--port` 参数指定监听端口
- 必须实现 `POST /api/{method}` 端点
- 返回 JSON 响应

**示例请求：**
```
POST http://127.0.0.1:3000/api/song/url/v1
Content-Type: application/json

{"id": "123", "level": "standard"}
```

### 3.3 JNI Provider

**适合：** 性能敏感的本地库，用 C/C++/Rust 编写

| 属性 | 说明 |
|------|------|
| `type` | `"jni"` |
| `entryPoint` | `.so` 文件名，如 `"libmybackend.so"` |
| 启动方式 | CPPlayer 通过 `System.load()` 加载 |
| 通信方式 | JNI 函数调用 |

**必须导出的 JNI 函数：**

```c
/**
 * 启动本地服务（可选，如果 Provider 不需要独立服务可留空）
 * @param host 监听地址，通常为 "127.0.0.1"
 * @param port 监听端口
 */
JNIEXPORT void JNICALL
Java_cp_player_provider_JniProvider_startNativeServer(
    JNIEnv *env, jobject obj, jstring host, jint port);

/**
 * API 调用（核心，必须实现）
 * @param method API 方法名 (已通过 apiMap 映射)
 * @param paramsJson JSON 格式的请求参数
 * @return JSON 格式的响应字符串
 */
JNIEXPORT jstring JNICALL
Java_cp_player_provider_JniProvider_nativeCallApi(
    JNIEnv *env, jobject obj, jstring method, jstring paramsJson);

/**
 * 音频分析（可选）
 * @param path 音频文件路径
 * @return JSON 格式的分析结果
 */
JNIEXPORT jstring JNICALL
Java_cp_player_provider_JniProvider_analyzeAudioFile(
    JNIEnv *env, jobject obj, jstring path);
```

**Rust 示例：**

```rust
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;

#[no_mangle]
pub extern "C" fn Java_cp_player_provider_JniProvider_nativeCallApi(
    mut env: JNIEnv,
    _class: JClass,
    method: JString,
    params_json: JString,
) -> jstring {
    let method: String = env.get_string(&method).unwrap().into();
    let params: String = env.get_string(&params_json).unwrap().into();

    let response = match method.as_str() {
        "search" => handle_search(&params),
        "song/url" => handle_song_url(&params),
        "lyric" => handle_lyric(&params),
        _ => r#"{"code": -1, "msg": "Method not implemented"}"#.to_string(),
    };

    env.new_string(&response).unwrap().into_raw()
}
```

---

## 4. manifest.json 完整规范

```jsonc
{
    // 必填：Provider 唯一标识符，建议使用小写字母和连字符
    "id": "my-music-provider",

    // 必填：Provider 显示名称，会出现在 CPPlayer 设置中
    "name": "My Music Provider",

    // 必填：语义化版本号
    "version": "1.0.0",

    // 必填：Provider 类型
    // "http"    - HTTP API 服务
    // "binary"  - 可执行文件
    // "jni"     - JNI 本地库
    "type": "http",

    // 必填：入口点
    // type="http"   → API 基础 URL
    // type="binary" → 可执行文件名（相对于模块根目录）
    // type="jni"    → .so 文件名（相对于模块根目录）
    "entryPoint": "http://localhost:8080",

    // 可选：API 方法名映射表
    // key = CPPlayer 内部方法名
    // value = 你的后端实际端点名
    // 如果不提供，CPPlayer 会直接使用内部方法名作为端点
    // 如果映射为 "unsupported"，该功能会被标记为不支持
    "apiMap": {
        "cloudsearch": "search",
        "song/url/v1": "song/url",
        "like": "unsupported"
    },

    // 可选：检查更新的端点 URL
    // 用户点击"检查更新"时，CPPlayer 会向此 URL 发送 HTTP GET 请求
    "updateUrl": "https://example.com/provider-update.json"
}
```

---

## 5. API 协议规范

### 5.1 请求规范

**HTTP Provider：**
```
POST {entryPoint}/{mapped_method}
Content-Type: application/json

{"param1": "value1", "param2": "value2"}
```

**Binary Provider：**
```
POST http://127.0.0.1:{port}/api/{mapped_method}
Content-Type: application/json

{"param1": "value1", "param2": "value2"}
```

**JNI Provider：**
```java
// 直接函数调用
nativeCallApi(mappedMethod, paramsJson)
```

### 5.2 响应规范

所有 API 必须返回 JSON 格式字符串，至少包含 `code` 字段：

```jsonc
{
    "code": 200,    // 必填：200=成功，其他=错误
    "msg": "...",   // 可选：错误信息
    // ... 业务数据字段（因 API 而异）
}
```

**CPPlayer 接受的成功的 `code` 值：**

| code 值 | 含义 |
|---------|------|
| `200` | 标准成功（**推荐**） |
| `0` | 成功（部分 API 约定） |
| `201` | 创建成功 |
| `301` | 重定向成功 |

其他 code 值（如 `400`, `500`, `-1` 等）会被标记为失败或警告。特别地，`code: -1` 会被识别为"Provider 不支持"。

### 5.3 cookie 处理

- 需要登录的 API 会在请求参数中包含 `"cookie": "MUSIC_U=xxx; __csrf=xxx"`
- 你的 Provider 需要将 cookie 传递给实际后端
- `login/status` 和 `register/anonimous` 等认证 API 需要在响应中返回 `"cookie"` 字段

---

## 6. 必选 API 列表

**你的 Provider 必须实现以下 API，否则 CPPlayer 核心功能无法正常工作：**

| API 方法名 | 说明 | 参数 | 必须返回字段 |
|-----------|------|------|-------------|
| `song/url/v1` | 获取歌曲播放 URL | `id`, `level`, `cookie?` | `data[].url` |
| `lyric/new` | 获取歌词 | `id`, `cookie?` | `lrc.lyric`, `yrc.lyric?`, `tlyric.lyric?` |
| `song/detail` | 获取歌曲详情 | `ids` (逗号分隔) | `songs[].{id,name,ar,al,dt}` |

### level 参数值说明

| 值 | 音质 | 码率范围 |
|----|------|----------|
| `standard` | 标准 | 128kbps |
| `higher` | 较高 | 192kbps |
| `exhigh` | 极高 | 320kbps |
| `lossless` | 无损 | FLAC |
| `hires` | Hi-Res | HiRes |
| `jyeffect` | 高清环绕 | - |
| `jymaster` | 超清母带 | - |
| `sky` | 沉浸环绕声 | - |
| `immersive` | 沉浸声 | - |
| `dolby` | 杜比全景声 | - |

---

## 7. 可选 API 列表

实现以下 API 可以解锁 CPPlayer 的更多功能。**不支持的 API 请在 `apiMap` 中映射为 `"unsupported"`。**

### 7.1 搜索

| API 方法名 | 说明 | 参数 |
|-----------|------|------|
| `cloudsearch` | 搜索 | `keywords`, `type` (1=歌曲, 1000=歌单, 100=歌手, 10=专辑) |
| `search/hot/detail` | 热搜 | 无 |
| `search/suggest` | 搜索建议 | `keywords`, `type` |

### 7.2 认证

| API 方法名 | 说明 | 参数 |
|-----------|------|------|
| `login/qr/key` | 获取扫码 key | 无 |
| `login/qr/create` | 创建二维码 | `key`, `qrimg` |
| `login/qr/check` | 检查扫码状态 | `key` |
| `login` | 邮箱登录 | `email`, `password` 或 `md5_password` |
| `login/cellphone` | 手机登录 | `phone`, `password` 或 `captcha` 或 `md5_password` |
| `captcha/sent` | 发送验证码 | `phone` |
| `logout` | 登出 | 无 |
| `register/anonimous` | 游客登录 | 无 |
| `login/status` | 登录状态 | `cookie?` |

### 7.3 用户

| API 方法名 | 说明 | 参数 |
|-----------|------|------|
| `user/playlist` | 用户全部歌单（创建+收藏） | `uid`, `cookie` |
| `user/playlist/create` | 用户创建的歌单 | `uid`, `cookie` |
| `user/playlist/collect` | 用户收藏的歌单 | `uid`, `cookie` |
| `user/detail` | 用户详情 | `uid` |
| `user/cloud` | 云盘歌曲 | `limit`, `cookie` |
| `likelist` | 喜欢列表 | `uid`, `cookie` |
| `like` | 喜欢/取消喜欢 | `id`, `like` ("true"/"false"), `cookie` |
| `recommend/songs` | 每日推荐 | `cookie` |
| `recommend/resource` | 推荐歌单 | `cookie` |
| `recommend/songs/dislike` | 不喜欢 | `id`, `cookie` |

### 7.4 歌单

| API 方法名 | 说明 | 参数 |
|-----------|------|------|
| `playlist/detail` | 歌单详情 | `id` |
| `playlist/track/all` | 歌单全部曲目 | `id`, `limit`, `offset` |
| `playlist/tracks` | 添加/删除曲目 | `op` ("add"/"del"), `pid`, `tracks` (逗号分隔 ID) |
| `playlist/create` | 创建歌单 | `name`, `privacy` (0=公开, 10=私密) |
| `playlist/delete` | 删除歌单 | `id` |
| `playlist/subscribe` | 收藏/取消收藏歌单 | `id`, `t` (1=收藏, 2=取消收藏) |

### 7.5 歌手

| API 方法名 | 说明 | 参数 |
|-----------|------|------|
| `artist/detail` | 歌手详情 | `id` |
| `artist/songs` | 歌手歌曲 | `id`, `limit` |
| `artist/album` | 歌手专辑 | `id`, `limit` |

### 7.6 播放增强

| API 方法名 | 说明 | 参数 |
|-----------|------|------|
| `song/url/v1/302` | 302 重定向播放 URL | `id`, `level`, `cookie?` |
| `song/download/url/v1` | 下载 URL | `id`, `level`, `cookie?` |
| `personal_fm` | 私人 FM | `timestamp`, `cookie` |
| `playmode/intelligence/list` | 心动模式 | `id`, `pid`, `sid`, `count` |

### 7.7 社交

| API 方法名 | 说明 | 参数 |
|-----------|------|------|
| `comment/music` | 音乐评论 | `id`, `limit`, `offset` |
| `comment/playlist` | 歌单评论 | `id`, `limit`, `offset` |
| `comment/album` | 专辑评论 | `id`, `limit`, `offset` |
| `comment/mv` | MV 评论 | `id`, `limit`, `offset` |
| `comment/dj` | 电台评论 | `id`, `limit`, `offset` |
| `comment/video` | 视频评论 | `id`, `limit`, `offset` |
| `comment/floor` | 楼层评论 | `id`, `parentCommentId`, `type`, `limit`, `time?` |
| `comment/like` | 点赞评论 | `id`, `cid`, `t`, `type` ("1"=赞/"0"=取消) |
| `comment` | 发表评论 | `id`, `type`, `content`, `op` ("add"/"reply"), `commentId?` |
| `pl/count` | 未读消息数 | `cookie` |
| `msg/recentcontact` | 最近联系人 | `cookie` |
| `msg/private` | 私信列表 | `limit`, `cookie` |
| `msg/private/history` | 私信历史 | `uid`, `cookie` |
| `msg/private/mark/read` | 标记已读 | `uid`, `cookie` |
| `send/text` | 发送消息 | `user_ids`, `msg`, `cookie` |

### 7.8 模块设置（自定义界面）

| API 方法名 | 说明 | 参数 |
|-----------|------|------|
| `settings/schema` | 获取提供商设置的 UI Schema | 无 |
| `settings/save` | 保存并同步用户设置到提供商 | `settings` (包含键值对的 JSON 字符串) |

---

## 8. 每个 API 的请求与响应格式

### 8.1 必选 API 详细格式

#### `song/url/v1` — 获取播放 URL

**请求：**
```json
{
    "id": "123456",
    "level": "standard",
    "cookie": "MUSIC_U=xxx"
}
```

**成功响应：**
```json
{
    "code": 200,
    "data": [
        {
            "id": 123456,
            "url": "https://m10.music.126.net/20230617120000/xxx.mp3",
            "br": 128000,
            "size": 3840000,
            "type": "mp3"
        }
    ]
}
```

**失败响应（无版权）：**
```json
{
    "code": 200,
    "data": [
        {
            "id": 123456,
            "url": null,
            "br": 0,
            "fee": 1
        }
    ]
}
```

> ⚠️ `data` 是数组格式。`url` 为 null 表示无法获取。

#### `lyric/new` — 获取歌词

**请求：**
```json
{
    "id": "123456"
}
```

**响应：**
```json
{
    "code": 200,
    "lrc": {
        "lyric": "[00:00.00]歌曲名 - 歌手\n[00:05.00]第一行歌词\n[00:10.00]第二行歌词\n"
    },
    "yrc": {
        "lyric": "[00:00.00,0](0,500,0)逐(500,300,0)字(800,400,0)\n"
    },
    "tlyric": {
        "lyric": "[00:05.00]First line translation\n[00:10.00]Second line translation\n"
    }
}
```

**字段说明：**

| 字段 | 说明 | 必须 |
|------|------|------|
| `lrc.lyric` | LRC 格式歌词（行级时间戳） | ✅ |
| `yrc.lyric` | YRC 格式歌词（逐字时间戳） | 推荐 |
| `tlyric.lyric` | 翻译歌词（LRC 格式） | 可选 |

**LRC 格式：**
```
[mm:ss.xx]歌词文本
[00:05.00]Hello World
```

**YRC 格式（逐字歌词）：**
```
[mm:ss.xx,offset](start,duration,flag)字1(start,duration,flag)字2
[00:05.00,0](0,500,0)你(500,300,0)好(800,400,0)
```

#### `song/detail` — 歌曲详情

**请求：**
```json
{
    "ids": "123456,789012,345678"
}
```

**响应：**
```json
{
    "code": 200,
    "songs": [
        {
            "id": 123456,
            "name": "歌曲名",
            "dt": 240000,
            "ar": [
                { "id": 111, "name": "歌手名" }
            ],
            "al": {
                "id": 222,
                "name": "专辑名",
                "picUrl": "https://p1.music.126.net/xxx.jpg"
            }
        }
    ]
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | long | 歌曲 ID |
| `name` | string | 歌曲名 |
| `dt` | long | 时长（毫秒） |
| `ar` | array | 歌手列表，每项含 `id` 和 `name` |
| `al` | object | 专辑信息，含 `id`、`name`、`picUrl`（封面图） |

---

### 8.2 可选 API 详细格式

#### `cloudsearch` — 搜索

**请求：**
```json
{
    "keywords": "周杰伦",
    "type": "1"
}
```

**type 值：** `1`=单曲, `100`=歌手, `10`=专辑, `1000`=歌单, `1014`=视频

**响应 (type=1)：**
```json
{
    "code": 200,
    "result": {
        "songs": [
            {
                "id": 123,
                "name": "晴天",
                "ar": [{ "id": 1, "name": "周杰伦" }],
                "al": { "id": 1, "name": "叶惠美", "picUrl": "https://..." },
                "dt": 269000
            }
        ],
        "songCount": 100
    }
}
```

**响应 (type=1000)：**
```json
{
    "code": 200,
    "result": {
        "playlists": [
            {
                "id": 456,
                "name": "周杰伦精选",
                "coverImgUrl": "https://...",
                "trackCount": 50,
                "creator": { "nickname": "User" }
            }
        ],
        "playlistCount": 200
    }
}
```

#### `search/hot/detail` — 热搜

**请求：** `{}` （无参数）

**响应：**
```json
{
    "code": 200,
    "data": [
        {
            "searchWord": "热搜关键词",
            "content": "热搜描述",
            "score": 10000
        }
    ]
}
```

#### `search/suggest` — 搜索建议

**请求：**
```json
{
    "keywords": "周杰",
    "type": "mobile"
}
```

**响应：**
```json
{
    "code": 200,
    "result": {
        "allMatch": [
            { "keyword": "周杰伦" },
            { "keyword": "周杰伦晴天" }
        ]
    }
}
```

#### `login/qr/key` — 获取扫码 key

**请求：** `{}`

**响应：**
```json
{
    "code": 200,
    "data": {
        "unikey": "xxxxxxxxxxxx"
    }
}
```

#### `login/qr/create` — 创建二维码

**请求：**
```json
{
    "key": "xxxxxxxxxxxx",
    "qrimg": "true"
}
```

**响应：**
```json
{
    "code": 200,
    "data": {
        "qrimg": "data:image/png;base64,iVBORw0KGgo...",
        "qrurl": "https://music.163.com/login?codekey=xxx"
    }
}
```

> `qrimg` 为 base64 编码的二维码图片。`qrimg` 或 `qrurl` 至少返回一个。

#### `login/qr/check` — 检查扫码状态

**请求：**
```json
{
    "key": "xxxxxxxxxxxx"
}
```

**响应：**
```jsonc
{
    "code": 803,        // 800=过期, 801=等待扫码, 802=等待确认, 803=成功
    "cookie": "MUSIC_U=xxx; __csrf=xxx"  // 仅 code=803 时返回
}
```

> ⚠️ 这里的 `code` 语义与普通 API 不同，是扫码状态码。

#### `login` — 邮箱登录

**请求：**
```json
{
    "email": "user@example.com",
    "password": "password123"
}
```
或 MD5 方式：
```json
{
    "email": "user@example.com",
    "md5_password": "e10adc3949ba59abbe56e057f20f883e"
}
```

**响应：**
```json
{
    "code": 200,
    "cookie": "MUSIC_U=xxx; __csrf=xxx; __remember_me=true"
}
```

#### `login/cellphone` — 手机登录

**请求：**
```json
{
    "phone": "13800138000",
    "password": "password123"
}
```
或验证码方式：
```json
{
    "phone": "13800138000",
    "captcha": "123456"
}
```

**响应：** 同 `login`

#### `captcha/sent` — 发送验证码

**请求：**
```json
{
    "phone": "13800138000"
}
```

**响应：**
```json
{
    "code": 200
}
```

#### `register/anonimous` — 游客登录

**请求：** `{}`

**响应：**
```json
{
    "code": 200,
    "cookie": "xxx"
}
```

#### `login/status` — 登录状态

**请求：**
```json
{
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "data": {
        "profile": {
            "userId": 123456,
            "nickname": "User",
            "avatarUrl": "https://...",
            "signature": "个性签名",
            "follows": 100,
            "followeds": 200
        }
    }
}
```

#### `logout` — 登出

**请求：** `{}`

**响应：**
```json
{ "code": 200 }
```

#### `settings/schema` — 获取提供商设置的 UI Schema

**请求：** `{}`

**响应：**
```json
{
    "code": 200,
    "data": [
        {
            "key": "server_url",
            "name": "服务器地址",
            "type": "input",
            "description": "自定义后端服务器地址",
            "defaultValue": "https://api.example.com"
        },
        {
            "key": "enable_hifi",
            "name": "开启 Hi-Fi",
            "type": "switch",
            "defaultValue": false
        },
        {
            "key": "quality_limit",
            "name": "最高音质限制",
            "type": "select",
            "defaultValue": "lossless",
            "options": [
                { "label": "标准", "value": "standard" },
                { "label": "无损", "value": "lossless" },
                { "label": "Hi-Res", "value": "hires" }
            ]
        }
    ]
}
```

> **字段说明：**
> - `key`: 设置项的唯一标识。
> - `name`: 显示在界面的设置名称。
> - `type`: 支持 `input` (文本框), `switch` (开关), `select` (下拉单选)。
> - `description`: (可选) 设置项的描述。
> - `defaultValue`: (可选) 默认值。
> - `options`: 仅 `select` 类型需要，提供可选的下拉选项列表。

#### `settings/save` — 保存并同步用户设置到提供商

**请求：**
```json
{
    "settings": "{\"server_url\":\"https://myapi.com\",\"enable_hifi\":true,\"quality_limit\":\"hires\"}"
}
```
> `settings` 是一个包含了用户修改后的设置键值对的 JSON 字符串。

**响应：**
```json
{ "code": 200 }
```

#### `user/playlist` — 用户全部歌单

**请求：**
```json
{
    "uid": "123456",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "playlist": [
        {
            "id": 789,
            "name": "我喜欢的音乐",
            "coverImgUrl": "https://...",
            "trackCount": 50,
            "subscribed": false,
            "creator": {
                "nickname": "User",
                "userId": 123456
            },
            "description": "歌单描述"
        }
    ]
}
```

> ⚠️ 此接口返回用户创建和收藏的全部歌单。`subscribed` 字段表示是否为收藏的歌单（`true`=收藏，`false`=创建）。推荐使用 `user/playlist/create` 和 `user/playlist/collect` 分别获取。

#### `user/playlist/create` — 用户创建的歌单

**请求：**
```json
{
    "uid": "123456",
    "cookie": "MUSIC_U=xxx"
}
```

**响应（可能有两种格式）：**

格式 A（根级别）：
```json
{
    "code": 200,
    "playlist": [
        {
            "id": 789,
            "name": "我喜欢的音乐",
            "coverImgUrl": "https://...",
            "trackCount": 50,
            "subscribed": false,
            "creator": { "nickname": "User", "userId": 123456 }
        }
    ]
}
```

格式 B（嵌套在 data 中）：
```json
{
    "code": 200,
    "data": {
        "count": 3,
        "more": false,
        "playlist": [...]
    }
}
```

> Provider 实现时，建议统一使用格式 A（根级别的 `playlist` 数组），以确保最大兼容性。

#### `user/playlist/collect` — 用户收藏的歌单

**请求/响应：** 同 `user/playlist/create`，但返回的歌单均为当前用户收藏（`subscribed: true`）的歌单。

#### `user/detail` — 用户详情

**请求：**
```json
{
    "uid": "123456"
}
```

**响应：**
```json
{
    "code": 200,
    "profile": {
        "userId": 123456,
        "nickname": "User",
        "avatarUrl": "https://...",
        "signature": "...",
        "follows": 100,
        "followeds": 200
    }
}
```

#### `user/cloud` — 云盘歌曲

**请求：**
```json
{
    "limit": "100",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "data": [
        {
            "songId": "123",
            "songName": "My Upload Song",
            "artist": "Me",
            "album": "My Album",
            "dt": 240000
        }
    ]
}
```

#### `likelist` — 喜欢列表

**请求：**
```json
{
    "uid": "123456",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "ids": ["123", "456", "789"]
}
```

#### `like` — 喜欢/取消喜欢

**请求：**
```json
{
    "id": "123456",
    "like": "true",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{ "code": 200 }
```

#### `recommend/songs` — 每日推荐

**请求：**
```json
{
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "data": {
        "dailySongs": [
            {
                "id": 123,
                "name": "推荐歌曲",
                "ar": [{ "id": 1, "name": "歌手" }],
                "al": { "id": 1, "name": "专辑", "picUrl": "https://..." },
                "dt": 240000
            }
        ]
    }
}
```

#### `recommend/resource` — 推荐歌单

**请求：**
```json
{
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "recommend": [
        {
            "id": 789,
            "name": "推荐歌单",
            "picUrl": "https://...",
            "trackCount": 30,
            "creator": { "nickname": "..." }
        }
    ]
}
```

#### `recommend/songs/dislike` — 不喜欢

**请求：**
```json
{
    "id": "123456",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{ "code": 200 }
```

#### `playlist/detail` — 歌单详情

**请求：**
```json
{
    "id": "789"
}
```

**响应：**
```json
{
    "code": 200,
    "playlist": {
        "id": 789,
        "name": "歌单名",
        "coverImgUrl": "https://...",
        "trackCount": 50,
        "creator": { "nickname": "User", "userId": 111 },
        "description": "描述"
    }
}
```

#### `playlist/track/all` — 歌单全部曲目

**请求：**
```json
{
    "id": "789",
    "limit": "1000",
    "offset": "0"
}
```

**响应：**
```json
{
    "code": 200,
    "songs": [
        {
            "id": 123,
            "name": "歌曲",
            "ar": [{ "id": 1, "name": "歌手" }],
            "al": { "id": 1, "name": "专辑", "picUrl": "https://..." },
            "dt": 240000
        }
    ]
}
```

#### `playlist/tracks` — 添加/删除曲目

**请求（添加）：**
```json
{
    "op": "add",
    "pid": "789",
    "tracks": "123,456,789",
    "cookie": "MUSIC_U=xxx"
}
```

**请求（删除）：**
```json
{
    "op": "del",
    "pid": "789",
    "tracks": "123,456",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{ "code": 200 }
```

#### `playlist/create` — 创建歌单

**请求：**
```json
{
    "name": "新歌单",
    "privacy": "0",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "playlist": { "id": 999, "name": "新歌单" }
}
```

#### `playlist/delete` — 删除歌单

**请求：**
```json
{
    "id": "999",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{ "code": 200 }
```

#### `artist/detail` — 歌手详情

**请求：**
```json
{
    "id": "111"
}
```

**响应：**
```json
{
    "code": 200,
    "data": {
        "artist": {
            "id": 111,
            "name": "歌手名",
            "cover": "https://...",
            "picUrl": "https://...",
            "briefDesc": "简介..."
        },
        "user": { "followeds": 5000 }
    }
}
```

#### `artist/songs` — 歌手歌曲

**请求：**
```json
{
    "id": "111",
    "limit": "50"
}
```

**响应：**
```json
{
    "code": 200,
    "songs": [
        {
            "id": 123,
            "name": "歌曲",
            "ar": [{ "id": 111, "name": "歌手" }],
            "al": { "id": 1, "name": "专辑", "picUrl": "https://..." },
            "dt": 240000
        }
    ]
}
```

#### `artist/album` — 歌手专辑

**请求：**
```json
{
    "id": "111",
    "limit": "20"
}
```

**响应：**
```json
{
    "code": 200,
    "hotAlbums": [
        {
            "id": 222,
            "name": "专辑名",
            "picUrl": "https://...",
            "trackCount": 12
        }
    ]
}
```

#### `song/url/v1/302` — 302 重定向播放 URL

**请求：** 同 `song/url/v1`

**响应：** 同 `song/url/v1`

> 如果你的后端支持 302 重定向，CPPlayer 会优先使用此端点获取直链。

#### `song/download/url/v1` — 下载 URL

**请求：** 同 `song/url/v1`

**响应：** 同 `song/url/v1`

#### `personal_fm` — 私人 FM

**请求：**
```json
{
    "timestamp": "1687000000000",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "data": [
        {
            "id": 123,
            "name": "FM 歌曲",
            "ar": [{ "id": 1, "name": "歌手" }],
            "al": { "id": 1, "name": "专辑", "picUrl": "https://..." },
            "dt": 240000
        }
    ]
}
```

#### `playmode/intelligence/list` — 心动模式

**请求：**
```json
{
    "id": "123",
    "pid": "789",
    "sid": "123",
    "count": "20",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "data": [
        {
            "id": 456,
            "name": "...",
            "ar": [...],
            "al": {...}
        }
    ]
}
```

#### 评论类 API

**`comment/music` / `comment/playlist` / `comment/album` / `comment/mv` / `comment/dj` / `comment/video`**

**请求：**
```json
{
    "id": "123456",
    "limit": "20",
    "offset": "0",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "comments": [
        {
            "commentId": 789,
            "user": {
                "userId": 111,
                "nickname": "User",
                "avatarUrl": "https://..."
            },
            "content": "评论内容",
            "time": 1687000000000,
            "timeStr": "2023-06-17",
            "likedCount": 42,
            "liked": false,
            "replyCount": 3,
            "beReplied": [
                {
                    "user": { "userId": 222, "nickname": "Other" },
                    "content": "被回复的内容"
                }
            ]
        }
    ],
    "hotComments": [
        { "commentId": 100, "user": {...}, "content": "热评", "likedCount": 999 }
    ],
    "totalCount": 100,
    "hasMore": true
}
```

#### `comment/floor` — 楼层评论

**请求：**
```json
{
    "id": "123456",
    "parentCommentId": "789",
    "type": "0",
    "limit": "20",
    "time": "0",
    "cookie": "MUSIC_U=xxx"
}
```

**type 值：** `0`=音乐, `1`=MV, `2`=歌单, `3`=专辑, `4`=电台, `5`=视频, `6`=动态

**响应：**
```json
{
    "code": 200,
    "data": {
        "comments": [...],
        "totalCount": 50,
        "hasMore": true,
        "time": 1687000000000
    }
}
```

#### `comment/like` — 点赞评论

**请求：**
```json
{
    "id": "123456",
    "cid": "789",
    "t": "0",
    "type": "1",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{ "code": 200 }
```

#### `comment` — 发表/回复评论

**请求（发表）：**
```json
{
    "id": "123456",
    "type": "0",
    "content": "好听！",
    "op": "add",
    "cookie": "MUSIC_U=xxx"
}
```

**请求（回复）：**
```json
{
    "id": "123456",
    "type": "0",
    "content": "回复内容",
    "op": "reply",
    "commentId": "789",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{ "code": 200 }
```

#### `pl/count` — 未读消息数

**请求：**
```json
{
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "msg": 5
}
```

> `msg` 字段为未读数量（整数）。

#### `msg/recentcontact` — 最近联系人

**请求：**
```json
{
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "recentcontacts": [
        {
            "fromUser": {
                "userId": 111,
                "nickname": "User",
                "avatarUrl": "https://..."
            },
            "lastMsg": "{\"msg\":\"hello\"}",
            "lastMsgTime": 1687000000000,
            "newMsgCount": 2
        }
    ]
}
```

#### `msg/private` — 私信列表

**请求：**
```json
{
    "limit": "50",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "msgs": [
        {
            "fromUser": { "userId": 111, "nickname": "User", "avatarUrl": "https://..." },
            "msg": "{\"msg\":\"hello\"}",
            "time": 1687000000000
        }
    ]
}
```

#### `msg/private/history` — 私信历史

**请求：**
```json
{
    "uid": "111",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{
    "code": 200,
    "msgs": [
        {
            "fromUser": { "userId": 111, "nickname": "User", "avatarUrl": "https://..." },
            "msg": "{\"msg\":\"hello\"}",
            "time": 1687000000000
        }
    ]
}
```

#### `msg/private/mark/read` — 标记已读

**请求：**
```json
{
    "uid": "111",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{ "code": 200 }
```

#### `send/text` — 发送消息

**请求：**
```json
{
    "user_ids": "111",
    "msg": "hello",
    "cookie": "MUSIC_U=xxx"
}
```

**响应：**
```json
{ "code": 200 }
```

---

## 9. 不支持的功能处理

### 9.1 在 apiMap 中标记

```json
{
    "apiMap": {
        "cloudsearch": "search",
        "song/url/v1": "song/url",
        "like": "unsupported",
        "comment/music": "unsupported",
        "personal_fm": "unsupported"
    }
}
```

### 9.2 用户看到的效果

当用户尝试使用不支持的功能时，CPPlayer 会显示：**"该提供商不支持此功能"**

### 9.3 推荐的最低支持集

为了提供良好的用户体验，建议至少实现以下 API：

```
✅ 必须: song/url/v1, lyric/new, song/detail
⭐ 强烈推荐: cloudsearch, search/hot/detail, login/qr/*, user/*, playlist/*
💡 可选: comment/*, personal_fm, playmode/intelligence/list
```

---

## 10. 健康监控与兼容性检查

CPPlayer 内置了一套 **API 健康监控系统**，会自动对每次 API 调用进行兼容性检查。当你的 Provider 返回的响应不符合预期时，系统会记录警告并在"健康状态"界面展示。**请确保你的 Provider 通过所有检查以提供最佳用户体验。**

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

## 11. AMLL TTML 歌词平台检测

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

## 12. 模块更新

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

## 13. 模块打包与分发

### 13.1 目录结构

**HTTP Provider：**
```
my-provider.zip
  └── manifest.json
```

**Binary Provider：**
```
my-provider.zip
  ├── manifest.json
  └── my-backend           # ARM64 可执行文件
```

**JNI Provider：**
```
my-provider.zip
  ├── manifest.json
  └── libmybackend.so      # ARM64 共享库
```

### 13.2 打包命令

```bash
# HTTP Provider
zip -r my-http-provider.zip manifest.json

# Binary Provider
zip -r my-binary-provider.zip manifest.json my-backend

# JNI Provider
zip -r my-jni-provider.zip manifest.json libmybackend.so
```

### 13.3 导入 CPPlayer

在 Android 设备上打开 CPPlayer。前往 **设置 → 模块管理 → 导入新模块 (.zip)** → 选择你的 `.zip` 文件。

### 13.4 分发方式

1. **直接分享 .zip 文件** — 用户在 CPPlayer 设置中导入
2. **GitHub Releases** — 发布到 GitHub，用户下载后导入
3. **自建模块仓库** — 高级方式，CPPlayer 未来可能支持在线模块市场

---

## 14. 调试与测试

### 14.1 使用 curl 测试 HTTP Provider

```bash
# 测试搜索
curl -X POST http://localhost:8080/search \
  -H "Content-Type: application/json" \
  -d '{"keywords": "hello", "type": "1"}'

# 测试播放 URL
curl -X POST http://localhost:8080/song/url \
  -H "Content-Type: application/json" \
  -d '{"id": "123456", "level": "standard"}'

# 测试歌词
curl -X POST http://localhost:8080/lyric \
  -H "Content-Type: application/json" \
  -d '{"id": "123456"}'
```

### 14.2 在 CPPlayer 中查看日志

CPPlayer 内置日志查看器：**设置 → 日志查看器**

关键日志标签：
- `CPDS` — DataSource 日志（URL 解析过程）
- `ModuleManager` — 模块加载日志
- `ProviderManager` — Provider 切换日志
- `BinaryProvider` / `JniProvider` / `HttpProvider` — 各 Provider 通信日志

### 14.3 健康状态测试

导入 Provider 后，前往 **设置 → 调试 → 健康状态 → 测试** Tab，逐一测试 API 方法，确认无兼容性警告。在"概览"Tab 查看健康评分，确保达到 80 分以上。

### 14.4 常见调试场景

**搜索无结果：**
1. 检查 `cloudsearch` 端点是否正确响应
2. 确认响应格式包含 `result.songs` 数组
3. 检查歌曲对象是否包含 `id`, `name`, `ar`, `al` 字段

**无法播放：**
1. 检查 `song/url/v1` 返回的 `url` 是否为有效 HTTP 链接
2. 确认 `data` 是数组格式
3. 检查 URL 是否需要特殊 header（CPPlayer 会添加 `User-Agent` 和 `Referer`）

**歌词不显示：**
1. 检查 `lyric/new` 返回的 `lrc.lyric` 是否为 LRC 格式
2. 确认时间戳格式为 `[mm:ss.xx]`

---

## 15. 常见问题

### Q: 我的后端和 NeteaseCloudMusicApi 完全兼容，需要设置 apiMap 吗？

**A:** 不需要。如果不设置 `apiMap`，CPPlayer 会直接使用内部方法名（如 `cloudsearch`、`song/url/v1`）作为端点路径，与 NeteaseCloudMusicApi 完全一致。

### Q: HTTP Provider 需要 CPPlayer 启动吗？

**A:** 是的，HTTP Provider 需要你自行确保服务运行。CPPlayer 不会自动启动 HTTP 服务。

### Q: Binary Provider 的端口冲突怎么办？

**A:** CPPlayer 默认使用端口 3000。如果冲突，可以在 CPPlayer 设置中修改默认端口。

### Q: 如何支持多音质？

**A:** `song/url/v1` 的 `level` 参数会告诉你要请求的音质等级。你的后端应该根据这个参数返回对应质量的音频 URL。如果某个音质不可用，返回 `url: null`。

### Q: 如何处理登录 cookie？

**A:**
1. 登录 API（`login`、`login/cellphone`、`login/qr/check`）需要在响应中返回 `cookie` 字段
2. CPPlayer 会保存这个 cookie
3. 后续需要登录的 API 调用会在请求参数中携带 `cookie` 字段
4. 你的后端需要使用这个 cookie 向上游服务认证

### Q: 我的 Provider 需要支持所有音质等级吗？

**A:** 不需要。对于不支持的音质，返回 `url: null` 即可。CPPlayer 会自动降级或提示用户。
