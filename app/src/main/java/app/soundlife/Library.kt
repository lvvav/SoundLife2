package app.soundlife

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Track(
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val url: String,
)

/** Библиотека: список mp3 из папки music на GitHub (так же, как это делает сайт). */
object Library {
    private val AUDIO = Regex(".*\\.(mp3|flac|wav|m4a|ogg|opus|aac)$", RegexOption.IGNORE_CASE)
    private val NUM = Regex("^\\d{1,3}\\s*[.)\\-]\\s*")

    private fun http(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "SoundLife-Android")
        if (c.responseCode !in 200..299) throw RuntimeException("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /** Сначала сеть, при ошибке — кэш (работает офлайн). */
    fun load(ctx: Context): List<Track> {
        val cache = File(ctx.filesDir, "tree.json")
        val json = try {
            http("https://api.github.com/repos/${BuildConfig.GH_REPO}/git/trees/${BuildConfig.GH_BRANCH}?recursive=1")
                .also { cache.writeText(it) }
        } catch (e: Exception) {
            if (cache.exists()) cache.readText() else throw e
        }
        return parse(json)
    }

    private fun parse(json: String): List<Track> {
        val tree = JSONObject(json).optJSONArray("tree") ?: return emptyList()
        val prefix = BuildConfig.GH_FOLDER + "/"
        val out = ArrayList<Track>()
        for (i in 0 until tree.length()) {
            val n = tree.getJSONObject(i)
            val p = n.optString("path")
            if (n.optString("type") != "blob" || !p.startsWith(prefix) || !AUDIO.matches(p)) continue
            out += toTrack(p)
        }
        return out.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() }))
    }

    private fun toTrack(path: String): Track {
        val parts = path.removePrefix(BuildConfig.GH_FOLDER + "/").split('/')
        val file = parts.last().substringBeforeLast('.').replace('_', ' ').trim()
        val folder = if (parts.size > 1) parts[parts.size - 2] else ""
        val name = file.replace(NUM, "")
        val dash = name.indexOf(" - ")
        val artist = if (dash > 0) name.substring(0, dash).trim() else folder.ifBlank { "Неизвестный артист" }
        val title = if (dash > 0) name.substring(dash + 3).trim() else name
        val url = "https://raw.githubusercontent.com/${BuildConfig.GH_REPO}/${BuildConfig.GH_BRANCH}/" +
            path.split('/').joinToString("/") { Uri.encode(it) }
        return Track(path, title, artist, folder, url)
    }
}
