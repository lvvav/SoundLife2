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
    /** Артист альбома, год, номер и диск: из тегов файла, затем meta.json и правок админки. */
    val albumArtist: String = "",
    val year: String = "",
    val num: Int = 0,
    val disc: Int = 0,
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

/** Фото артистов из meta.json «@artists» (как на сайте): ключ — имя артиста в нижнем регистре, значение — готовая ссылка. */
/** Последний загруженный каталог: нужен синхронизации, чтобы сопоставить сохранённые альбомы и артистов сайта. */
internal object LibraryCache {
    @Volatile var tracks: List<Track> = emptyList()
}

internal object ArtistPhotos {
    @Volatile var map: Map<String, String> = emptyMap()
}

/** Фото артиста: из meta.json (как на сайте), иначе картинка первого трека с обложкой. */
internal fun artistImg(name: String, tracks: List<Track>): String =
    ArtistPhotos.map[name.lowercase()].takeUnless { it.isNullOrBlank() }
        ?: tracks.firstOrNull { it.img.isNotBlank() }?.img.orEmpty()

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
        val artistPhotos = HashMap<String, String>()
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
                    j.optJSONObject("@artists")?.let { ao ->
                        ao.keys().forEach { k ->
                            val img = ao.optJSONObject(k)?.optString("img").orEmpty()
                            if (img.isNotBlank()) artistPhotos[k.lowercase()] = rawUrl(rp, img)
                        }
                    }
                } catch (e: Exception) {
                    // meta.json не обязателен
                }
            }
        }

        if (byPath.isEmpty()) throw (lastErr ?: RuntimeException("В хранилищах нет треков"))
        ArtistPhotos.map = artistPhotos

        val enriched = Overrides.apply(Id3.enrich(ctx, byPath.values.toList()).map { it.withMeta(meta[it.path]) })
        LibraryCache.tracks = enriched
        return enriched
            // одинаковые песни (артист + название) показываем один раз; предпочитаем FLAC
            .groupBy { dupKey(it) }
            .values
            .map { group -> group.firstOrNull { it.path.endsWith(".flac", ignoreCase = true) } ?: group.first() }
            .sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { if (it.disc > 0) it.disc else Int.MAX_VALUE }, { if (it.num > 0) it.num else Int.MAX_VALUE }, { it.title.lowercase() }))
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

    /** Разбор имени как на сайте (fromPath): «Артист - Название», номера «01. » и «02 - » убираем. */
    private fun toTrack(p: String, rp: String, imgs: Map<String, String>, folder: String): Track {
        val parts = p.removePrefix("$folder/").split('/')
        val name = parts.last().substringBeforeLast('.').replace('_', ' ').trim()
        val folderName = if (parts.size > 1) parts[parts.size - 2] else ""
        val url = rawUrl(rp, p)
        val img = coverFor(p, imgs)

        RULES.firstOrNull { it.test.containsMatchIn(name) }?.let { r ->
            return Track(p, unnum(name), r.artist, r.album, url, img, num = numOf(name))
        }
        val sp = name.split(" - ").toMutableList()
        var artist = if (sp.size > 1) sp.removeAt(0).trim() else folderName.ifBlank { "Неизвестный артист" }
        val title = unnum(sp.joinToString(" - ").trim().ifBlank { name })
        if (sp.size > 1 || artist != folderName) artist = unnum(artist)
        return Track(p, title, artist, folderName, url, img, num = numOf(name))
    }

    private val UNNUM1 = Regex("^(\\d{1,3})\\s*[.)]\\s*(.+)$")
    private val UNNUM2 = Regex("^(\\d{1,3})\\s+-\\s+(.+)$")
    /** Номер трека из начала имени файла: «01. Название» или «01 - Название». */
    private fun numOf(x: String): Int {
        val m = UNNUM1.find(x) ?: UNNUM2.find(x) ?: return 0
        return m.groupValues[1].toIntOrNull() ?: 0
    }

    private fun unnum(x: String): String {
        val m = UNNUM1.find(x) ?: UNNUM2.find(x) ?: return x
        return m.groupValues[2].trim()
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
            albumArtist = o.optString("aa").ifBlank { albumArtist },
            year = o.optString("y").ifBlank { year },
            num = o.optInt("n", 0).takeIf { it > 0 } ?: num,
            disc = o.optInt("d", 0).takeIf { it > 0 } ?: disc,
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
