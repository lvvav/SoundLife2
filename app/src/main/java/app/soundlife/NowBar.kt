package app.soundlife

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.content.Intent
import android.os.Build
import androidx.media3.common.Player
import app.soundlife.PlaybackService

/**
 * Статусная строка / Now Bar (Android 16+, One UI 8.5): «живое» уведомление
 * с коротким текстом. Нажатие открывает приложение.
 * Методы API 36 вызываем через рефлексию, чтобы не поднимать compileSdk.
 */
object NowBar {
    private const val CHANNEL = "now_bar2"
    private const val ID = 9001
    const val A_SHUF = "app.soundlife.NB_SHUF"
    const val A_PREV = "app.soundlife.NB_PREV"
    const val A_PLAY = "app.soundlife.NB_PLAY"
    const val A_NEXT = "app.soundlife.NB_NEXT"
    const val A_LIKE = "app.soundlife.NB_LIKE"

    private fun action(ctx: Context, icon: Int, label: String, act: String, code: Int): Notification.Action {
        val pi = PendingIntent.getService(
            ctx, code, Intent(ctx, PlaybackService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(ctx, icon), label, pi).build()
    }

    fun update(ctx: Context, p: Player, token: android.media.session.MediaSession.Token? = null) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val item = p.currentMediaItem
        if (item == null || p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
            nm.cancel(ID); return
        }
        if (Build.VERSION.SDK_INT < 33) return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Сейчас играет", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "SoundLife" }
        val artist = item.mediaMetadata.artist?.toString().orEmpty()
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_now_bar)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .also { b -> token?.let { b.setStyle(Notification.MediaStyle().setMediaSession(it).setShowActionsInCompactView(1, 2, 3)) } }
        // Кнопки: перемешка слева, сердечко справа; между ними назад, пауза/играть, далее
        val liked = Favorites.has(item.mediaId)
        b.addAction(action(ctx, if (p.shuffleModeEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off, "Перемешка", A_SHUF, 1))
        b.addAction(action(ctx, android.R.drawable.ic_media_previous, "Назад", A_PREV, 2))
        b.addAction(action(ctx, if (p.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "Пауза или играть", A_PLAY, 3))
        b.addAction(action(ctx, android.R.drawable.ic_media_next, "Далее", A_NEXT, 4))
        b.addAction(action(ctx, if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart, "Любимое", A_LIKE, 5))
        b.extras.putBoolean("android.requestPromotedOngoing", true)
        if (Build.VERSION.SDK_INT >= 36) {
            // Флаги Live Updates (Android 16): коротко показываем в статусной строке
            runCatching {
                Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    .invoke(b, true)
                Notification.Builder::class.java.getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(b, if (title.length > 12) title.take(11) + "…" else title)
            }
        }
        nm.notify(ID, b.build())
    }
}
