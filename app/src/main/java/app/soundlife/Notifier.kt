package app.soundlife

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Уведомления о новых сообщениях без Firebase: фоновая проверка раз в 15 минут (Android не даёт чаще).
 * Проверяет только непрочитанные сообщения, пришедшие после прошлой проверки, текст не показывает.
 */
object Notifier {
    private const val CHANNEL = "messages"
    private const val WORK = "sl_msg_check"
    private const val K_LAST = "last_check"
    private const val PREFS = "notif"

    fun schedule(ctx: Context) {
        val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val req = PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES).setConstraints(c).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(CHANNEL, "Сообщения", NotificationManager.IMPORTANCE_DEFAULT)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Android 13+ спрашивает разрешение на уведомления один раз. */
    fun askPermission(act: Activity) {
        if (Build.VERSION.SDK_INT >= 33 && !canPost(act)) {
            ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun show(ctx: Context, count: Int) {
        createChannel(ctx)
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = if (count == 1) "У вас новое сообщение" else "Новых сообщений: $count"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("SoundLife")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(ctx).notify(1001, n)
    }

    class CheckWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            Auth.init(ctx)
            val u = Auth.userId() ?: return@withContext Result.success()
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val last = prefs.getString(K_LAST, "") ?: ""
            // Первый запуск: запоминаем время, старые непрочитанные не присылаем
            if (last.isEmpty()) {
                prefs.edit().putString(K_LAST, Instant.now().toString()).apply()
                return@withContext Result.success()
            }
            val tk = Auth.token() ?: return@withContext Result.success()
            try {
                val q = "/rest/v1/messages?select=id,created_at&recipient=eq.$u&read_at=is.null" +
                    "&created_at=gt.${URLEncoder.encode(last, "UTF-8")}&order=created_at.asc&limit=50"
                val arr = JSONArray(Sync.rest("GET", q, tk, null, null))
                if (arr.length() > 0) {
                    prefs.edit().putString(K_LAST, arr.getJSONObject(arr.length() - 1).optString("created_at")).apply()
                    if (canPost(ctx)) show(ctx, arr.length())
                }
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }
}
