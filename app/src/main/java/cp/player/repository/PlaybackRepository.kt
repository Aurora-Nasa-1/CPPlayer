package cp.player.repository

import com.google.gson.JsonObject
import cp.player.api.MusicApiMethod
import cp.player.api.MusicApiService
import cp.player.api.MusicApiServiceFactory
import cp.player.model.LyricLine
import cp.player.model.Song
import cp.player.service.LyricService
import cp.player.util.JsonUtils

/**
 * 播放数据仓库。
 *
 * 负责播放相关的 API 调用和数据转换，包括：
 * - 歌词获取与解析
 * - 私人 FM
 * - 心动模式
 *
 * ### 调用链路
 * ```
 * ViewModel → PlaybackRepository → MusicApiService → ProviderManager → BackendProvider
 * ```
 */
class PlaybackRepository(
    private val api: MusicApiService = MusicApiServiceFactory.instance
) {

    /**
     * 获取歌词并解析为 [LyricLine] 列表。
     *
     * @param songId 歌曲 ID
     * @param duration 时长（ms），用于 LRC 回退解析
     * @param cookie 认证 cookie
     * @return 已合并翻译的歌词行列表
     */
    fun fetchLyrics(songId: String, duration: Long, cookie: String?): List<LyricLine> {
        val body = api.getLyric(songId)
        return LyricService.parseFromJson(body, duration)
    }

    /**
     * 获取私人 FM 歌曲列表。
     *
     * @param cookie 认证 cookie
     * @return 歌曲列表
     */
    fun getPersonalFm(cookie: String?): List<Song> {
        val body = api.callApi(MusicApiMethod.PERSONAL_FM, mapOf("timestamp" to System.currentTimeMillis().toString()), cookie)
        return (body.get("data")?.asJsonArray ?: body.get("result")?.asJsonArray)
            ?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
    }

    /**
     * 获取心动模式/智能播放列表。
     *
     * @param songId 当前歌曲 ID
     * @param playlistId 歌单 ID
     * @param cookie 认证 cookie
     * @return 歌曲列表
     */
    fun getHeartbeatSongs(songId: String, playlistId: Long, cookie: String?): List<Song> {
        val body = api.callApi(
            MusicApiMethod.INTELLIGENCE_LIST,
            mapOf(
                "id" to songId,
                "pid" to playlistId.toString(),
                "sid" to songId,
                "count" to "20"
            ),
            cookie
        )
        val songsJson = when {
            body.get("data")?.isJsonArray == true -> body.get("data").asJsonArray
            body.get("data")?.isJsonObject == true && body.get("data").asJsonObject.has("data") ->
                body.get("data").asJsonObject.get("data").asJsonArray
            body.has("list") && body.get("list").isJsonArray -> body.get("list").asJsonArray
            else -> null
        }
        return songsJson?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
    }
}
