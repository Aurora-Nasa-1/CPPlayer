package cp.player.api

/**
 * 统一的音乐 API 方法名常量定义。
 *
 * 所有对后端 Provider 的 API 调用都应通过 [MusicApiService] 进行，
 * 方法名从此常量对象获取，禁止使用裸字符串。
 *
 * ### 命名约定
 * - 常量使用 `UPPER_SNAKE_CASE`
 * - 按业务域分组（Auth / User / Playlist / Artist / Search / Playback / Social）
 * - 值与后端 Provider 的 API 路径保持一致
 */
object MusicApiMethod {

    // ======================== 认证 Auth ========================

    /** 扫码登录 - 获取二维码 key */
    const val AUTH_QR_KEY = "login/qr/key"

    /** 扫码登录 - 创建二维码 */
    const val AUTH_QR_CREATE = "login/qr/create"

    /** 扫码登录 - 检查扫码状态 */
    const val AUTH_QR_CHECK = "login/qr/check"

    /** 邮箱登录 */
    const val AUTH_LOGIN = "login"

    /** 手机号登录 */
    const val AUTH_LOGIN_PHONE = "login/cellphone"

    /** 发送验证码 */
    const val AUTH_CAPTCHA_SENT = "captcha/sent"

    /** 登出 */
    const val AUTH_LOGOUT = "logout"

    /** 游客登录 */
    const val AUTH_ANONYMOUS = "register/anonimous"

    /** 获取登录状态 */
    const val AUTH_LOGIN_STATUS = "login/status"

    // ======================== 用户 User ========================

    /** 获取用户歌单列表（包含创建的和收藏的） */
    const val USER_PLAYLIST = "user/playlist"

    /** 获取用户创建的歌单列表 */
    const val USER_PLAYLIST_CREATE = "user/playlist/create"

    /** 获取用户收藏的歌单列表 */
    const val USER_PLAYLIST_COLLECT = "user/playlist/collect"

    /** 获取用户详情 */
    const val USER_DETAIL = "user/detail"

    /** 获取用户云盘歌曲 */
    const val USER_CLOUD = "user/cloud"

    /** 获取喜欢的音乐 ID 列表 */
    const val USER_LIKE_LIST = "likelist"

    /** 喜欢/取消喜欢歌曲 */
    const val USER_LIKE = "like"

    /** 获取每日推荐歌曲 */
    const val USER_RECOMMEND_SONGS = "recommend/songs"

    /** 获取推荐歌单 */
    const val USER_RECOMMEND_RESOURCE = "recommend/resource"

    /** 不喜欢推荐歌曲 */
    const val USER_DISLIKE_SONG = "recommend/songs/dislike"

    // ======================== 歌单 Playlist ========================

    /** 获取歌单详情 */
    const val PLAYLIST_DETAIL = "playlist/detail"

    /** 获取歌单全部歌曲 */
    const val PLAYLIST_TRACK_ALL = "playlist/track/all"

    /** 添加/删除歌单中的歌曲 */
    const val PLAYLIST_TRACKS = "playlist/tracks"

    /** 创建歌单 */
    const val PLAYLIST_CREATE = "playlist/create"

    /** 删除歌单 */
    const val PLAYLIST_DELETE = "playlist/delete"

    /** 收藏/取消收藏歌单 (t=1收藏, t=2取消收藏) */
    const val PLAYLIST_SUBSCRIBE = "playlist/subscribe"

    // ======================== 专辑 Album ========================

    /** 获取专辑详情（含歌曲列表） */
    const val ALBUM_DETAIL = "album"

    // ======================== 歌手 Artist ========================

    /** 获取歌手详情 */
    const val ARTIST_DETAIL = "artist/detail"

    /** 获取歌手歌曲列表 */
    const val ARTIST_SONGS = "artist/songs"

    /** 获取歌手专辑列表 */
    const val ARTIST_ALBUM = "artist/album"

    // ======================== 搜索 Search ========================

    /** 云搜索（统一搜索接口） */
    const val SEARCH_CLOUD = "cloudsearch"

    /** 热搜详情 */
    const val SEARCH_HOT_DETAIL = "search/hot/detail"

    /** 搜索建议 */
    const val SEARCH_SUGGEST = "search/suggest"

    /** 搜索类型：单曲 */
    const val SEARCH_TYPE_SONG = 1

    /** 搜索类型：专辑 */
    const val SEARCH_TYPE_ALBUM = 10

    /** 搜索类型：歌手 */
    const val SEARCH_TYPE_ARTIST = 100

    /** 搜索类型：歌单 */
    const val SEARCH_TYPE_PLAYLIST = 1000

    // ======================== 播放 Playback ========================

    /** 获取歌曲播放 URL (302 重定向) */
    const val SONG_URL_V1_302 = "song/url/v1/302"

    /** 获取歌曲播放 URL */
    const val SONG_URL_V1 = "song/url/v1"

    /** 获取歌曲下载 URL */
    const val SONG_DOWNLOAD_URL = "song/download/url/v1"

    /** 获取歌曲详情 */
    const val SONG_DETAIL = "song/detail"

    /** 私人 FM */
    const val PERSONAL_FM = "personal_fm"

    /** 心动模式/智能播放列表 */
    const val INTELLIGENCE_LIST = "playmode/intelligence/list"

    /** 获取歌词 */
    const val LYRIC_NEW = "lyric/new"

    // ======================== 社交 Social ========================

    /** 音乐评论 */
    const val COMMENT_MUSIC = "comment/music"

    /** 歌单评论 */
    const val COMMENT_PLAYLIST = "comment/playlist"

    /** 专辑评论 */
    const val COMMENT_ALBUM = "comment/album"

    /** MV 评论 */
    const val COMMENT_MV = "comment/mv"

    /** 电台评论 */
    const val COMMENT_DJ = "comment/dj"

    /** 视频评论 */
    const val COMMENT_VIDEO = "comment/video"

    /** 楼层评论 */
    const val COMMENT_FLOOR = "comment/floor"

    /** 点赞评论 */
    const val COMMENT_LIKE = "comment/like"

    /** 发表/回复评论 */
    const val COMMENT_POST = "comment"

    /** 未读消息数 */
    const val MESSAGE_UNREAD_COUNT = "pl/count"

    /** 最近联系人 */
    const val MESSAGE_RECENT_CONTACT = "msg/recentcontact"

    /** 私信列表 */
    const val MESSAGE_PRIVATE = "msg/private"

    /** 私信历史记录 */
    const val MESSAGE_PRIVATE_HISTORY = "msg/private/history"

    /** 标记私信已读 */
    const val MESSAGE_MARK_READ = "msg/private/mark/read"

    /** 发送文本消息 */
    const val MESSAGE_SEND_TEXT = "send/text"
}
