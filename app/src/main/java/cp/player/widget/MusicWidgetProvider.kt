package cp.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import cp.player.MainActivity
import cp.player.R
import cp.player.service.MusicService
import cp.player.util.resized
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, null, null, null, false)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            songTitle: String?,
            artistName: String?,
            albumArtUrl: String?,
            isPlaying: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.music_widget)

            // Content
            views.setTextViewText(R.id.tv_title, songTitle ?: context.getString(R.string.app_name))
            views.setTextViewText(R.id.tv_artist, artistName ?: "")
            views.setImageViewResource(
                R.id.btn_play_pause,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            // Open App
            val intent = Intent(context, MainActivity::class.java).apply {
                action = "ACTION_SHOW_PLAYER"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            // Controls
            views.setOnClickPendingIntent(R.id.btn_prev, createServicePendingIntent(context, "ACTION_PREVIOUS", 1))
            views.setOnClickPendingIntent(R.id.btn_play_pause, createServicePendingIntent(context, "ACTION_TOGGLE_PLAY", 2))
            views.setOnClickPendingIntent(R.id.btn_next, createServicePendingIntent(context, "ACTION_NEXT", 3))

            if (!albumArtUrl.isNullOrEmpty()) {
                val loader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(albumArtUrl.resized(300))
                    .target(
                        onSuccess = { result ->
                            views.setImageViewBitmap(R.id.iv_cover, result.toBitmap())
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        },
                        onError = {
                            views.setImageViewResource(R.id.iv_cover, R.mipmap.ic_launcher)
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    )
                    .build()
                loader.enqueue(request)
            } else {
                views.setImageViewResource(R.id.iv_cover, R.mipmap.ic_launcher)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createServicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, MusicService::class.java).apply {
                this.action = action
            }
            return PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun updateAllWidgets(context: Context, songTitle: String?, artistName: String?, albumArtUrl: String?, isPlaying: Boolean) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MusicWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, songTitle, artistName, albumArtUrl, isPlaying)
            }
        }
    }
}
