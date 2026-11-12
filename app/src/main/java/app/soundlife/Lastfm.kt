package app.soundlife

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/** Мировая популярность трека (Last.fm): слушатели, прослушивания, теги. Как на сайте. */
object Lastfm {
    class Info(val listeners: Long, val plays: Long, val tags: List<String>)

    private val NONE = Info(0, 0, emptyList()) // «не найдено», чтобы не спрашивать повторно
    private val cache = HashMap<String, Info>()

    val enabled: Boolean get() = BuildConfig.LASTFM_KEY.isNotEmpty()

    /** Вызывать в фоновом потоке. null — нет данных или сеть недоступна. */
    fun get(artist: String, title: String): Info? {
        if (!enabled || artist.isBlank() || artist == "—" || title.isBlank()) return null
        val key = "${artist.lowercase()}|${title.lowercase()}"
        synchronized(cache) { cache[key]?.let { return if (it === NONE) null else it } }
        // при ошибке сети не кэшируем: попробуем в следующий раз
        val info = try { fetch(artist, title) } catch (e: Exception) { return null }
        synchronized(cache) { cache[key] = info ?: NONE }
        return info
    }

    private fun fetch(artist: String, title: String): Info? {
        val url = "https://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=${BuildConfig.LASTFM_KEY}" +
            "&format=json&autocorrect=1&artist=${enc(artist)}&track=${enc(title)}"
        val d = JSONObject(httpGet(url, "application/json"))
        if (d.has("error")) return null
        val t = d.optJSONObject("track") ?: return null
        val listeners = t.optString("listeners", "0").toLongOrNull() ?: 0L
        val plays = t.optString("playcount", "0").toLongOrNull() ?: 0L
        if (listeners == 0L && plays == 0L) return null
        // теги приходят то объектом, то массивом
        val raw = t.optJSONObject("toptags")?.opt("tag")
        val arr = when (raw) {
            is JSONArray -> raw
            is JSONObject -> JSONArray().put(raw)
            else -> JSONArray()
        }
        val tags = (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it)?.optString("name")?.lowercase()?.takeIf { n -> n.isNotBlank() && n != "seen live" } }
            .take(5)
        return Info(listeners, plays, tags)
    }

    /** 1,2 млн / 343 тыс. — как на сайте. */
    fun fmt(n: Long): String {
        for ((unit, name) in listOf(1e9 to "млрд", 1e6 to "млн", 1e3 to "тыс.")) {
            if (n >= unit) {
                val v = n / unit
                val s = if (v < 10) String.format(Locale.US, "%.1f", v).removeSuffix(".0") else String.format(Locale.US, "%.0f", v)
                return s.replace('.', ',') + " " + name
            }
        }
        return n.toString()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
