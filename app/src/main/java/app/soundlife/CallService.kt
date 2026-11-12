package app.soundlife

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Фоновое соединение с Realtime: нужно, чтобы входящие звонки приходили, когда приложение закрыто.
 * Видно как тихое уведомление «На связи». Запускается при входе в аккаунт и при открытии приложения.
 */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Calls.init(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH_SVC, "Фоновая связь", NotificationManager.IMPORTANCE_MIN)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val n = NotificationCompat.Builder(this, CH_SVC)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("SoundLife")
            .setContentText("На связи: входящие звонки")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NID_SVC, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NID_SVC, n)
        }
        Auth.init(this)
        val uid = Auth.userId()
        if (uid == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        Realtime.listen("sl-call-$uid") { ev, p -> Calls.onEvent(ev, p) }
        Realtime.start()
        return START_STICKY
    }

    // Android 15 ограничивает такие сервисы по времени: тогда просто завершаемся, соединение поднимется снова при открытии приложения
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    companion object {
        private const val CH_SVC = "service"
        private const val NID_SVC = 2002
    }
}
