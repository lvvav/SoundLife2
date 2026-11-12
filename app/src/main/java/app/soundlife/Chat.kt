package app.soundlife

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Личные сообщения, как на сайте: таблицы messages и message_reactions, бакет voice для вложений.
 * Видеокружки записываются только на сайте: в приложении их можно открыть.
 */
object Chat {
    /** Реакции — те же, что на сайте. */
    val EMOJI = listOf("❤️", "👍", "😂", "🔥", "😮", "😢")

    data class Msg(
        val id: String, val sender: String, val recipient: String, val body: String,
        val track: String, val created: String, val readAt: String?,
    )

    /** Диалог: собеседник, последнее сообщение и число непрочитанных. */
    data class Dialog(val peer: String, val last: Msg?, val unread: Int)

    private const val SEL = "select=id,sender,recipient,body,track,created_at,read_at"
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }

    private fun token(): String = Auth.token() ?: throw RuntimeException("Сначала войдите в аккаунт")
    private fun me(): String = Auth.userId() ?: throw RuntimeException("Сначала войдите в аккаунт")

    /** Диалоги: по одному на собеседника, самое свежее сверху. Вызывать в фоне. */
    fun inbox(): List<Dialog> {
        val u = me()
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/messages?$SEL&or=(sender.eq.$u,recipient.eq.$u)&order=created_at.desc&limit=300", token(), null, null))
        val seen = LinkedHashMap<String, Pair<Msg, Int>>()
        for (i in 0 until arr.length()) {
            val m = msg(arr.getJSONObject(i))
            val peer = if (m.sender == u) m.recipient else m.sender
            val unreadAdd = if (m.recipient == u && m.readAt == null) 1 else 0
            val prev = seen[peer]
            seen[peer] = if (prev == null) m to unreadAdd else prev.first to prev.second + unreadAdd
        }
        return seen.map { (peer, v) -> Dialog(peer, v.first, v.second) }
    }

    /** Переписка с человеком, от старых к новым. */
    fun thread(peer: String): List<Msg> {
        val u = me()
        val f = "or=(and(sender.eq.$u,recipient.eq.$peer),and(sender.eq.$peer,recipient.eq.$u))"
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/messages?$SEL&$f&order=created_at.desc&limit=150", token(), null, null))
        return (0 until arr.length()).map { msg(arr.getJSONObject(it)) }.reversed()
    }

    /** Загрузка файла в бакет voice: путь uid/имя. Возвращает значение для поля track: kind/uid/имя. */
    fun upload(kind: String, name: String, bytes: ByteArray, mime: String): String {
        val u = me()
        val req = Request.Builder()
            .url("${BuildConfig.SUPA_URL}/storage/v1/object/voice/$u/$name")
            .header("apikey", BuildConfig.SUPA_KEY)
            .header("Authorization", "Bearer ${token()}")
            .post(bytes.toRequestBody(mime.toMediaType()))
            .build()
        http.newCall(req).execute().use { if (!it.isSuccessful) throw RuntimeException("Файл не загружен (HTTP ${it.code})") }
        return "$kind/$u/$name"
    }

    /** Публичная ссылка на вложение по значению track (voice/..., media/..., circle/...). */
    fun url(track: String): String = "${BuildConfig.SUPA_URL}/storage/v1/object/public/voice/" + track.substringAfter('/')

    fun send(peer: String, body: String, track: String? = null): Msg {
        val row = JSONObject().put("recipient", peer).put("body", body)
        if (track != null) row.put("track", track)
        val res = Sync.rest("POST", "/rest/v1/messages?$SEL", token(), row.toString(), "return=representation")
        return msg(JSONArray(res).getJSONObject(0))
    }

    /** Отмечает все входящие от собеседника как прочитанные (PATCH, через OkHttp: HttpURLConnection не умеет PATCH). */
    fun markRead(peer: String) {
        val u = me()
        val body = JSONObject().put("read_at", Instant.now().toString()).toString()
        val req = Request.Builder()
            .url("${BuildConfig.SUPA_URL}/rest/v1/messages?recipient=eq.$u&sender=eq.$peer&read_at=is.null")
            .header("apikey", BuildConfig.SUPA_KEY)
            .header("Authorization", "Bearer ${token()}")
            .header("Prefer", "return=minimal")
            .patch(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { if (!it.isSuccessful) throw RuntimeException("HTTP ${it.code}") }
    }

    /** Реакции к сообщениям: messageId -> (uid -> emoji). */
    fun reactions(ids: List<String>): Map<String, Map<String, String>> {
        if (ids.isEmpty()) return emptyMap()
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/message_reactions?select=message_id,user_id,emoji&message_id=in.(${ids.joinToString(",")})", token(), null, null))
        val out = HashMap<String, MutableMap<String, String>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.getOrPut(o.optString("message_id")) { HashMap() }[o.optString("user_id")] = o.optString("emoji")
        }
        return out
    }

    /** Поставить реакцию или убрать свою. */
    fun setReaction(msgId: String, emoji: String, remove: Boolean) {
        val u = me()
        if (remove) {
            Sync.rest("DELETE", "/rest/v1/message_reactions?message_id=eq.$msgId&user_id=eq.$u", token(), null, null)
        } else {
            val row = JSONObject().put("message_id", msgId).put("user_id", u).put("emoji", emoji)
            Sync.rest("POST", "/rest/v1/message_reactions?on_conflict=message_id,user_id", token(), row.toString(), "resolution=merge-duplicates,return=minimal")
        }
    }

    private fun msg(o: JSONObject) = Msg(
        id = o.optString("id"),
        sender = o.optString("sender"),
        recipient = o.optString("recipient"),
        body = if (o.isNull("body")) "" else o.optString("body"),
        track = if (o.isNull("track")) "" else o.optString("track"),
        created = o.optString("created_at"),
        readAt = if (o.isNull("read_at")) null else o.optString("read_at"),
    )
}
