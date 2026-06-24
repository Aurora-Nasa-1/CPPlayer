package cp.player.engine

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import cp.player.util.UserPreferences

object CPPlayerManager {

    fun createPlayer(context: Context, engineType: Int): Player {
        return if (engineType == 1) {
            FlickPlayer(context)
        } else {
            ExoAudioFxManager.initPrefs(context)
            val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)

            val dataSourceFactory = DataSourceFactoryProvider.createCacheDataSourceFactory(context)

            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)

            val playerBuilder = ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)

            val player = playerBuilder.build()

            player.addListener(object : Player.Listener {
                private var currentSessionId: Int = 0

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    if (currentSessionId != 0 && currentSessionId != audioSessionId) {
                        ExoAudioFxManager.release(currentSessionId)
                    }
                    currentSessionId = audioSessionId
                    ExoAudioFxManager.init(audioSessionId)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_IDLE && currentSessionId != 0) {
                        // The player is stopped or released
                        ExoAudioFxManager.release(currentSessionId)
                        currentSessionId = 0
                    }
                }
            })

            if (UserPreferences.getAutoAudioFocus(context)) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()
                player.setAudioAttributes(audioAttributes, true)
                player.setHandleAudioBecomingNoisy(true)
            }

            player
        }
    }
}
