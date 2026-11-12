package app.soundlife

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

/**
 * «Слушаем вместе»: комната sl-jam-<код>, формат сообщений как на сайте (st — состояние, hi — запрос состояния).
 * Треки ищутся в библиотеке по пути, поэтому играть можно только то, что есть в библиотеке приложения.
 * Количество участников не показывается (нет presence).
 */
object Jam {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val myId = UUID.randomUUID().toString().take(8)
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    private var appCtx: Context? = null
    private var tracks: List<Track> = emptyList()
    private var ctl: MediaController? = null
    private var ignoreUntil = 0L

    var code by mutableStateOf(""); private set

    private val tick = object : Runnable {
        override fun run() {
            if (code.isEmpty()) return
            push()
            main.postDelayed(this, 5000)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = push()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = push()
        override fun onPlaybackStateChanged(playbackState: Int) = push()
    }

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
    }

    /** Вызывается из SoundLifeApp, когда библиотека загрузилась. */
    fun setTracks(list: List<Track>) {
        tracks = list
    }

    /** Вызывается из SoundLifeApp, когда появился плеер. Вызывать в главном потоке. */
    fun attach(c: MediaController?) {
        ctl?.removeListener(listener)
        ctl = c
        c?.addListener(listener)
    }

    fun create() {
        val r = StringBuilder()
        repeat(6) { r.append(ALPHABET[(Math.random() * ALPHABET.length).toInt()]) }
        join(r.toString())
    }

    fun join(raw: String) {
        val c = raw.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }.take(8)
        if (c.length < 4) { toast("Код комнаты — 6 символов"); return }
        leave(quiet = true)
        code = c
        Realtime.listen("sl-jam-$c") { ev, p -> onEvent(ev, p) }
        scope.launch { Realtime.sendTo("sl-jam-$c", "hi", JSONObject().put("f", myId)) }
        main.post(tick)
        toast("Вы в комнате ${c.uppercase()}")
    }

    fun leave(quiet: Boolean = false) {
        val c = code
        if (c.isEmpty()) return
        code = ""
        main.removeCallbacks(tick)
        Realtime.unlisten("sl-jam-$c")
        if (!quiet) toast("Вы вышли из комнаты")
    }

    /** Текст приглашения для отправки другу. */
    fun inviteText(): String = "Заходи слушать музыку вместе в SoundLife. Код комнаты: ${code.uppercase()}"

    private fun toast(msg: String) {
        main.post { appCtx?.let { Toast.makeText(it, msg, Toast.LENGTH_LONG).show() } }
    }

    /** Отправить своё состояние, если играет трек из библиотеки. Вызывать в главном потоке. */
    private fun push() {
        if (code.isEmpty() || System.currentTimeMillis() < ignoreUntil) return
        val c = ctl ?: return
        val id = c.currentMediaItem?.mediaId ?: return
        if (!id.startsWith(BuildConfig.GH_FOLDER + "/")) return
        val md = c.mediaMetadata
        val st = JSONObject()
            .put("k", id)
            .put("t", md.title?.toString().orEmpty())
            .put("a", md.artist?.toString().orEmpty())
            .put("pos", c.currentPosition / 1000.0)
            .put("pl", c.isPlaying)
            .put("f", myId)
        val room = code
        scope.launch { Realtime.sendTo("sl-jam-$room", "st", st) }
    }

    private fun onEvent(ev: String, p: JSONObject) {
        if (p.optString("f") == myId) return
        when (ev) {
            "st" -> main.post { apply(p) }
            "hi" -> main.post { push() }
        }
    }

    /** Применить состояние из комнаты: трек, позиция и пауза/воспроизведение. Вызывать в главном потоке. */
    private fun apply(p: JSONObject) {
        val c = ctl ?: return
        val k = p.optString("k")
        if (k.isEmpty()) return
        val t = tracks.firstOrNull { it.path == k } ?: run { toast("Этого трека нет в вашей библиотеке"); return }
        val pos = (p.optDouble("pos", 0.0) * 1000).toLong() + (if (p.optBoolean("pl")) 300L else 0L)
        ignoreUntil = System.currentTimeMillis() + 2000
        if (c.currentMediaItem?.mediaId != k) {
            val arr = p.optJSONArray("q")
            val q = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                tracks.firstOrNull { it.path == arr.optString(i) }
            }
            val list = (listOf(t) + q).distinctBy { it.path }
            c.setMediaItems(list.map { it.toItem() }, 0, pos)
            c.prepare()
        } else if (abs(c.currentPosition - pos) > 1500) {
            c.seekTo(pos)
        }
        if (p.optBoolean("pl")) c.play() else c.pause()
    }
}
