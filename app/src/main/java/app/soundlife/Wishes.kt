package app.soundlife

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * «Хочу этот трек»: отрывки iTunes, которые хочется получить полностью.
 * Как на сайте: хранится на устройстве и в таблице wishes (ключ it:<id iTunes>).
 */
object Wishes {
    data class W(val id: String, val t: String, val a: String, val al: String, val img: String, val url: String)

    var list by mutableStateOf<List<W>>(emptyList())
        private set
    private var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        list = parse(prefs(ctx).getString("wishes", "[]") ?: "[]")
    }

    fun has(id: String) = list.any { it.id == id }

    fun toTrack(w: W) = Track(path = "pv:" + w.id, title = w.t, artist = w.a, album = w.al, url = w.url, img = w.img, preview = true)

    /** Нажатие «+» у отрывка: добавить или убрать. */
    fun toggle(t: Track) {
        val id = t.path.removePrefix("pv:")
        if (has(id)) {
            list = list.filter { it.id != id }
            bg { remoteDelete(id) }
        } else {
            val w = W(id, t.title, t.artist, t.album, t.img, t.url)
            list = (listOf(w) + list).take(300)
            bg { remoteUpsert(w) }
        }
        save()
    }

    /** Загрузка с аккаунта: добавляет то, чего нет на устройстве. Вызывать в фоне. */
    fun pull() {
        val uid = Auth.userId() ?: return
        val tk = Auth.token() ?: return
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/wishes?select=key,t,a,al&user_id=eq.$uid", tk, null, null))
        val cur = list.toMutableList()
        var changed = false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val key = o.optString("key")
            if (!key.startsWith("it:")) continue
            val id = key.removePrefix("it:")
            if (id.isEmpty() || cur.any { it.id == id }) continue
            cur.add(resolve(W(id, o.optString("t"), o.optString("a"), o.optString("al"), "", "")))
            changed = true
        }
        if (changed) {
            list = cur.take(300)
            save()
        }
    }

    /** Для записи с аккаунта нет ссылки на отрывок: ищем его в iTunes. */
    private fun resolve(w: W): W = try {
        ITunes.search("${w.a} ${w.t}").firstOrNull { it.path == "pv:${w.id}" }
            ?.let { W(w.id, w.t, w.a, w.al, it.img, it.url) } ?: w
    } catch (e: Exception) {
        w
    }

    private fun remoteUpsert(w: W) {
        val uid = Auth.userId() ?: return
        val tk = Auth.token() ?: return
        val body = JSONArray().put(
            JSONObject().put("user_id", uid).put("key", "it:${w.id}").put("t", w.t).put("a", w.a).put("al", w.al),
        )
        Sync.rest("POST", "/rest/v1/wishes?on_conflict=user_id,key", tk, body.toString(), "resolution=merge-duplicates,return=minimal")
    }

    private fun remoteDelete(id: String) {
        val uid = Auth.userId() ?: return
        val tk = Auth.token() ?: return
        Sync.rest("DELETE", "/rest/v1/wishes?user_id=eq.$uid&key=eq.it:$id", tk, null, null)
    }

    /** Ошибки сети не мешают работе: список на устройстве уже сохранён. */
    private fun bg(block: () -> Unit) {
        Thread { try { block() } catch (_: Exception) { } }.start()
    }

    private fun save() {
        val ctx = appCtx ?: return
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(JSONObject().put("id", w.id).put("t", w.t).put("a", w.a).put("al", w.al).put("img", w.img).put("url", w.url))
        }
        prefs(ctx).edit().putString("wishes", arr.toString()).apply()
    }

    private fun parse(s: String): List<W> {
        val arr = try { JSONArray(s) } catch (e: Exception) { return emptyList() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            W(o.optString("id"), o.optString("t"), o.optString("a"), o.optString("al"), o.optString("img"), o.optString("url"))
        }.filter { it.id.isNotEmpty() }
    }
}
