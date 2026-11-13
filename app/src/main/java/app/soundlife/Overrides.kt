package app.soundlife

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Правки из админки сайта: таблица album_edits (Supabase). Ключ трека — путь файла, обложка альбома — «cov:артист|альбом». */
object Overrides {
    private val http = OkHttpClient()

    class Ov(val tracks: Map<String, JSONObject>, val covers: Map<String, String>)

    fun fetch(): Ov = try {
        val arr = http.newCall(
            Request.Builder()
                .url(BuildConfig.SUPA_URL.trimEnd('/') + "/rest/v1/album_edits?select=key,data")
                .header("apikey", BuildConfig.SUPA_KEY)
                .header("authorization", "Bearer " + BuildConfig.SUPA_KEY)
                .build(),
        ).execute().use { r ->
            if (!r.isSuccessful) error("сервер ответил ${r.code}")
            JSONArray(r.body!!.string())
        }
        val tracks = HashMap<String, JSONObject>()
        val covers = HashMap<String, String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val key = o.optString("key")
            val d = o.optJSONObject("data") ?: continue
            if (key.startsWith("cov:")) {
                d.optString("img").takeIf { it.isNotBlank() }?.let { covers[key.removePrefix("cov:")] = it }
            } else {
                tracks[key] = d
            }
        }
        Ov(tracks, covers)
    } catch (e: Exception) {
        Ov(emptyMap(), emptyMap())
    }

    fun albumKey(artist: String, album: String): String = (artist + "|" + album).lowercase()

    fun apply(list: List<Track>): List<Track> {
        val ov = fetch()
        return list.map { t0 ->
            var t = t0
            ov.tracks[t.path]?.let { d ->
                fun s(k: String) = d.optString(k).trim()
                t = t.copy(
                    title = s("t").ifBlank { t.title },
                    artist = s("a").ifBlank { t.artist },
                    album = s("al").ifBlank { t.album },
                    albumArtist = s("aa").ifBlank { t.albumArtist },
                    year = s("y").ifBlank { t.year },
                    num = d.optInt("n", 0).takeIf { it > 0 } ?: t.num,
                    disc = d.optInt("d", 0).takeIf { it > 0 } ?: t.disc,
                )
            }
            ov.covers[albumKey(t.albumArtist.ifBlank { t.artist }, t.album)]?.let { t = t.copy(img = it) }
            t
        }
    }
}

/** Теги ID3v2 из mp3: читаем только начало файла (Range), результаты кэшируем на устройстве. */
object Id3 {
    private val http = OkHttpClient()
    private val lock = Any()
    private var cache: JSONObject? = null

    private fun cacheFile(ctx: Context) = File(ctx.filesDir, "id3cache.json")

    private fun loadCache(ctx: Context): JSONObject = synchronized(lock) {
        cache ?: (try { JSONObject(cacheFile(ctx).readText()) } catch (e: Exception) { JSONObject() }).also { cache = it }
    }

    private fun saveCache(ctx: Context) = synchronized(lock) {
        try { cacheFile(ctx).writeText(cache.toString()) } catch (_: Exception) {}
    }

    fun enrich(ctx: Context, list: List<Track>): List<Track> {
        val c = loadCache(ctx)
        val pool = Executors.newFixedThreadPool(6)
        val futures = list.map { t -> pool.submit<Track> { fromTag(c, t) } }
        val out = futures.map { it.get() }
        pool.shutdown()
        saveCache(ctx)
        return out
    }

    private fun fromTag(c: JSONObject, t: Track): Track {
        if (!t.path.endsWith(".mp3", ignoreCase = true)) return t
        val cached = c.optJSONObject(t.path)
        val tags = cached ?: readTags(t.url)?.also { c.put(t.path, it) } ?: return t
        fun s(k: String) = tags.optString(k).trim()
        return t.copy(
            title = s("title").ifBlank { t.title },
            artist = s("artist").ifBlank { t.artist },
            album = s("album").ifBlank { t.album },
            albumArtist = s("aa").ifBlank { t.albumArtist },
            year = s("year").ifBlank { t.year },
            num = tags.optInt("num", 0).takeIf { it > 0 } ?: t.num,
            disc = tags.optInt("disc", 0).takeIf { it > 0 } ?: t.disc,
        )
    }

    private fun range(url: String, n: Int): ByteArray? = http.newCall(
        Request.Builder().url(url).header("Range", "bytes=0-${n - 1}").build(),
    ).execute().use { r ->
        if (!r.isSuccessful) return null
        r.body!!.byteStream().use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (out.size() < n) {
                val k = input.read(buf, 0, minOf(buf.size, n - out.size()))
                if (k <= 0) break
                out.write(buf, 0, k)
            }
            out.toByteArray()
        }
    }

    /** null = ошибка сети (не кэшируем); пустой объект = тегов нет. */
    private fun readTags(url: String): JSONObject? = try {
        val head = range(url, 10) ?: return null
        val res = JSONObject()
        if (head.size < 10 || String(head, 0, 3, Charsets.ISO_8859_1) != "ID3") return res
        val ver = head[3].toInt()
        if (ver != 3 && ver != 4) return res
        val tagSize = synch(head, 6)
        val buf = range(url, minOf(10 + tagSize, 400_000)) ?: return null
        var pos = 10
        while (pos + 10 <= buf.size) {
            val id = String(buf, pos, 4, Charsets.ISO_8859_1)
            if (id[0] !in 'A'..'Z') break
            val fsize = if (ver == 4) synch(buf, pos + 4) else be32(buf, pos + 4)
            if (fsize <= 0 || pos + 10 + fsize > buf.size) break
            val data = buf.copyOfRange(pos + 10, pos + 10 + fsize)
            when (id) {
                "TIT2" -> res.put("title", text(data))
                "TPE1" -> res.put("artist", text(data))
                "TALB" -> res.put("album", text(data))
                "TPE2" -> res.put("aa", text(data))
                "TRCK" -> res.put("num", text(data).substringBefore('/').toIntOrNull() ?: 0)
                "TPOS" -> res.put("disc", text(data).substringBefore('/').toIntOrNull() ?: 0)
                "TYER", "TDRC" -> if (!res.has("year")) res.put("year", text(data).take(4))
            }
            pos += 10 + fsize
        }
        res
    } catch (e: Exception) {
        null
    }

    private fun synch(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7f) shl 21) or ((b[o + 1].toInt() and 0x7f) shl 14) or
            ((b[o + 2].toInt() and 0x7f) shl 7) or (b[o + 3].toInt() and 0x7f)

    private fun be32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xff) shl 24) or ((b[o + 1].toInt() and 0xff) shl 16) or
            ((b[o + 2].toInt() and 0xff) shl 8) or (b[o + 3].toInt() and 0xff)

    private fun text(d: ByteArray): String {
        if (d.isEmpty()) return ""
        val body = d.copyOfRange(1, d.size)
        val s = when (d[0].toInt()) {
            0 -> String(body, charset("windows-1251"))
            1 -> String(body, Charsets.UTF_16)
            2 -> String(body, Charsets.UTF_16BE)
            3 -> String(body, Charsets.UTF_8)
            else -> String(body, Charsets.UTF_8)
        }
        return s.trim('\u0000', ' ', '\n', '\r', '\t')
    }
}
