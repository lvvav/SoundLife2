package app.soundlife.admin

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Трек из папки music. Поля из meta.json перекрывают имя файла, как на сайте. */
data class Track(
    val path: String,        // полный путь в репозитории: music/жанр/файл.mp3
    val sha: String,
    val size: Long,
    val artist: String,
    val title: String,
    val album: String,
    val year: String,
    val number: String,
    val disc: String,
    val genre: String,
    val cover: String?,      // путь к картинке в репозитории, если есть
) {
    val base: String get() = path.substringAfterLast('/').substringBeforeLast('.')
    val folder: String get() = path.substringBeforeLast('/') + "/"
}

/** Карточка артиста из meta.json → "@artists". */
data class ArtistCard(val key: String, val name: String, val img: String?, val bio: String?, val tracks: List<Track>)

/** Альбом: треки одной папки/альбома. */
data class Album(val key: String, val title: String, val artist: String, val cover: String?, val tracks: List<Track>)

/** Заявка «Хочу послушать» из Supabase (таблица wishes_top). */
data class Wish(val n: Int, val artist: String, val title: String, val album: String, val key: String)

/** Снимок библиотеки: файлы, meta.json, lyrics.json. */
class Library(
    val tracks: List<Track>,
    val meta: JSONObject,
    val lyrics: JSONObject,
    val metaSha: String?,
    val lyricsSha: String?,
) {
    val artists: List<ArtistCard> by lazy { buildArtists() }
    val albums: List<Album> by lazy { buildAlbums() }

    private fun buildArtists(): List<ArtistCard> {
        val cards = meta.optJSONObject("@artists") ?: JSONObject()
        val groups = tracks.groupBy { it.artist.trim().lowercase() }
        val keys = (groups.keys + cards.keys().asSequence().toList()).toSet()
        return keys.mapNotNull { k ->
            val list = groups[k].orEmpty()
            val card = cards.optJSONObject(k)
            if (list.isEmpty() && card == null) return@mapNotNull null
            val name = card?.optString("n").takeUnless { it.isNullOrBlank() }
                ?: list.firstOrNull()?.artist?.ifBlank { null } ?: if (k.isBlank()) "Без исполнителя" else k
            ArtistCard(k, name, card?.optString("img")?.ifBlank { null }, card?.optString("bio")?.ifBlank { null }, list)
        }.sortedBy { it.name.lowercase() }
    }

    private fun buildAlbums(): List<Album> =
        tracks.groupBy { (it.album.ifBlank { "Без альбома" }) + "\u0001" + it.artist }
            .map { (_, list) ->
                val first = list.first()
                Album(
                    key = first.album.ifBlank { "Без альбома" } + "|" + first.artist,
                    title = first.album.ifBlank { "Без альбома" },
                    artist = first.artist.ifBlank { "Неизвестный исполнитель" },
                    cover = list.firstNotNullOfOrNull { it.cover },
                    tracks = list.sortedWith(compareBy({ it.disc.toIntOrNull() ?: 0 }, { it.number.toIntOrNull() ?: 0 }, { it.title })),
                )
            }
            .sortedBy { it.title.lowercase() }

    fun lyricsFor(t: Track): String = lyrics.optString(t.path, "")

    fun metaFor(t: Track): JSONObject? = meta.optJSONObject(t.path)

    companion object {
        private val AUDIO = Regex("\\.(mp3|flac|wav|m4a|ogg)$", RegexOption.IGNORE_CASE)
        private val IMAGE = Regex("\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE)

        /** Строит библиотеку из дерева репозитория и двух JSON-файлов. */
        fun build(token: String, repo: String, branch: String, folder: String): Library {
            val nodes = Gh.tree(token, repo, branch)
            val metaNode = nodes.firstOrNull { it.path == "$folder/meta.json" }
            val lyrNode = nodes.firstOrNull { it.path == "$folder/lyrics.json" }
            val meta = metaNode?.let { parseObject(Gh.blobBytes(token, repo, it.sha)) } ?: JSONObject()
            val lyrics = lyrNode?.let { parseObject(Gh.blobBytes(token, repo, it.sha)) } ?: JSONObject()

            val inFolder = nodes.filter { it.path.startsWith("$folder/") }
            val covers = HashMap<String, String>()
            inFolder.filter { IMAGE.containsMatchIn(it.path) && it.path.substringAfterLast('/').substringBeforeLast('.').lowercase() in setOf("cover", "folder", "artwork") }
                .forEach { covers[it.path.substringBeforeLast('/') + "/"] = it.path }

            val audio = inFolder.filter { AUDIO.containsMatchIn(it.path) }
            // Теги MP3 читаем параллельно, как сайт: по несколько байт из начала файла
            val pool = java.util.concurrent.Executors.newFixedThreadPool(6)
            val tagFutures = audio.associateWith { n ->
                pool.submit(java.util.concurrent.Callable { runCatching { Tags.read(Raw.url(repo, branch, n.path)) }.getOrNull() })
            }
            // Не ждём бесконечно: файл без тегов за 20 секунд просто пропускаем
            val tagInfo = tagFutures.mapValues { (_, f) -> runCatching { f.get(20, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull() }
            pool.shutdownNow()

            val tracks = audio.map { n ->
                val m = meta.optJSONObject(n.path) ?: JSONObject()
                val tag = tagInfo[n] ?: Tags.Info()
                val base = n.path.substringAfterLast('/').substringBeforeLast('.')
                val parts = base.split(" - ", limit = 2)
                val fileArtist = if (parts.size == 2) parts[0] else ""
                val fileTitle = if (parts.size == 2) parts[1] else base
                val parentDir = n.path.substringBeforeLast('/').removePrefix("$folder/").substringAfterLast('/')
                fun pick(key: String, vararg fallbacks: String): String {
                    m.optString(key).takeIf { it.isNotBlank() }?.let { return it }
                    return fallbacks.firstOrNull { it.isNotBlank() }.orEmpty()
                }
                Track(
                    path = n.path, sha = n.sha, size = n.size,
                    artist = pick("a", tag.artist, fileArtist),
                    title = pick("t", tag.title, fileTitle),
                    album = pick("al", tag.album, if (parentDir.isBlank() || parentDir == folder) "" else parentDir),
                    year = pick("y", tag.year),
                    number = pick("n", tag.track),
                    disc = pick("d", tag.disc),
                    genre = parentDir,
                    cover = m.optString("aa").ifBlank { null } ?: covers[n.path.substringBeforeLast('/') + "/"],
                )
            }
            return Library(tracks, meta, lyrics, metaNode?.sha, lyrNode?.sha)
        }

        private fun parseObject(bytes: ByteArray): JSONObject =
            runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrDefault(JSONObject())
    }
}

/** Заявки «Хочу послушать» из Supabase. Публичный ключ берётся из веб-админки (только чтение). */
object Wishes {
    private const val URL_BASE = "https://zpmzqgxwqoisfvahfhqc.supabase.co"
    private const val KEY = "sb_publishable_EJD_GXiloGMd6DDMt87G8Q_PxYmI8eF"

    fun load(): List<Wish> {
        val c = (URL("$URL_BASE/rest/v1/wishes_top?select=t,a,al,n&order=n.desc,last_at.desc&limit=500")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("apikey", KEY)
            setRequestProperty("Authorization", "Bearer $KEY")
        }
        if (c.responseCode !in 200..299) throw GhError(c.responseCode, "Заявки не загрузились. Проверьте SQL из supabase_v6.sql.")
        val arr = org.json.JSONArray(c.inputStream.bufferedReader().use { it.readText() })
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val artist = o.optString("a")
            val title = o.optString("t")
            Wish(
                n = o.optInt("n", 0),
                artist = artist,
                title = title,
                album = o.optString("al"),
                key = (artist + " " + title).lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim(),
            )
        }
    }
}
