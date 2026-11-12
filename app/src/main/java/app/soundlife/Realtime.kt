package app.soundlife

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Клиент Supabase Realtime (протокол Phoenix 1.0.0), только broadcast.
 * Нужен для сигналов звонков и совместного прослушивания. Соединение переподключается само,
 * раз в 50 минут переоткрывается, чтобы взять свежий токен.
 */
object Realtime {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val refs = AtomicInteger(1)
    private val handlers = HashMap<String, (String, JSONObject) -> Unit>()
    private val joined = HashSet<String>()
    private val waiting = HashMap<String, CompletableDeferred<Boolean>>()
    private var ws: WebSocket? = null
    private var open = false
    private var connectedAt = 0L
    private var loopStarted = false

    /** Подписаться на канал и получать его broadcast-события (событие, payload). */
    fun listen(topic: String, onEvent: (String, JSONObject) -> Unit) {
        synchronized(lock) { handlers[topic] = onEvent }
        connect()
        if (open) join(topic)
    }

    /** Отписаться от канала и выйти из него. */
    fun unlisten(topic: String) {
        synchronized(lock) {
            handlers.remove(topic)
            joined.remove(topic)
        }
        send(topic, "phx_leave", JSONObject())
    }

    /** Отправить broadcast в чужой канал. Присоединяется к каналу, дожидается ответа и шлёт. */
    suspend fun sendTo(topic: String, event: String, payload: JSONObject): Boolean {
        connect()
        if (!waitOpen(5000)) return false
        if (!ensureJoined(topic)) return false
        val msg = JSONObject().put("type", "broadcast").put("event", event).put("payload", payload)
        return send(topic, "broadcast", msg)
    }

    /** Запустить фоновый цикл: сердцебиение каждые 25 с и переподключение по сроку токена. */
    fun start() {
        synchronized(lock) {
            if (loopStarted) return
            loopStarted = true
        }
        scope.launch {
            while (true) {
                delay(25_000)
                val w = synchronized(lock) { if (open) ws else null } ?: continue
                send("phoenix", "heartbeat", JSONObject())
                if (System.currentTimeMillis() - connectedAt > 50 * 60_000L) w.close(1000, "refresh")
            }
        }
    }

    private fun connect() {
        synchronized(lock) { if (ws != null) return }
        if (Auth.userId() == null) return
        val base = BuildConfig.SUPA_URL.replaceFirst("https://", "wss://")
        val req = Request.Builder().url("$base/realtime/v1/websocket?apikey=${BuildConfig.SUPA_KEY}&vsn=1.0.0").build()
        synchronized(lock) {
            ws = http.newWebSocket(req, listener)
            connectedAt = System.currentTimeMillis()
        }
    }

    private suspend fun waitOpen(ms: Long): Boolean {
        val end = System.currentTimeMillis() + ms
        while (!synchronized(lock) { open } && System.currentTimeMillis() < end) delay(200)
        return synchronized(lock) { open }
    }

    private suspend fun ensureJoined(topic: String): Boolean {
        if (synchronized(lock) { joined.contains(topic) }) return true
        val d = CompletableDeferred<Boolean>()
        synchronized(lock) { waiting[topic] = d }
        join(topic)
        return withTimeoutOrNull(4000) { d.await() } ?: false
    }

    private fun join(topic: String) {
        val tk = Auth.token() ?: return
        val cfg = JSONObject()
            .put("broadcast", JSONObject().put("self", false).put("ack", false))
            .put("presence", JSONObject().put("key", ""))
            .put("postgres_changes", JSONArray())
        send(topic, "phx_join", JSONObject().put("config", cfg).put("access_token", tk))
    }

    private fun send(topic: String, event: String, payload: JSONObject): Boolean {
        val w = synchronized(lock) { ws } ?: return false
        val o = JSONObject()
            .put("topic", topic).put("event", event).put("payload", payload)
            .put("ref", refs.getAndIncrement().toString())
        return w.send(o.toString())
    }

    private fun drop() {
        synchronized(lock) {
            ws = null
            open = false
            joined.clear()
            waiting.values.forEach { it.complete(false) }
            waiting.clear()
        }
        scope.launch {
            delay(10_000)
            connect()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val topics = synchronized(lock) {
                open = true
                joined.clear()
                handlers.keys.toList()
            }
            topics.forEach { join(it) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val o = try { JSONObject(text) } catch (e: Exception) { return }
            val topic = o.optString("topic")
            val event = o.optString("event")
            val p = o.optJSONObject("payload") ?: JSONObject()
            when (event) {
                "phx_reply" -> {
                    val ok = p.optString("status") == "ok"
                    val d = synchronized(lock) {
                        if (ok) joined.add(topic)
                        waiting.remove(topic)
                    }
                    d?.complete(ok)
                }
                "broadcast" -> {
                    val h = synchronized(lock) { handlers[topic] }
                    h?.invoke(p.optString("event"), p.optJSONObject("payload") ?: JSONObject())
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = drop()
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = drop()
    }
}
