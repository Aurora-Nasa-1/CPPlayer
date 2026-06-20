# CPPlayer 音乐 API 开发文档

> **版本：** 1.0.0 · **最后更新：** 2026-06-17

---

## 目录

1. [架构概览](#1-架构概览)
2. [API 完整返回格式](#2-api-完整返回格式)
3. [API 方法总览](#3-api-方法总览)
4. [Provider 接入指南（面向第三方开发者）](#4-provider-接入指南面向第三方开发者)
5. [apiMap 映射策略](#5-apimap-映射策略)
6. [不支持回退的 API](#6-不支持回退的-api)
7. [在 Kotlin 中调用 API](#7-在-kotlin-中调用-api)
8. [新增 API 端点流程](#8-新增-api-端点流程)
9. [错误处理规范](#9-错误处理规范)
10. [代码规范与禁忌](#10-代码规范与禁忌)

---

## 1. 架构概览

```
┌──────────────────────────────────────────────────────────────┐
│                      UI Layer (Jetpack Compose)               │
├──────────────────────────────────────────────────────────────┤
│                      ViewModel Layer                          │
│   BaseViewModel.api → MusicApiService (类型安全方法)           │
├──────────────────────────────────────────────────────────────┤
│                     Repository Layer                           │
│   PlaybackRepository · (未来: UserRepository, SearchRepository)│
│   职责: JSON → 领域模型转换                                    │
├──────────────────────────────────────────────────────────────┤
│                MusicApiService（统一入口，唯一合法调用点）       │
│   MusicApiServiceFactory.instance                             │
│   职责: 路由 · cookie 注入 · 错误包装 · 多 Provider 容灾       │
├──────────────────────────────────────────────────────────────┤
│                   Provider Layer（传输层）                      │
│   ProviderManager → BackendProvider                            │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐                   │
│   │ JniProvider│ │BinaryProvider│ │HttpProvider│               │
│   └──────────┘  └──────────┘  └──────────┘                   │
│   ModuleManager → 模块加载 (manifest.json → Provider 实例)     │
└──────────────────────────────────────────────────────────────┘
```

**核心调用链路：**

```
ViewModel → [Repository] → MusicApiService → ProviderManager → BackendProvider.callApi()
```

> ⚠️ **铁律：** 所有 API 调用必须经过 `MusicApiService`，禁止任何地方直接调用 `ProviderManager.callApi()` 或 `BackendProvider.callApi()`。

---

## 2. API 完整返回格式

### 2.1 统一响应结构

所有后端 Provider 返回的 JSON 必须遵循以下结构：

```jsonc
{
    "code": 200,          // 状态码 (必填)
    "msg": "success",     // 状态消息 (可选)
    // --- 业务数据 (按 API 不同而不同) ---
    "data": { ... },      // 单层数据
    "result": { ... },    // 搜索类结果
    "songs": [ ... ],     // 歌曲列表
    "playlist": { ... },  // 歌单
    "cookie": "..."       // 登录类返回
}
```

### 2.2 各 API 返回格式详解

#### 认证类 (Auth)

**`AUTH_QR_KEY` — login/qr/key**
```jsonc
{
    "code": 200,
    "data": {
        "unikey": "xxx"   // 二维码 key，后续所有扫码操作都需要
    }
}
```

**`AUTH_QR_CREATE` — login/qr/create**
```jsonc
{
    "code": 200,
    "data": {
        "qrimg": "data:image/png;base64,...",  // base64 编码的二维码图片
        "qrurl": "https://..."                 // 二维码对应的 URL
    }
}
```

**`AUTH_QR_CHECK` — login/qr/check**
```jsonc
// code 字段的含义与其他 API 不同，这里是扫码状态码
{
    "code": 803,       // 800=过期, 801=等待扫码, 802=等待确认, 803=成功
    "cookie": "MUSIC_U=xxx; __csrf=xxx"  // 仅 code=803 时存在
}
```

**`AUTH_LOGIN` / `AUTH_LOGIN_PHONE` — login / login/cellphone**
```jsonc
{
    "code": 200,
    "cookie": "MUSIC_U=xxx; __csrf=xxx",  // 登录凭证
    "profile": {                           // 部分后端返回
        "userId": 123456,
        "nickname": "User",
        "avatarUrl": "https://..."
    }
}
```

**`AUTH_ANONYMOUS` — register/anonimous**
```jsonc
{
    "code": 200,
    "cookie": "xxx"   // 游客 cookie
}
```

**`AUTH_LOGIN_STATUS` — login/status**
```jsonc
{
    "code": 200,
    "data": {
        "profile": {
            "userId": 123456,
            "nickname": "User",
            "avatarUrl": "https://...",
            "signature": "...",
            "follows": 100,
            "followeds": 200
        }
    }
}
```

**`AUTH_LOGOUT` — logout**
```jsonc
{ "code": 200 }
```

**`AUTH_CAPTCHA_SENT` — captcha/sent**
```jsonc
{ "code": 200, "data": null }
```

---

#### 用户类 (User)

**`USER_PLAYLIST` — user/playlist**
```jsonc
{
    "code": 200,
    "playlist": [
        {
            "id": 123456789,
            "name": "我喜欢的音乐",
            "coverImgUrl": "https://...",
            "trackCount": 50,
            "creator": { "nickname": "User" },
            "description": "..."
        }
    ]
}
```

**`USER_DETAIL` — user/detail**
```jsonc
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

**`USER_CLOUD` — user/cloud**
```jsonc
{
    "code": 200,
    "data": [
        {
            "songId": "123",
            "songName": "My Song",
            "artist": "Artist",
            "album": "Album",
            "dt": 240000
        }
    ]
}
```

**`USER_LIKE_LIST` — likelist**
```jsonc
{
    "code": 200,
    "ids": ["123", "456", "789"]   // 喜欢的歌曲 ID 列表
}
```

**`USER_LIKE` — like**
```jsonc
// 请求: { "id": "song_id", "like": "true"/"false" }
{ "code": 200 }
```

**`USER_RECOMMEND_SONGS` — recommend/songs**
```jsonc
{
    "code": 200,
    "data": {
        "dailySongs": [
            {
                "id": 123,
                "name": "Song Name",
                "ar": [{ "id": 1, "name": "Artist" }],
                "al": { "id": 1, "name": "Album", "picUrl": "https://..." },
                "dt": 240000
            }
        ]
    }
}
```

**`USER_RECOMMEND_RESOURCE` — recommend/resource**
```jsonc
{
    "code": 200,
    "recommend": [
        {
            "id": 123456,
            "name": "推荐歌单",
            "picUrl": "https://...",
            "trackCount": 30,
            "creator": { "nickname": "..." }
        }
    ]
}
```

**`USER_DISLIKE_SONG` — recommend/songs/dislike**
```jsonc
{ "code": 200 }
```

---

#### 歌单类 (Playlist)

**`PLAYLIST_DETAIL` — playlist/detail**
```jsonc
{
    "code": 200,
    "playlist": {
        "id": 123456,
        "name": "歌单名",
        "coverImgUrl": "https://...",
        "trackCount": 50,
        "creator": { "nickname": "User", "userId": 111 },
        "description": "歌单描述"
    }
}
```

**`PLAYLIST_TRACK_ALL` — playlist/track/all**
```jsonc
{
    "code": 200,
    "songs": [
        {
            "id": 123,
            "name": "Song Name",
            "ar": [{ "id": 1, "name": "Artist" }],
            "al": { "id": 1, "name": "Album", "picUrl": "https://..." },
            "dt": 240000
        }
    ]
}
```

**`PLAYLIST_TRACKS` — playlist/tracks**
```jsonc
// 请求: { "op": "add"/"del", "pid": "123", "tracks": "id1,id2,id3" }
{ "code": 200 }
```

**`PLAYLIST_CREATE` — playlist/create**
```jsonc
{
    "code": 200,
    "playlist": { "id": 999, "name": "New Playlist" }
}
```

**`PLAYLIST_DELETE` — playlist/delete**
```jsonc
{ "code": 200 }
```

---

#### 歌手类 (Artist)

**`ARTIST_DETAIL` — artist/detail**
```jsonc
{
    "code": 200,
    "data": {
        "artist": {
            "id": 111,
            "name": "Artist Name",
            "cover": "https://...",
            "picUrl": "https://...",
            "briefDesc": "简介..."
        },
        "user": { "followeds": 5000 }
    }
}
```

**`ARTIST_SONGS` — artist/songs**
```jsonc
{
    "code": 200,
    "songs": [
        {
            "id": 123,
            "name": "Song",
            "ar": [{ "id": 111, "name": "Artist" }],
            "al": { "id": 1, "name": "Album", "picUrl": "https://..." },
            "dt": 240000
        }
    ]
}
```

**`ARTIST_ALBUM` — artist/album**
```jsonc
{
    "code": 200,
    "hotAlbums": [
        {
            "id": 222,
            "name": "Album Name",
            "picUrl": "https://...",
            "trackCount": 12
        }
    ]
}
```

---

#### 搜索类 (Search)

**`SEARCH_CLOUD` — cloudsearch**
```jsonc
// 请求: { "keywords": "xxx", "type": "1" }
// type: 1=单曲, 100=歌手, 10=专辑, 1000=歌单, 1014=视频
{
    "code": 200,
    "result": {
        // type=1 时:
        "songs": [
            {
                "id": 123,
                "name": "Song",
                "ar": [{ "id": 1, "name": "Artist" }],
                "al": { "id": 1, "name": "Album", "picUrl": "https://..." },
                "dt": 240000
            }
        ],
        // type=1000 时:
        "playlists": [
            {
                "id": 456,
                "name": "Playlist",
                "coverImgUrl": "https://...",
                "trackCount": 30
            }
        ]
    }
}
```

**`SEARCH_HOT_DETAIL` — search/hot/detail**
```jsonc
{
    "code": 200,
    "data": [
        { "searchWord": "关键词", "content": "描述" }
    ]
}
```

**`SEARCH_SUGGEST` — search/suggest**
```jsonc
{
    "code": 200,
    "result": {
        "allMatch": [
            { "keyword": "建议词" }
        ]
    }
}
```

---

#### 播放类 (Playback)

**`SONG_URL_V1_302` — song/url/v1/302 ⭐ 优先使用**
```jsonc
// ⚠️ 此 API 不支持回退，如果返回无 URL，MusicApiService 不会自动降级
// 请求: { "id": "song_id", "level": "standard" }
{
    "code": 200,
    "data": [
        {
            "id": 123,
            "url": "https://...",
            "br": 320000,
            "type": "mp3"
        }
    ],
    // MusicApiService 会自动填充 redirectUrl
    "redirectUrl": "https://..."  // 如果 Provider 支持 302 重定向
}
```

**`SONG_URL_V1` — song/url/v1 (降级使用)**
```jsonc
// 仅在 302 版本无 URL 时自动降级使用
{
    "code": 200,
    "data": [
        {
            "id": 123,
            "url": "https://...",
            "br": 320000
        }
    ]
}
```

**`SONG_DOWNLOAD_URL` — song/download/url/v1 ⭐ 下载专用**
```jsonc
// ⚠️ 此 API 不支持回退
{
    "code": 200,
    "data": {
        "url": "https://...",
        "br": 320000
    }
}
```

**`SONG_DETAIL` — song/detail**
```jsonc
// 请求: { "ids": "123,456,789" }
{
    "code": 200,
    "songs": [
        {
            "id": 123,
            "name": "Song Name",
            "ar": [{ "id": 1, "name": "Artist" }],
            "al": { "id": 1, "name": "Album", "picUrl": "https://..." },
            "dt": 240000
        }
    ]
}
```

**`PERSONAL_FM` — personal_fm**
```jsonc
{
    "code": 200,
    "data": [
        {
            "id": 123,
            "name": "FM Song",
            "ar": [{ "id": 1, "name": "Artist" }],
            "al": { "id": 1, "name": "Album", "picUrl": "https://..." },
            "dt": 240000
        }
    ]
}
```

**`INTELLIGENCE_LIST` — playmode/intelligence/list**
```jsonc
// 请求: { "id": "song_id", "pid": "playlist_id", "sid": "song_id", "count": "20" }
{
    "code": 200,
    "data": [
        { "id": 123, "name": "...", "ar": [...], "al": {...} }
    ]
}
```

**`LYRIC_NEW` — lyric/new**
```jsonc
// 请求: { "id": "song_id" }
{
    "code": 200,
    "lrc": { "lyric": "[00:00.00]歌词文本\n[00:05.00]第二行\n" },
    "yrc": { "lyric": "[00:00.00](0,500,0)逐(500,300,0)字(800,400,0)\n" },
    "tlyric": { "lyric": "[00:00.00]Translation\n" }
}
```

> `yrc` 优先于 `lrc`。`tlyric` 为翻译歌词。三者格式见 [`LyricService`](../app/src/main/java/cp/player/service/LyricService.kt)。

---

#### 社交类 (Social)

**`COMMENT_MUSIC` / `COMMENT_PLAYLIST` / ... — comment/{type}**
```jsonc
// 请求: { "id": "resource_id", "limit": "20", "offset": "0" }
{
    "code": 200,
    "comments": [
        {
            "commentId": 789,
            "user": { "userId": 111, "nickname": "User", "avatarUrl": "https://..." },
            "content": "评论内容",
            "time": 1687000000000,
            "timeStr": "2023-06-17",
            "likedCount": 42,
            "liked": false,
            "replyCount": 3,
            "beReplied": [
                { "user": { "userId": 222, "nickname": "Other" }, "content": "原文" }
            ]
        }
    ],
    "hotComments": [...],    // 热评 (仅 page=1)
    "totalCount": 100,
    "hasMore": true
}
```

**`COMMENT_FLOOR` — comment/floor**
```jsonc
{
    "code": 200,
    "data": {
        "comments": [...],
        "totalCount": 50,
        "hasMore": true,
        "time": 1687000000000  // 翻页游标
    }
}
```

**`COMMENT_LIKE` — comment/like**
```jsonc
// 请求: { "id": "resource_id", "cid": "comment_id", "t": "0", "type": "1"(赞)/"0"(取消) }
{ "code": 200 }
```

**`COMMENT_POST` — comment**
```jsonc
// 请求: { "id": "resource_id", "type": "0", "content": "...", "op": "add"/"reply", "commentId": "..." (可选) }
{ "code": 200 }
```

**`MESSAGE_UNREAD_COUNT` — pl/count**
```jsonc
{ "code": 200, "msg": 5 }  // msg 字段为未读数
```

**`MESSAGE_RECENT_CONTACT` — msg/recentcontact**
```jsonc
{
    "code": 200,
    "recentcontacts": [
        {
            "fromUser": { "userId": 111, "nickname": "User", "avatarUrl": "https://..." },
            "lastMsg": "{\"msg\":\"hello\"}",
            "lastMsgTime": 1687000000000,
            "newMsgCount": 2
        }
    ]
}
```

**`MESSAGE_PRIVATE` — msg/private**
```jsonc
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

**`MESSAGE_PRIVATE_HISTORY` — msg/private/history**
```jsonc
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

**`MESSAGE_MARK_READ` — msg/private/mark/read**
```jsonc
{ "code": 200 }
```

**`MESSAGE_SEND_TEXT` — send/text**
```jsonc
// 请求: { "user_ids": "111", "msg": "hello" }
{ "code": 200 }
```

---

## 3. API 方法总览

所有常量定义在 [`MusicApiMethod.kt`](../app/src/main/java/cp/player/api/MusicApiMethod.kt) 中。

| 常量 | 原始路径 | 说明 | 支持回退 |
|------|----------|------|----------|
| **认证** | | | |
| `AUTH_QR_KEY` | `login/qr/key` | 获取扫码 key | ✅ |
| `AUTH_QR_CREATE` | `login/qr/create` | 创建二维码 | ✅ |
| `AUTH_QR_CHECK` | `login/qr/check` | 检查扫码状态 | ✅ |
| `AUTH_LOGIN` | `login` | 邮箱登录 | ✅ |
| `AUTH_LOGIN_PHONE` | `login/cellphone` | 手机登录 | ✅ |
| `AUTH_CAPTCHA_SENT` | `captcha/sent` | 发送验证码 | ✅ |
| `AUTH_LOGOUT` | `logout` | 登出 | ✅ |
| `AUTH_ANONYMOUS` | `register/anonimous` | 游客登录 | ✅ |
| `AUTH_LOGIN_STATUS` | `login/status` | 登录状态 | ✅ |
| **用户** | | | |
| `USER_PLAYLIST` | `user/playlist` | 用户歌单 | ✅ |
| `USER_DETAIL` | `user/detail` | 用户详情 | ✅ |
| `USER_CLOUD` | `user/cloud` | 云盘歌曲 | ✅ |
| `USER_LIKE_LIST` | `likelist` | 喜欢列表 | ✅ |
| `USER_LIKE` | `like` | 喜欢/取消 | ✅ |
| `USER_RECOMMEND_SONGS` | `recommend/songs` | 每日推荐 | ✅ |
| `USER_RECOMMEND_RESOURCE` | `recommend/resource` | 推荐歌单 | ✅ |
| `USER_DISLIKE_SONG` | `recommend/songs/dislike` | 不喜欢 | ✅ |
| **歌单** | | | |
| `PLAYLIST_DETAIL` | `playlist/detail` | 歌单详情 | ✅ |
| `PLAYLIST_TRACK_ALL` | `playlist/track/all` | 歌单全部曲目 | ✅ |
| `PLAYLIST_TRACKS` | `playlist/tracks` | 添加/删除曲目 | ✅ |
| `PLAYLIST_CREATE` | `playlist/create` | 创建歌单 | ✅ |
| `PLAYLIST_DELETE` | `playlist/delete` | 删除歌单 | ✅ |
| **歌手** | | | |
| `ARTIST_DETAIL` | `artist/detail` | 歌手详情 | ✅ |
| `ARTIST_SONGS` | `artist/songs` | 歌手歌曲 | ✅ |
| `ARTIST_ALBUM` | `artist/album` | 歌手专辑 | ✅ |
| **搜索** | | | |
| `SEARCH_CLOUD` | `cloudsearch` | 搜索 | ✅ |
| `SEARCH_HOT_DETAIL` | `search/hot/detail` | 热搜 | ✅ |
| `SEARCH_SUGGEST` | `search/suggest` | 搜索建议 | ✅ |
| **播放** | | | |
| `SONG_URL_V1_302` | `song/url/v1/302` | 播放 URL (302) | ❌ |
| `SONG_URL_V1` | `song/url/v1` | 播放 URL (降级) | ✅ (作为 302 的降级) |
| `SONG_DOWNLOAD_URL` | `song/download/url/v1` | 下载 URL | ❌ |
| `SONG_DETAIL` | `song/detail` | 歌曲详情 | ✅ |
| `PERSONAL_FM` | `personal_fm` | 私人 FM | ✅ |
| `INTELLIGENCE_LIST` | `playmode/intelligence/list` | 心动模式 | ✅ |
| `LYRIC_NEW` | `lyric/new` | 歌词 | ✅ |
| **社交** | | | |
| `COMMENT_MUSIC` | `comment/music` | 音乐评论 | ✅ |
| `COMMENT_PLAYLIST` | `comment/playlist` | 歌单评论 | ✅ |
| `COMMENT_ALBUM` | `comment/album` | 专辑评论 | ✅ |
| `COMMENT_MV` | `comment/mv` | MV 评论 | ✅ |
| `COMMENT_DJ` | `comment/dj` | 电台评论 | ✅ |
| `COMMENT_VIDEO` | `comment/video` | 视频评论 | ✅ |
| `COMMENT_FLOOR` | `comment/floor` | 楼层评论 | ✅ |
| `COMMENT_LIKE` | `comment/like` | 点赞评论 | ✅ |
| `COMMENT_POST` | `comment` | 发表评论 | ✅ |
| `MESSAGE_UNREAD_COUNT` | `pl/count` | 未读数 | ✅ |
| `MESSAGE_RECENT_CONTACT` | `msg/recentcontact` | 最近联系人 | ✅ |
| `MESSAGE_PRIVATE` | `msg/private` | 私信列表 | ✅ |
| `MESSAGE_PRIVATE_HISTORY` | `msg/private/history` | 私信历史 | ✅ |
| `MESSAGE_MARK_READ` | `msg/private/mark/read` | 标记已读 | ✅ |
| `MESSAGE_SEND_TEXT` | `send/text` | 发送消息 | ✅ |

---

## 4. Provider 接入指南（面向第三方开发者）

### 4.1 什么是 Provider？

Provider 是 CPPlayer 与音乐后端之间的**传输适配器**。你的 Provider 负责：

1. 接收标准化的 API 方法名 + 参数
2. 将其转发给你的后端服务
3. 返回标准化的 JSON 响应

### 4.2 三种接入方式

| 方式 | 适合场景 | 传输协议 |
|------|----------|----------|
| **HTTP Provider** | 已有 HTTP API 服务 | HTTP REST |
| **Binary Provider** | 可执行文件形式的后端 | 启动子进程 + HTTP |
| **JNI Provider** | 性能敏感的本地库 | JNI 直接调用 |

### 4.3 方式一：HTTP Provider（最简单）

你的后端需要是一个 HTTP 服务，接收 POST 请求：

```
POST http://your-server:port/{method}
Content-Type: application/json

{"id": "123", "level": "standard", "cookie": "..."}
```

返回标准 JSON 响应。

**创建模块包：**

```
my-http-provider/
  manifest.json
```

**manifest.json：**
```json
{
    "id": "my-http-backend",
    "name": "My HTTP Music Backend",
    "version": "1.0.0",
    "type": "http",
    "entryPoint": "http://your-server:port",
    "apiMap": {
        "cloudsearch": "search",
        "song/url/v1": "song/url",
        "song/url/v1/302": "song/url/302",
        "song/download/url/v1": "song/download",
        "lyric/new": "lyric",
        "login/qr/key": "auth/qr/key",
        "login/qr/create": "auth/qr/create",
        "login/qr/check": "auth/qr/check",
        "login": "auth/login",
        "login/cellphone": "auth/phone",
        "logout": "auth/logout",
        "register/anonimous": "auth/anonymous",
        "login/status": "auth/status",
        "user/playlist": "user/playlists",
        "user/detail": "user/detail",
        "user/cloud": "user/cloud",
        "likelist": "user/likes",
        "like": "song/like",
        "recommend/songs": "recommend/songs",
        "recommend/resource": "recommend/playlists",
        "recommend/songs/dislike": "recommend/dislike",
        "playlist/detail": "playlist/detail",
        "playlist/track/all": "playlist/tracks",
        "playlist/tracks": "playlist/modify",
        "playlist/create": "playlist/create",
        "playlist/delete": "playlist/delete",
        "artist/detail": "artist/detail",
        "artist/songs": "artist/songs",
        "artist/album": "artist/albums",
        "search/hot/detail": "search/hot",
        "search/suggest": "search/suggest",
        "song/detail": "song/detail",
        "personal_fm": "fm",
        "playmode/intelligence/list": "intelligence",
        "comment/music": "comment/music",
        "comment/playlist": "comment/playlist",
        "comment/album": "comment/album",
        "comment/mv": "comment/mv",
        "comment/dj": "comment/dj",
        "comment/video": "comment/video",
        "comment/floor": "comment/floor",
        "comment/like": "comment/like",
        "comment": "comment/post",
        "pl/count": "message/unread",
        "msg/recentcontact": "message/contacts",
        "msg/private": "message/list",
        "msg/private/history": "message/history",
        "msg/private/mark/read": "message/read",
        "send/text": "message/send"
    }
}
```

打包为 `.zip`（包含 `manifest.json` 和模块文件），通过应用设置 → 模块管理 → 导入。

### 4.4 方式二：Binary Provider

你的后端是一个可执行文件，CPPlayer 会启动它作为子进程：

```bash
./my-backend --port 3000
```

**manifest.json：**
```json
{
    "id": "my-binary-backend",
    "name": "My Binary Backend",
    "version": "1.0.0",
    "type": "binary",
    "entryPoint": "my-backend",
    "apiMap": { ... }
}
```

模块包中需包含可执行文件：

```
my-binary-provider/
  manifest.json
  my-backend          # 可执行文件 (ARM64/x86_64)
```

### 4.5 方式三：JNI Provider

你的后端是一个 `.so` 共享库，CPPlayer 通过 JNI 调用：

**manifest.json：**
```json
{
    "id": "my-jni-backend",
    "name": "My JNI Backend",
    "version": "1.0.0",
    "type": "jni",
    "entryPoint": "libmybackend.so",
    "apiMap": { ... }
}
```

**JNI 接口要求：** 你的 `.so` 必须导出以下函数：

```c
// 启动本地服务
void startNativeServer(const char* host, int port);

// API 调用 (核心)
// paramsJson: JSON 字符串参数
// 返回值: JSON 字符串响应
const char* nativeCallApi(const char* method, const char* paramsJson);

// 音频分析 (可选)
const char* analyzeAudioFile(const char* path);
```

### 4.6 Provider 必须实现的 API

**最低要求（核心功能）：**

| API | 说明 | 必须 |
|-----|------|------|
| `song/url/v1` | 获取播放 URL | ✅ |
| `song/url/v1/302` | 302 重定向播放 URL | 推荐 |
| `song/download/url/v1` | 获取下载 URL | 推荐 |
| `lyric/new` | 获取歌词 | ✅ |
| `song/detail` | 歌曲详情 | 推荐 |
| `cloudsearch` | 搜索 | 推荐 |

**认证相关（如需用户登录功能）：**

| API | 说明 | 必须 |
|-----|------|------|
| `login/qr/key` | 扫码登录 | 可选 |
| `login/qr/create` | 创建二维码 | 可选 |
| `login/qr/check` | 检查扫码 | 可选 |
| `login` | 邮箱登录 | 可选 |
| `login/cellphone` | 手机登录 | 可选 |
| `register/anonimous` | 游客登录 | 可选 |
| `login/status` | 登录状态 | 可选 |

**不支持的 API：** 在 `apiMap` 中映射为 `"unsupported"`：
```json
{
    "apiMap": {
        "like": "unsupported",
        "comment/music": "unsupported"
    }
}
```

调用时会返回：`{"code": -1, "msg": "该提供商不支持此功能"}`

---

## 5. apiMap 映射策略

### 5.1 工作原理

```
应用内部方法名 ──apiMap──→ Provider 实际端点
```

```kotlin
// ProviderManager 中的映射逻辑:
val mappedMethod = provider.apiMap?.get(method) ?: method
// 如果映射为 "unsupported"，直接返回 {"code": -1, "msg": "该提供商不支持此功能"}
// 如果没有映射，直接使用原始方法名
```

### 5.2 映射示例

**场景：** 你的后端使用不同的路径命名

```json
{
    "apiMap": {
        "cloudsearch": "api/v2/search",        // 搜索
        "song/url/v1": "api/v2/music/play",    // 播放
        "lyric/new": "api/v2/music/lyrics",    // 歌词
        "login/qr/key": "api/v2/auth/qr",      // 扫码
        "like": "unsupported"                   // 不支持点赞
    }
}
```

### 5.3 完整映射参考

如果你的后端完全兼容 NeteaseCloudMusicApi，可以不设置 `apiMap`（所有方法名直接透传）。

如果你的后端路径不同，请参考上方 [4.3 节](#43-方式一http-provider最简单) 的完整映射示例。

---

## 6. 不支持回退的 API

以下 API 在 `MusicApiServiceImpl` 中**不会自动降级到其他 Provider**：

| API | 原因 |
|-----|------|
| `SONG_URL_V1_302` (song/url/v1/302) | 这是播放 URL 解析的首选方法。如果失败，会自动降级到 `SONG_URL_V1`，但**不会切换 Provider** |
| `SONG_DOWNLOAD_URL` (song/download/url/v1) | 下载 URL 解析。失败后由 `CPDownloadManager` 降级到 `SONG_URL_V1` |

> **注意：** 在 `BackendDataSource` 中，URL 解析场景使用 `callWithAllProviders()` 进行**多 Provider 容灾**，会依次尝试所有已加载的 Provider。这是唯一允许遍历 Provider 的场景。

### 不支持回退的 API 在 Kotlin 中的表现

```kotlin
// MusicApiServiceImpl 中:
override fun getSongUrl(songId: String, level: String): JsonObject {
    val result = callApi(MusicApiMethod.SONG_URL_V1_302, params)
    // ⚠️ 不会自动降级到其他 Provider
    // 但会确保 result 中有 redirectUrl 字段
    return result
}

override fun getSongDownloadUrl(songId: String, level: String): JsonObject {
    // ⚠️ 不会自动降级，直接返回结果
    return callApi(MusicApiMethod.SONG_DOWNLOAD_URL, params)
}
```

**调用方需要自行处理降级：**

```kotlin
// CPDownloadManager 中的降级逻辑:
var url: String? = null
val downloadBody = api.getSongDownloadUrl(song.id, quality)  // 优先尝试下载 URL
url = JsonUtils.findUrl(downloadBody)

if (url == null) {
    val fallbackBody = api.getSongUrlFallback(song.id, quality)  // 降级到普通播放 URL
    url = JsonUtils.findUrl(fallbackBody)
}
```

---

## 7. 在 Kotlin 中调用 API

### 7.1 在 ViewModel 中（推荐）

```kotlin
class MyViewModel(application: Application) : BaseViewModel(application) {

    // ✅ 类型安全方法（推荐）
    fun searchSongs(keyword: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                val body = withContext(Dispatchers.IO) {
                    api.search(keyword, type = 1)  // 自动注入 cookie
                }
                val songs = body.get("result")?.asJsonObject
                    ?.get("songs")?.asJsonArray
                    ?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
                // 更新状态...
            } finally {
                isLoading = false
            }
        }
    }

    // ✅ 通用调用（当 MusicApiMethod 中无对应常量时）
    fun callNewApi() {
        viewModelScope.launch(Dispatchers.IO) {
            val body = api.callApi("new/method", mapOf("param" to "value"))
        }
    }

    // ✅ 通过 BaseViewModel.callApi()（向后兼容）
    fun legacyCall() {
        viewModelScope.launch(Dispatchers.IO) {
            val body = callApi("old/method", mapOf("key" to "value"))
        }
    }
}
```

### 7.2 在非 ViewModel 中

```kotlin
val api = MusicApiServiceFactory.instance

// 类型安全
val body = api.getLyric(songId)
val body = api.likeSong(songId, like = true)
val body = api.getSongUrl(songId, level = "exhigh")

// 通用
val body = api.callApi(MusicApiMethod.LYRIC_NEW, mapOf("id" to songId))
```

### 7.3 在 Repository 中

```kotlin
class MyRepository(
    private val api: MusicApiService = MusicApiServiceFactory.instance
) {
    fun getSongDetail(songId: String): Song? {
        val body = api.getSongDetail(listOf(songId))
        return body.get("songs")?.asJsonArray
            ?.firstOrNull()?.let { JsonUtils.parseSong(it) }
    }
}
```

### 7.4 在 Service / DataSource 中

```kotlin
// Service 中直接使用
val api = MusicApiServiceFactory.instance
val body = api.likeSong(mediaId, isLiked)

// DataSource 中使用多 Provider 容灾
val api = MusicApiServiceFactory.instance
val url = api.callWithAllProviders(MusicApiMethod.SONG_URL_V1, params) { body ->
    val url = JsonUtils.findUrl(body)
    if (!url.isNullOrEmpty() && url.startsWith("http")) url else null
}
```

---

## 8. 新增 API 端点流程

### 步骤 1：在 MusicApiMethod 中添加常量

```kotlin
// app/src/main/java/cp/player/api/MusicApiMethod.kt
object MusicApiMethod {
    // ... 现有常量

    /** 新功能 API */
    const val NEW_FEATURE = "new/feature/api"
}
```

### 步骤 2：在 MusicApiService 接口中添加方法

```kotlin
// app/src/main/java/cp/player/api/MusicApiService.kt
interface MusicApiService {
    /** 新功能 - 获取xxx数据 */
    fun getNewFeature(param: String): JsonObject
}
```

### 步骤 3：在 MusicApiServiceImpl 中实现

```kotlin
// app/src/main/java/cp/player/api/MusicApiServiceImpl.kt
class MusicApiServiceImpl(private val context: Context) : MusicApiService {
    override fun getNewFeature(param: String): JsonObject =
        callApi(MusicApiMethod.NEW_FEATURE, mapOf("param" to param))
}
```

### 步骤 4：在 Provider 的 apiMap 中添加映射

如果使用模块系统，在 `manifest.json` 中添加：
```json
{
    "apiMap": {
        "new/feature/api": "your-backend-endpoint"
    }
}
```

### 步骤 5：在 ViewModel / Repository 中使用

```kotlin
val body = api.getNewFeature("value")
```

---

## 9. 错误处理规范

### 9.1 错误码定义

| code | 含义 | 触发条件 | 处理建议 |
|------|------|----------|----------|
| `200` | 成功 | 正常响应 | 处理业务数据 |
| `-1` | Provider 不支持 | apiMap 映射为 "unsupported" | 提示用户切换 Provider |
| `500` | 服务端/网络错误 | Provider 调用失败或 JSON 解析失败 | 重试或提示用户 |
| `800` | 二维码过期 | 扫码登录超时 | 重新获取二维码 |
| `801` | 等待扫码 | 用户未扫码 | 继续轮询 |
| `802` | 等待确认 | 用户已扫码未确认 | 继续轮询 |
| `803` | 登录成功 | 扫码登录完成 | 保存 cookie |

### 9.2 ViewModel 中的标准错误处理

```kotlin
fun loadData() {
    viewModelScope.launch {
        isLoading = true
        try {
            val body = withContext(Dispatchers.IO) { api.someMethod() }
            when (body.get("code")?.asInt) {
                200 -> {
                    // 成功：解析业务数据
                    val data = body.get("data")?.asJsonObject
                    // ...
                }
                -1 -> {
                    // Provider 不支持：提示切换
                    DebugLog.toast(context, "当前提供商不支持此功能")
                }
                else -> {
                    // 其他错误
                    val msg = body.get("msg")?.asString ?: "未知错误"
                    DebugLog.e("API error: $msg")
                }
            }
        } catch (e: Exception) {
            // 网络/解析异常
            DebugLog.e("loadData failed", e)
        } finally {
            isLoading = false
        }
    }
}
```

---

## 10. 代码规范与禁忌

### ✅ 必须

1. **使用 `MusicApiMethod` 常量** — 所有 API 方法名必须引用常量
2. **通过 `MusicApiService` 调用** — 这是唯一合法的 API 入口
3. **IO 线程执行** — 所有 API 调用在 `Dispatchers.IO` 中
4. **cookie 自动注入** — `MusicApiService` 自动处理，无需手动传入
5. **检查 `code` 字段** — 每次 API 调用后检查响应状态码

### ❌ 禁止

1. **禁止裸字符串** — 不得使用 `"cloudsearch"` 等硬编码字符串
2. **禁止绕过 MusicApiService** — 不得直接调用 `ProviderManager.callApi()`
3. **禁止在 UI 线程调用** — API 调用阻塞会导致 ANR
4. **禁止忽略错误码** — 不得只处理成功情况

### ⚠️ 废弃代码

- [`ApiRepository`](../app/src/main/java/cp/player/repository/ApiRepository.kt) 已标记 `@Deprecated`，内部委托给 `MusicApiService`。新代码不应使用。
- `BaseViewModel.callApi()` 保留用于向后兼容，新代码应直接使用 `api.xxx()` 类型安全方法。

---

## 附录：文件索引

| 文件 | 说明 |
|------|------|
| [`api/MusicApiMethod.kt`](../app/src/main/java/cp/player/api/MusicApiMethod.kt) | API 方法名常量 |
| [`api/MusicApiService.kt`](../app/src/main/java/cp/player/api/MusicApiService.kt) | API 服务接口 |
| [`api/MusicApiServiceImpl.kt`](../app/src/main/java/cp/player/api/MusicApiServiceImpl.kt) | API 服务实现 |
| [`api/MusicApiServiceFactory.kt`](../app/src/main/java/cp/player/api/MusicApiServiceFactory.kt) | 单例工厂 |
| [`provider/BackendProvider.kt`](../app/src/main/java/cp/player/provider/BackendProvider.kt) | Provider 接口 |
| [`provider/ProviderManager.kt`](../app/src/main/java/cp/player/provider/ProviderManager.kt) | Provider 生命周期管理 |
| [`provider/ModuleManager.kt`](../app/src/main/java/cp/player/provider/ModuleManager.kt) | 模块加载 |
| [`provider/ModuleManifest.kt`](../app/src/main/java/cp/player/provider/ModuleManifest.kt) | 模块清单结构 |
| [`provider/JniProvider.kt`](../app/src/main/java/cp/player/provider/JniProvider.kt) | JNI Provider |
| [`provider/BinaryProvider.kt`](../app/src/main/java/cp/player/provider/BinaryProvider.kt) | Binary Provider |
| [`provider/HttpProvider.kt`](../app/src/main/java/cp/player/provider/HttpProvider.kt) | HTTP Provider |
| [`repository/PlaybackRepository.kt`](../app/src/main/java/cp/player/repository/PlaybackRepository.kt) | 播放数据仓库 |
| [`repository/ApiRepository.kt`](../app/src/main/java/cp/player/repository/ApiRepository.kt) | ⚠️ 已废弃 |
| [`viewmodel/BaseViewModel.kt`](../app/src/main/java/cp/player/viewmodel/BaseViewModel.kt) | ViewModel 基类 |
