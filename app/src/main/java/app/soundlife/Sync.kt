package app.soundlife

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Синхронизация с аккаунтом сайта: та же строка таблицы user_data.
 * Поле favs (лайки) — то же, что на сайте. Подписки и Яндекс-подписки лежат в своём блоке native_subs,
 * сайт его не читает и не трогает. Слияние: объединение, удалений между устройствами пока нет.
 */
object Sync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private var job: Job? = null

    /** Отложенная синхронизация: вызывается после каждого изменения. */
    fun schedule() {
        job?.cancel()
        job = scope.launch {
            delay(3000)
            try {
                runOnce()
            } catch (e: Exception) {
                // офлайн или нет входа: попробуем при следующем изменении или запуске
            }
        }
    }

    /** Один цикл: скачать, объединить с локальным, записать обратно. Без входа — ничего не делает. */
    fun runOnce() {
        val uid = Auth.userId() ?: return
        val tk = Auth.token() ?: return

        val rows = JSONArray(rest("GET", "/rest/v1/user_data?user_id=eq.$uid&select=data,updated_at", tk, null, null))
        val data = if (rows.length() > 0) rows.getJSONObject(0).optJSONObject("data") ?: JSONObject() else JSONObject()
        // Настройки сайта (поле set): читаем только акцентный цвет, сами настройки сайта не трогаем
        val siteSet = data.optJSONObject("set")
        val siteAcc = siteSet?.optJSONObject("v")?.optString("acc").orEmpty()
        val siteBg = siteSet?.optJSONObject("v")?.optString("bg").orEmpty()
        val siteT = siteSet?.optLong("t", 0L) ?: 0L

        // Лайки: объединяем с тем, что уже лежит на сайте
        val favs = LinkedHashSet<String>()
        data.optJSONArray("favs")?.let { a -> for (i in 0 until a.length()) favs += a.optString(i) }
        favs += Favorites.ids

        // Подписки: объединяем свой блок
        val ns = data.optJSONObject("native_subs") ?: JSONObject()
        val artists = ns.optJSONArray("artists").strings() + Subs.artists
        val albums = ns.optJSONArray("albums").strings() + Subs.albums
        val remoteYm = ns.optJSONArray("ym")?.let { a -> (0 until a.length()).map { ymSubFrom(a.getJSONObject(it)) } }.orEmpty()
        val ymAll = (remoteYm + Subs.ym).distinctBy { it.kind + it.id }

        data.put("favs", JSONArray(favs.toList()))
        data.put(
            "native_subs",
            JSONObject()
                .put("artists", JSONArray(artists.toList()))
                .put("albums", JSONArray(albums.toList()))
                .put("ym", JSONArray().apply { ymAll.forEach { put(ymSubTo(it)) } }),
        )

        // Плейлисты: как на сайте — массив {id, n, k:[ключи треков]}; объединяем по названию.
        // Совместные плейлисты (collab) сайта не трогаем, они лежат в других таблицах.
        val remotePl = data.optJSONArray("pl") ?: JSONArray()
        val remoteMap = LinkedHashMap<String, LinkedHashSet<String>>()
        val collabKeep = JSONArray()
        for (i in 0 until remotePl.length()) {
            val p = remotePl.getJSONObject(i)
            if (p.optBoolean("collab")) { collabKeep.put(p); continue }
            val name = p.optString("n")
            if (name.isEmpty()) continue
            val set = remoteMap.getOrPut(name) { LinkedHashSet() }
            p.optJSONArray("k")?.let { a -> for (j in 0 until a.length()) set += a.optString(j) }
        }
        val localPl = Playlists.lists
        val mergedPl = LinkedHashMap<String, List<String>>()
        for (name in remoteMap.keys + localPl.keys) {
            val keys = LinkedHashSet<String>()
            remoteMap[name]?.let { keys += it }
            localPl[name]?.let { keys += it }
            mergedPl[name] = keys.toList()
        }
        val plOut = JSONArray()
        for ((name, keys) in mergedPl) {
            plOut.put(
                JSONObject()
                    .put("id", "p" + Integer.toString(name.hashCode() and 0x7fffffff, 36))
                    .put("n", name)
                    .put("k", JSONArray(keys)),
            )
        }
        for (i in 0 until collabKeep.length()) plOut.put(collabKeep.get(i))
        data.put("pl", plOut)

        // Статистика аккаунта: st, dt, cnt, hist. Сливаем по максимуму и пишем обратно, ничего не сбрасываем.
        val stO = data.optJSONObject("st")
        val dtO = data.optJSONObject("dt")
        Stats.mergeFromSite(stO, dtO, data.optJSONObject("cnt"), data.optJSONArray("hist"))
        val (stOut, dtOut) = Stats.toSite(dtO?.optJSONObject("o"))
        data.put("st", stOut)
        data.put("dt", dtOut)
        data.put("cnt", Stats.cntJson())
        data.put("hist", Stats.histJson())

        // Локальные списки обновляем на главном потоке (состояние Compose)
        main.post {
            Favorites.mergeRemote(favs.toSet())
            Subs.mergeRemote(artists, albums, ymAll)
            Playlists.mergeRemote(mergedPl)
            Settings.applySite(siteAcc, siteBg, siteT)
        }

        val body = JSONArray().put(
            JSONObject().put("user_id", uid).put("data", data).put("updated_at", Instant.now().toString()),
        )
        rest("POST", "/rest/v1/user_data?on_conflict=user_id", tk, body.toString(), "resolution=merge-duplicates,return=minimal")
    }

    internal fun rest(method: String, path: String, tk: String, body: String?, prefer: String?): String {
        val c = URL("${BuildConfig.SUPA_URL}$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("apikey", BuildConfig.SUPA_KEY)
        c.setRequestProperty("Authorization", "Bearer $tk")
        c.setRequestProperty("Accept", "application/json")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            if (prefer != null) c.setRequestProperty("Prefer", prefer)
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val ok = c.responseCode in 200..299
        val text = (if (ok) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (!ok) throw RuntimeException("Sync HTTP ${c.responseCode}: $text")
        return text.ifBlank { "[]" }
    }

    private fun JSONArray?.strings(): Set<String> =
        if (this == null) emptySet() else (0 until length()).map { getString(it) }.toSet()

    private fun ymSubFrom(o: JSONObject) =
        YmSub(o.optString("kind"), o.optString("id"), o.optString("title"), o.optString("sub"), o.optString("img"))

    private fun ymSubTo(s: YmSub) = JSONObject()
        .put("kind", s.kind).put("id", s.id).put("title", s.title).put("sub", s.sub).put("img", s.img)
}
