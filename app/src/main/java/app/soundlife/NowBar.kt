package app.soundlife

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.Player

/**
 * Статусная строка / Now Bar (Android 16+, One UI 8.5): «живое» уведомление
 * с коротким текстом. Нажатие открывает приложение.
 * Методы API 36 вызываем через рефлексию, чтобы не поднимать compileSdk.
 */
object NowBar {
    private const val CHANNEL = "now_bar"
    private const val ID = 9001

    fun update(ctx: Context, p: Player) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val item = p.currentMediaItem
        if (item == null || p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
            nm.cancel(ID); return
        }
        if (Build.VERSION.SDK_INT < 33) return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Сейчас играет", NotificationManager.IMPORTANCE_LOW))
        }
        val title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "SoundLife" }
        val artist = item.mediaMetadata.artist?.toString().orEmpty()
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
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
