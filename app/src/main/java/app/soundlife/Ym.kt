package app.soundlife

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class YmTrack(val id: String, val title: String, val artist: String, val album: String, val img: String)
data class YmAlbum(val id: String, val title: String, val artist: String, val year: String, val img: String)
data class YmArtist(val id: String, val name: String, val img: String)
data class YmResults(val tracks: List<YmTrack>, val albums: List<YmAlbum>, val artists: List<YmArtist>)

/** Яндекс Музыка через серверную функцию ymusic (та же, что у сайта). Работает только для владельца. */
object Ym {
    class YmError(val code: String, message: String) : Exception(message)

    private fun call(body: JSONObject): JSONObject {
        val tk = Auth.token() ?: throw YmError("login", ymMessage("login"))
        val c = URL("${BuildConfig.SUPA_URL}/functions/v1/ymusic").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("apikey", BuildConfig.SUPA_KEY)
        c.setRequestProperty("Authorization", "Bearer $tk")
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }

        val text = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: "{}"
        val j = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
        val err = j.optString("error")
        if (err.isNotEmpty()) throw YmError(err, j.optString("detail").ifBlank { ymMessage(err) })
        return j
    }

    /** true — аккаунт владельца, Яндекс доступен. */
    fun isOwner(): Boolean = try {
        call(JSONObject().put("a", "status"))
        true
    } catch (e: Exception) {
        false
    }

    fun search(q: String): YmResults {
        val j = call(JSONObject().put("a", "search").put("q", q))
        return YmResults(
            tracks = j.list("tracks") { ymTrack(it) },
            albums = j.list("albums") { ymAlbum(it) },
            artists = j.list("artists") { ymArtist(it) },
        )
    }

    /** Временная ссылка на поток полного трека. */
    fun streamUrl(id: String): String {
        val u = call(JSONObject().put("a", "url").put("id", id)).optString("url")
        if (u.isEmpty()) throw YmError("url", ymMessage("url"))
        return u
    }

    /** Альбом: его описание и треки по дискам. */
    fun album(id: String): Pair<YmAlbum?, List<YmTrack>> {
        val j = call(JSONObject().put("a", "album").put("id", id))
        val tracks = ArrayList<YmTrack>()
        val discs = j.optJSONArray("discs")
        if (discs != null) {
            for (d in 0 until discs.length()) {
                val disc = discs.optJSONArray(d) ?: continue
                for (i in 0 until disc.length()) tracks += ymTrack(disc.getJSONObject(i))
            }
        }
        return Pair(j.optJSONObject("album")?.let { ymAlbum(it) }, tracks)
    }

    /** Исполнитель: его описание, треки и альбомы. */
    fun artist(id: String): Triple<YmArtist?, List<YmTrack>, List<YmAlbum>> {
        val j = call(JSONObject().put("a", "artist").put("id", id))
        return Triple(
            j.optJSONObject("artist")?.let { ymArtist(it) },
            j.list("tracks") { ymTrack(it) },
            j.list("albums") { ymAlbum(it) },
        )
    }

    /** Трек Яндекса для плеера: адрес потока подставит сервис в момент воспроизведения. */
    fun asTrack(r: YmTrack): Track = Track(
        path = "ym:${r.id}",
        title = r.title,
        artist = r.artist,
        album = r.album.ifBlank { "Яндекс Музыка" },
        url = "soundlife-ym://${r.id}",
        img = r.img,
    )

    private fun ymTrack(o: JSONObject) =
        YmTrack(o.optString("id"), o.optString("t"), o.optString("a"), o.optString("al"), o.optString("img"))

    private fun ymAlbum(o: JSONObject) =
        YmAlbum(o.optString("id"), o.optString("t"), o.optString("a"), o.optString("y"), o.optString("img"))

    private fun ymArtist(o: JSONObject) =
        YmArtist(o.optString("id"), o.optString("t").ifBlank { o.optString("n") }, o.optString("img"))

    private fun <T> JSONObject.list(key: String, f: (JSONObject) -> T): List<T> {
        val a = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { f(a.getJSONObject(it)) }
    }

    private fun ymMessage(code: String): String = when (code) {
        "login" -> "Войдите в аккаунт SoundLife, чтобы играть из Яндекс Музыки"
        "denied", "no_owner" -> "Яндекс Музыка доступна только владельцу сайта"
        "preview" -> "Яндекс отдаёт только 30 секунд: проверьте подписку Плюс"
        "token" -> "Яндекс не принял токен: обновите секрет YANDEX_TOKEN"
        else -> "Не удалось получить трек из Яндекс Музыки"
    }
}
