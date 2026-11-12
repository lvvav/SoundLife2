package app.soundlife

import org.json.JSONObject
import java.net.URLEncoder

/**
 * Тексты песен, как на сайте: сначала свои из music/lyrics.json (ключ — путь файла),
 * потом LRCLIB. Вызывать в фоновом потоке.
 */
object Lyrics {
    /** lines — строки с таймкодами (мс), plain — текст без таймкодов. */
    class Result(val lines: List<Pair<Long, String>>, val plain: String, val instrumental: Boolean)

    private val TS = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]")
    private val cache = HashMap<String, Result>()
    private val NONE = Result(emptyList(), "", false) // «не найдено», чтобы не искать повторно
    private var own: Map<String, String>? = null

    fun get(path: String, title: String, artist: String, durSec: Long): Result? {
        synchronized(cache) { cache[path]?.let { return if (it === NONE) null else it } }
        // ошибку сети не кэшируем: при следующем открытии попробуем снова
        val r = try { fetch(path, title, artist, durSec) } catch (e: Exception) { return null }
        synchronized(cache) { cache[path] = r ?: NONE }
        return r
    }

    private fun fetch(path: String, title: String, artist: String, durSec: Long): Result? {
        ownLyrics()[path]?.let { return fromText(it) }
        val a = artist.takeIf { it.isNotBlank() && it != "—" }.orEmpty()
        val base = "https://lrclib.net/api/"
        val dur = if (durSec > 0) "&duration=$durSec" else ""
        try {
            val j = JSONObject(httpGet(base + "get?track_name=" + q(title) + "&artist_name=" + q(a) + dur, "application/json"))
            fromLrc(j)?.let { return it }
        } catch (_: Exception) {
            // не найдено — пробуем поиск ниже
        }
        val list = org.json.JSONArray(httpGet(base + "search?track_name=" + q(title) + (if (a.isNotEmpty()) "&artist_name=" + q(a) else ""), "application/json"))
        for (i in 0 until list.length()) {
            fromLrc(list.getJSONObject(i))?.let { return it }
        }
        return null
    }

    /** Свои тексты из всех хранилищ GitHub; первое найденное побеждает. */
    @Synchronized
    private fun ownLyrics(): Map<String, String> {
        own?.let { return it }
        val out = HashMap<String, String>()
        val folder = BuildConfig.GH_FOLDER
        BuildConfig.GH_REPOS.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { rp ->
            try {
                val j = JSONObject(httpGet(Library.rawUrl(rp, "$folder/lyrics.json"), "application/json"))
                j.keys().forEach { k ->
                    val v = j.optString(k)
                    if (v.isNotBlank() && !out.containsKey(k)) out[k] = v
                }
            } catch (_: Exception) {
                // lyrics.json не обязателен
            }
        }
        own = out
        return out
    }

    private fun fromText(s: String): Result {
        val lines = parseLrc(s)
        return Result(lines, TS.replace(s, "").trim(), false)
    }

    private fun fromLrc(j: JSONObject): Result? {
        if (j.optBoolean("instrumental")) return Result(emptyList(), "", true)
        val synced = j.optString("syncedLyrics")
        val plain = j.optString("plainLyrics")
        if (synced.isBlank() && plain.isBlank()) return null
        return Result(if (synced.isNotBlank()) parseLrc(synced) else emptyList(), plain.ifBlank { TS.replace(synced, "").trim() }, false)
    }

    private fun parseLrc(s: String): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        s.lines().forEach { ln ->
            val ts = TS.findAll(ln).toList()
            if (ts.isEmpty()) return@forEach
            val text = ln.substring(ts.last().range.last + 1).trim()
            ts.forEach { m ->
                val ms = (m.groupValues[1].toDouble() * 60 + m.groupValues[2].toDouble()) * 1000
                out.add(ms.toLong() to text)
            }
        }
        return out.sortedBy { it.first }
    }

    private fun q(s: String): String = URLEncoder.encode(s, "UTF-8")
}
