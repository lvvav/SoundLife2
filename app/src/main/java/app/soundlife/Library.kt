package app.soundlife

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Track(
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val url: String,
    /** Обложка: картинка рядом с треком в GitHub или картинка из iTunes. Пусто = нет. */
    val img: String = "",
    /** true = 30-секундный отрывок из iTunes; в библиотеку не попадает. */
    val preview: Boolean = false,
)

/** Правило для файлов без тегов (как RULES в index.html). */
private class Rule(val test: Regex, val artist: String, val album: String)

private val RULES = listOf(
    Rule(Regex("^(0[1-9]|1[0-4])\\s*[.)]\\s"), "Валентин Стрыкало", "Часть чего-то большего"),
    Rule(Regex("^(92|Бесполезно|Делать это трезвым|О брат|Подворотня - мой дом|Решится само собой|Тени|Ускользает)$"), "Валентин Стрыкало", "Развлечение"),
)

private val AUDIO = Regex(".*\\.(mp3|flac|wav|m4a|ogg|opus|aac)$", RegexOption.IGNORE_CASE)
private val IMG = Regex(".*\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE)
private val NUM = Regex("^\\d{1,3}\\s*[.)\\-]\\s*")
private val IMG_EXT = listOf("jpg", "jpeg", "png", "webp")
private val COVER_NAMES = listOf("cover", "folder", "artwork")

/** Простой GET. Бросает исключение при ошибке. */
internal fun httpGet(url: String, accept: String = "*/*"): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 15000
    c.readTimeout = 30000
    c.setRequestProperty("Accept", accept)
    c.setRequestProperty("User-Agent", "SoundLife-Android")
    if (c.responseCode !in 200..299) throw RuntimeException("HTTP ${c.responseCode}")
    return c.inputStream.bufferedReader().use { it.readText() }
}

/** Библиотека: треки из нескольких хранилищ GitHub (папка music), с обложками и meta.json. */
object Library {
    fun load(ctx: Context): List<Track> {
        val folder = BuildConfig.GH_FOLDER
        val repos = BuildConfig.GH_REPOS.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val byPath = LinkedHashMap<String, Track>()
        val meta = HashMap<String, JSONObject>()
        var lastErr: Exception? = null

        for (rp in repos) {
            val tree = try {
                fetchTree(ctx, rp)
            } catch (e: Exception) {
                lastErr = e
                continue
            }
            val files = (0 until tree.length()).map { tree.getJSONObject(it) }
                .filter { it.optString("type") == "blob" && it.optString("path").startsWith("$folder/") }
                .map { it.optString("path") }

            val imgs = HashMap<String, String>()
            files.filter { IMG.matches(it) }.forEach { imgs[it.lowercase()] = rawUrl(rp, it) }
            // Если трек уже есть из другого хранилища, берётся первый (как на сайте)
            files.filter { AUDIO.matches(it) && it !in byPath }
                .forEach { p -> byPath[p] = toTrack(p, rp, imgs, folder) }

            if ("$folder/meta.json" in files) {
                try {
                    val j = JSONObject(httpGet(rawUrl(rp, "$folder/meta.json"), "application/json"))
                    j.keys().forEach { k -> j.optJSONObject(k)?.let { meta[k] = it } }
                } catch (e: Exception) {
                    // meta.json не обязателен
                }
            }
        }

        if (byPath.isEmpty()) throw (lastErr ?: RuntimeException("В хранилищах нет треков"))

        return byPath.values
            .map { it.withMeta(meta[it.path]) }
            // одинаковые песни (артист + название) показываем один раз; предпочитаем FLAC
            .groupBy { dupKey(it) }
            .values
            .map { group -> group.firstOrNull { it.path.endsWith(".flac", ignoreCase = true) } ?: group.first() }
            .sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() }))
    }

    /** Список файлов дерева; при сбое сети берётся сохранённая копия. */
    private fun fetchTree(ctx: Context, rp: String): JSONArray {
        val cache = File(ctx.filesDir, "tree_" + rp.replace('/', '_') + ".json")
        val json = try {
            httpGet(
                "https://api.github.com/repos/$rp/git/trees/${BuildConfig.GH_BRANCH}?recursive=1",
                "application/vnd.github+json",
            ).also { cache.writeText(it) }
        } catch (e: Exception) {
            if (cache.exists()) cache.readText() else throw e
        }
        return JSONObject(json).optJSONArray("tree") ?: JSONArray()
    }

    internal fun rawUrl(rp: String, p: String): String =
        "https://raw.githubusercontent.com/$rp/${BuildConfig.GH_BRANCH}/" + p.split('/').joinToString("/") { Uri.encode(it) }

    private fun toTrack(p: String, rp: String, imgs: Map<String, String>, folder: String): Track {
        val parts = p.removePrefix("$folder/").split('/')
        val file = parts.last().substringBeforeLast('.').replace('_', ' ').trim()
        val folderName = if (parts.size > 1) parts[parts.size - 2] else ""
        val url = rawUrl(rp, p)
        val img = coverFor(p, imgs)

        RULES.firstOrNull { it.test.containsMatchIn(file) }?.let { r ->
            return Track(p, file.replace(NUM, ""), r.artist, r.album, url, img)
        }
        val name = file.replace(NUM, "")
        val dash = name.indexOf(" - ")
        val artist = if (dash > 0) name.substring(0, dash).trim() else folderName.ifBlank { "Неизвестный артист" }
        val title = if (dash > 0) name.substring(dash + 3).trim() else name
        return Track(p, title, artist, folderName, url, img)
    }

    /** Картинка с тем же именем, либо cover / folder / artwork в той же папке. */
    private fun coverFor(p: String, imgs: Map<String, String>): String {
        val base = p.substringBeforeLast('.').lowercase()
        val dir = p.substringBeforeLast('/', "").lowercase()
        IMG_EXT.forEach { e -> imgs["$base.$e"]?.let { return it } }
        COVER_NAMES.forEach { n -> IMG_EXT.forEach { e -> imgs["$dir/$n.$e"]?.let { return it } } }
        return ""
    }

    /** Поля из meta.json (ключ — путь файла): t, a, al. */
    private fun Track.withMeta(o: JSONObject?): Track {
        if (o == null) return this
        return copy(
            title = o.optString("t").ifBlank { title },
            artist = o.optString("a").ifBlank { artist },
            album = o.optString("al").ifBlank { album },
        )
    }

    private fun dupKey(t: Track): String =
        "${t.artist} ${t.title}".lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}

/** Поиск в iTunes: 30-секундные отрывки. Ключ не нужен. */
object ITunes {
    fun search(q: String): List<Track> {
        val url = "https://itunes.apple.com/search?term=" + URLEncoder.encode(q, "UTF-8") +
            "&media=music&entity=song&limit=12"
        val arr = JSONObject(httpGet(url, "application/json")).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Track>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val preview = o.optString("previewUrl")
            val title = o.optString("trackName")
            if (preview.isBlank() || title.isBlank()) continue
            out += Track(
                path = "pv:" + o.optLong("trackId"),
                title = title,
                artist = o.optString("artistName"),
                album = o.optString("collectionName"),
                url = preview,
                img = o.optString("artworkUrl100").replace("100x100", "300x300"),
                preview = true,
            )
        }
        return out.distinctBy { (it.artist + " " + it.title).lowercase() }
    }
}
