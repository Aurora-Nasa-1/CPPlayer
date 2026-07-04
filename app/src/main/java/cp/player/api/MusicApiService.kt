package cp.player.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cp.player.provider.ProviderManager
import cp.player.util.UserPreferences

/**
 * 统一的音乐 API 服务。
 *
 * **这是应用内所有音乐 API 调用的唯一入口。**
 *
 * ### 设计原则
 * 1. 所有 API 调用都通过此接口进行，禁止直接调用 [ProviderManager]
 * 2. 方法签名返回 [JsonObject]，由调用方（Repository 层）解析为领域模型
 * 3. 内部自动注入 cookie，调用方无需手动传递
 * 4. 错误统一返回 `{"code": 500, "msg": "..."}` 格式的 JSON
 * 5. 所有方法均为 [suspend fun]，内部自动在 IO 线程执行网络调用
 *
 * ### 调用链路
 * ```
 * ViewModel → Repository → MusicApiService → ProviderManager → BackendProvider
 * ```
 *
 * @see MusicApiMethod 所有 API 方法名常量
 */
interface MusicApiService {

    // ======================== 通用 ========================

    /**
     * 通用 API 调用。当需要调用 [MusicApiMethod] 中未覆盖的新增接口时使用。
     *
     * @param method API 方法名（应来自 [MusicApiMethod] 或与后端约定的路径）
     * @param params 请求参数
     * @param cookie 认证 cookie，null 时使用默认 cookie
     * @return JSON 响应
     */
    suspend fun callApi(
        method: String,
        params: Map<String, String> = emptyMap(),
        cookie: String? = null
    ): JsonObject

    // ======================== 认证 Auth ========================

    /** 获取扫码登录的二维码 key */
    suspend fun getQrKey(): JsonObject

    /** 创建二维码图片 */
    suspend fun createQrCode(key: String): JsonObject

    /** 检查扫码登录状态 */
    suspend fun checkQrStatus(key: String): JsonObject

    /** 邮箱登录 */
    suspend fun login(email: String, password: String, md5: Boolean = false): JsonObject

    /** 手机号登录 */
    suspend fun loginWithPhone(
        phone: String,
        password: String,
        captcha: Boolean = false,
        md5: Boolean = false
    ): JsonObject

    /** 发送验证码 */
    suspend fun sendCaptcha(phone: String): JsonObject

    /** 登出 */
    suspend fun logout(): JsonObject

    /** 游客登录 */
    suspend fun loginAnonymous(): JsonObject

    /** 获取当前登录状态 */
    suspend fun getLoginStatus(cookie: String? = null): JsonObject

    // ======================== 用户 User ========================

    /** 获取用户歌单列表（包含创建的和收藏的） */
    suspend fun getUserPlaylists(uid: Long): JsonObject

    /** 获取用户创建的歌单列表 */
    suspend fun getUserCreatedPlaylists(uid: Long): JsonObject

    /** 获取用户收藏的歌单列表 */
    suspend fun getUserCollectedPlaylists(uid: Long): JsonObject

    /** 获取用户详情 */
    suspend fun getUserDetail(uid: Long): JsonObject

    /** 获取用户云盘歌曲 */
    suspend fun getUserCloud(limit: Int = 200, offset: Int = 0): JsonObject

    /** 获取喜欢的音乐 ID 列表 */
    suspend fun getLikeList(uid: Long): JsonObject

    /** 喜欢/取消喜欢歌曲 */
    suspend fun likeSong(id: String, like: Boolean): JsonObject

    /** 获取每日推荐歌曲 */
    suspend fun getRecommendedSongs(): JsonObject

    /** 获取推荐歌单 */
    suspend fun getRecommendedPlaylists(): JsonObject

    /** 不喜欢推荐歌曲 */
    suspend fun dislikeSong(id: String): JsonObject

    // ======================== 歌单 Playlist ========================

    /** 获取歌单详情 */
    suspend fun getPlaylistDetail(id: Long): JsonObject

    /** 获取歌单全部歌曲 */
    suspend fun getPlaylistTracks(id: Long, limit: Int = 1000, offset: Int = 0): JsonObject

    /** 添加歌曲到歌单 */
    suspend fun addTracksToPlaylist(pid: Long, trackIds: List<String>): JsonObject

    /** 从歌单删除歌曲 */
    suspend fun removeTracksFromPlaylist(pid: Long, trackIds: List<String>): JsonObject

    /** 创建歌单 */
    suspend fun createPlaylist(name: String, privacy: Int = 0): JsonObject

    /** 删除歌单 */
    suspend fun deletePlaylist(id: Long): JsonObject

    /**
     * 收藏/取消收藏歌单。
     * @param t 1=收藏, 2=取消收藏
     */
    suspend fun subscribePlaylist(id: Long, t: Int): JsonObject

    // ======================== 专辑 Album ========================

    /** 获取专辑详情（含歌曲列表） */
    suspend fun getAlbumDetail(id: Long): JsonObject

    // ======================== 歌手 Artist ========================

    /** 获取歌手详情 */
    suspend fun getArtistDetail(id: Long): JsonObject

    /** 获取歌手歌曲列表 */
    suspend fun getArtistSongs(id: Long, limit: Int = 50): JsonObject

    /** 获取歌手专辑列表 */
    suspend fun getArtistAlbums(id: Long, limit: Int = 20): JsonObject

    // ======================== 搜索 Search ========================

    /**
     * 云搜索
     * @param type 1=单曲, 1000=歌单, 100=歌手, 10=专辑, 1014=视频
     */
    suspend fun search(keywords: String, type: Int = 1): JsonObject

    /** 获取热搜详情 */
    suspend fun getHotSearches(): JsonObject

    /** 获取搜索建议 */
    suspend fun getSearchSuggestions(keywords: String): JsonObject

    // ======================== 播放 Playback ========================

    /**
     * 获取歌曲播放 URL（302 重定向版本，优先使用）
     *
     * @param songId 歌曲 ID
     * @param level 音质等级: standard / higher / exhigh / lossless / hires / jyeffect / jymaster / sky / immersive / dolby
     */
    suspend fun getSongUrl(songId: String, level: String = "standard"): JsonObject

    /**
     * 获取歌曲播放 URL（直接返回版本，302 失败时降级使用）
     */
    suspend fun getSongUrlFallback(songId: String, level: String = "standard"): JsonObject

    /**
     * 获取歌曲下载 URL
     */
    suspend fun getSongDownloadUrl(songId: String, level: String = "standard"): JsonObject

    /** 获取歌曲详情（可批量） */
    suspend fun getSongDetail(ids: List<String>): JsonObject

    /** 获取私人 FM 歌曲 */
    suspend fun getPersonalFm(): JsonObject

    /** 获取心动模式/智能播放列表 */
    suspend fun getIntelligenceList(songId: String, playlistId: Long): JsonObject

    /** 获取歌词 */
    suspend fun getLyric(songId: String): JsonObject

    /**
     * 听歌打卡（上报播放进度，影响推荐算法和听歌排行）。
     *
     * @param songId 歌曲 ID
     * @param sourceId 来源歌单/专辑 ID
     * @param playedSeconds 已播放时长（秒）
     */
    suspend fun scrobble(songId: String, sourceId: String, playedSeconds: Int): JsonObject

    // ======================== 社交 Social ========================

    /**
     * 根据类型获取对应的评论 API 方法名
     */
    fun getCommentMethod(type: String): String {
        return when (type) {
            "music" -> MusicApiMethod.COMMENT_MUSIC
            "playlist" -> MusicApiMethod.COMMENT_PLAYLIST
            "album" -> MusicApiMethod.COMMENT_ALBUM
            "mv" -> MusicApiMethod.COMMENT_MV
            "dj" -> MusicApiMethod.COMMENT_DJ
            "video" -> MusicApiMethod.COMMENT_VIDEO
            else -> MusicApiMethod.COMMENT_MUSIC
        }
    }

    /**
     * 获取评论列表
     * @param id 资源 ID
     * @param type 资源类型: music / playlist / album / mv / dj / video
     * @param limit 每页数量
     * @param offset 偏移量
     */
    suspend fun getComments(id: String, type: String = "music", limit: Int = 20, offset: Int = 0, sortType: Int = 1): JsonObject

    /**
     * 获取楼层评论
     * @param id 资源 ID
     * @param parentCommentId 父评论 ID
     * @param type 资源类型
     * @param limit 每页数量
     * @param time 翻页游标
     */
    suspend fun getFloorComments(
        id: String,
        parentCommentId: Long,
        type: String = "music",
        limit: Int = 20,
        time: Long = 0
    ): JsonObject

    /**
     * 点赞评论
     * @param id 资源 ID
     * @param cid 评论 ID
     * @param type 资源类型
     * @param liked true=点赞, false=取消点赞
     */
    suspend fun likeComment(id: String, cid: Long, type: String, liked: Boolean): JsonObject

    /**
     * 发表/回复评论
     * @param id 资源 ID
     * @param type 资源类型
     * @param content 评论内容
     * @param replyId 回复的评论 ID，null 表示发表新评论
     */
    suspend fun postComment(id: String, type: String, content: String, replyId: Long? = null): JsonObject

    /** 获取未读消息数 */
    suspend fun getUnreadCount(): JsonObject

    /** 获取最近联系人 */
    suspend fun getRecentContacts(): JsonObject

    /** 获取私信列表 */
    suspend fun getPrivateMessages(): JsonObject

    /** 获取与某人的私信历史 */
    suspend fun getMessageHistory(uid: Long): JsonObject

    /** 标记私信已读 */
    suspend fun markMessageAsRead(uid: Long): JsonObject

    /** 发送文本消息 */
    suspend fun sendMessage(uid: Long, text: String): JsonObject

    // ======================== 排行榜 Ranking ========================

    /** 获取所有榜单列表 */
    suspend fun getToplist(): JsonObject

    /** 获取所有榜单内容摘要 */
    suspend fun getToplistDetail(): JsonObject

    /** 新歌速递 @param type 地区: 0=全部, 7=华语, 96=欧美, 8=日本, 16=韩国 */
    suspend fun getTopSongs(type: Int = 0): JsonObject

    /** 新碟上架 @param area ALL/ZH/EA/KR/JP */
    suspend fun getTopAlbums(area: String = "ALL", limit: Int = 30): JsonObject

    /** 热门歌手 */
    suspend fun getTopArtists(limit: Int = 30): JsonObject

    /** 热门歌单 @param order new/hot @param cat 分类标签 */
    suspend fun getTopPlaylists(order: String = "hot", cat: String = "全部", limit: Int = 30): JsonObject

    /** 精品歌单 */
    suspend fun getHighqualityPlaylists(cat: String = "全部", limit: Int = 30): JsonObject

    // ======================== 推荐 Discovery ========================

    /** 推荐歌单（无需登录） */
    suspend fun getPersonalizedPlaylists(limit: Int = 30): JsonObject

    /** 推荐新音乐 */
    suspend fun getPersonalizedNewSongs(limit: Int = 10): JsonObject

    /** 首页 Banner */
    suspend fun getBanner(): JsonObject

    /** 历史日推可用日期列表 */
    suspend fun getHistoryRecommendSongs(): JsonObject

    /** 历史日推详情 @param date 日期字符串 */
    suspend fun getHistoryRecommendSongsDetail(date: String): JsonObject

    // ======================== 相似 Similar ========================

    /** 获取相似歌曲 */
    suspend fun getSimilarSongs(songId: String): JsonObject

    /** 获取相似歌手 */
    suspend fun getSimilarArtists(artistId: Long): JsonObject

    /** 获取相似歌单 */
    suspend fun getSimilarPlaylists(songId: String): JsonObject

    // ======================== 签到 Signin ========================

    /** 每日签到 */
    suspend fun dailySignin(): JsonObject
}
