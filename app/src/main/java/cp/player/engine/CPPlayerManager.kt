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
            val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)

            val dataSourceFactory = DataSourceFactoryProvider.createCacheDataSourceFactory(context)

            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)

            val playerBuilder = ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)

            if (UserPreferences.getAutoAudioFocus(context)) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()
                playerBuilder.setAudioAttributes(audioAttributes, true)
                playerBuilder.setHandleAudioBecomingNoisy(true)
            }

            playerBuilder.build()
        }
    }
}
