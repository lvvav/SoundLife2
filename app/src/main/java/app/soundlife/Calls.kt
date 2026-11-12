package app.soundlife

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * Звонки, как на сайте: сигналы через Realtime (канал sl-call-<uid>), сама связь через страницу VDO.Ninja.
 * Состояние: IDLE — нет звонка, RING — я звоню, IN — мне звонят, LIVE — разговор.
 */
object Calls {
    enum class St { IDLE, RING, IN, LIVE }

    const val CH_CALL = "calls"
    private const val NID_CALL = 2001

    var st by mutableStateOf(St.IDLE); private set
    var peer = ""; private set
    var peerName by mutableStateOf(""); private set
    var room by mutableStateOf(""); private set
    var pw by mutableStateOf(""); private set
    var video = false; private set
    private var cid = ""
    private var dir = ""
    private var t0 = 0L
    private var logged = false
    private var timer: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
    }

    private fun toast(msg: String) {
        main.post { appCtx?.let { Toast.makeText(it, msg, Toast.LENGTH_LONG).show() } }
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    private fun obj(vararg kv: Pair<String, Any>): JSONObject {
        val o = JSONObject()
        kv.forEach { o.put(it.first, it.second) }
        return o
    }

    /** Позвонить человеку. */
    fun start(peerUid: String, withVideo: Boolean, name: String) {
        val me = Auth.userId()
        if (me == null) { toast("Сначала войдите в аккаунт"); return }
        if (st != St.IDLE) { toast("Звонок уже идёт"); return }
        val c = newId()
        cid = c; peer = peerUid; peerName = name; dir = "out"; video = withVideo; logged = false; t0 = 0L
        room = "sl" + newId(); pw = newId()
        st = St.RING
        val r = room; val p = pw
        scope.launch {
            val ok = Realtime.sendTo("sl-call-$peerUid", "offer",
                obj("cid" to c, "from" to me, "vdo" to obj("room" to r, "pw" to p), "video" to withVideo))
            if (!ok) { if (cid == c) finish("fail", "Нет связи с сервером звонков"); return@launch }
            val n = Profile.nick
            val body = (if (withVideo) "📹 Звонит " else "📞 Звонит ") + (if (n.isNotEmpty()) "@$n" else "друг")
            logged = runCatching { Chat.send(peerUid, body, "call/$c"); true }.getOrDefault(false)
            timer = scope.launch {
                delay(30_000)
                if (cid == c && st == St.RING) hang("Не отвечает")
            }
        }
    }

    /** Ответить на входящий. */
    fun answer() {
        if (st != St.IN) return
        val me = Auth.userId() ?: return
        val c = cid; val p = peer
        goLive()
        scope.launch { Realtime.sendTo("sl-call-$p", "answer", obj("cid" to c, "from" to me, "vdo" to 1)) }
    }

    /** Отклонить входящий. */
    fun decline() {
        if (st != St.IN) return
        val me = Auth.userId() ?: return
        val c = cid; val p = peer
        scope.launch { Realtime.sendTo("sl-call-$p", "decline", obj("cid" to c, "from" to me)) }
        finish("none", null)
    }

    /** Завершить или отменить звонок, собеседник получит сигнал. */
    fun hang(msg: String? = null) {
        if (st == St.IDLE) return
        val me = Auth.userId()
        val c = cid; val p = peer
        val res = if (t0 > 0) "ok" else "miss"
        if (me != null && dir != "") scope.launch { Realtime.sendTo("sl-call-$p", "hangup", obj("cid" to c, "from" to me)) }
        finish(res, msg)
    }

    /** Сигналы из канала sl-call-<мой uid>. Вызывается из фонового сервиса. */
    fun onEvent(ev: String, p: JSONObject) {
        val me = Auth.userId() ?: return
        val c = p.optString("cid")
        when (ev) {
            "offer" -> {
                val from = p.optString("from")
                val vdo = p.optJSONObject("vdo") ?: return
                if (c.isEmpty() || from.isEmpty()) return
                if (st != St.IDLE) {
                    if (c != cid) scope.launch { Realtime.sendTo("sl-call-$from", "busy", obj("cid" to c, "from" to me)) }
                    return
                }
                cid = c; peer = from; dir = "in"; room = vdo.optString("room"); pw = vdo.optString("pw")
                video = p.optBoolean("video"); t0 = 0L; logged = false
                peerName = "Собеседник"
                st = St.IN
                scope.launch {
                    val name = runCatching { Social.person(from)?.nick }.getOrNull().orEmpty()
                    if (cid == c && name.isNotEmpty()) peerName = "@$name"
                }
                showIncoming()
                timer = scope.launch {
                    delay(35_000)
                    if (cid == c && st == St.IN) finish("miss", "Пропущенный звонок")
                }
            }
            "answer" -> if (dir == "out" && c == cid && st == St.RING) goLive()
            "hangup" -> if (c == cid && st != St.IDLE) {
                if (dir == "in" && st == St.IN) finish("miss", "Пропущенный звонок")
                else finish(if (t0 > 0) "ok" else "miss", null)
            }
            "busy" -> if (c == cid && st != St.IDLE) finish("busy", "Занято")
            "decline" -> if (c == cid && st != St.IDLE) finish("decline", "Собеседник отклонил звонок")
        }
    }

    private fun goLive() {
        timer?.cancel()
        t0 = System.currentTimeMillis()
        st = St.LIVE
        appCtx?.let { NotificationManagerCompat.from(it).cancel(NID_CALL) }
    }

    private fun finish(res: String, msg: String?) {
        if (st == St.IDLE) return
        timer?.cancel(); timer = null
        val dur = if (t0 > 0) System.currentTimeMillis() - t0 else 0L
        val wasOut = dir == "out"; val wasLogged = logged; val c = cid; val p = peer
        st = St.IDLE; cid = ""; room = ""; pw = ""; dir = ""; t0 = 0L; logged = false
        appCtx?.let { NotificationManagerCompat.from(it).cancel(NID_CALL) }
        if (wasOut && wasLogged) {
            val text = when (res) {
                "ok" -> "📞 Звонок · " + String.format("%d:%02d", dur / 60000, (dur / 1000) % 60)
                "miss" -> "📞 Не отвечают"
                "decline" -> "📞 Звонок отклонён"
                "busy" -> "📞 Занято"
                else -> "📞 Звонок отменён"
            }
            if (res != "fail") scope.launch { runCatching { Chat.send(p, text, "call/$c/$res") } }
        }
        msg?.let { toast(it) }
    }

    private fun showIncoming() {
        val ctx = appCtx ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH_CALL, "Звонки", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build(),
            )
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CH_CALL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Входящий звонок")
            .setContentText(peerName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .build()
        if (Notifier.canPost(ctx)) NotificationManagerCompat.from(ctx).notify(NID_CALL, n)
    }
}
