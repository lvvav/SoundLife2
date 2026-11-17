package app.soundlife.admin

import org.json.JSONObject

/** Вход и настройки хранилища, нужные всем экранам. */
data class Session(val token: String, val repo: String, val branch: String, val folder: String)

/** Поля формы правки. Пустое поле в режиме нескольких треков значит «не менять». */
data class TrackForm(
    val artist: String = "",
    val title: String = "",
    val album: String = "",
    val genre: String = "",
    val year: String = "",
    val number: String = "",
    val disc: String = "",
    val lyrics: String? = null,     // только для одного трека
    val cover: ByteArray? = null,   // новая обложка (jpeg)
    val removeCover: Boolean = false,
)

object Save {
    private val BAD_CHARS = Regex("[\\\\/:*?\"<>|]+")

    fun clean(s: String): String = s.replace(BAD_CHARS, "_").replace(Regex("\\s+"), " ").trim().trim('.')

    /** Правка треков одним коммитом: переименование, перенос в жанр, метаданные, текст, обложка. */
    fun tracks(s: Session, lib: Library, targets: List<Track>, form: TrackForm, single: Boolean): String {
        val meta = JSONObject(lib.meta.toString())
        val lyr = JSONObject(lib.lyrics.toString())
        val files = LinkedHashMap<String, String?>()
        val existing = lib.tracks.map { it.path }.toHashSet()
        val moving = HashSet<String>()

        for (t in targets) {
            val artist = if (form.artist.isNotBlank()) form.artist.trim() else t.artist
            val title = if (form.title.isNotBlank()) form.title.trim() else t.title
            val renamed = form.artist.isNotBlank() || form.title.isNotBlank()
            val genre = if (form.genre.isNotBlank()) clean(form.genre) else t.genre
            val base = if (renamed) (if (artist.isNotBlank()) clean(artist) + " - " else "") + clean(title) else t.base
            if (base.isBlank()) return "Получилось пустое имя файла."
            val dir = if (genre.isBlank()) "" else "$genre/"
            val ext = t.path.substringAfterLast('.')
            val newPath = "${s.folder}/$dir$base.$ext"

            if (newPath != t.path) {
                if (newPath in existing && newPath !in moving) return "Файл «$newPath» уже существует."
                moving += newPath
                files[t.path] = null
                files[newPath] = t.sha
                val old = meta.optJSONObject(t.path)
                meta.remove(t.path)
                if (old != null) meta.put(newPath, JSONObject(old.toString()))
                if (lyr.has(t.path)) { val v = lyr.getString(t.path); lyr.remove(t.path); lyr.put(newPath, v) }
            }

            val m = meta.optJSONObject(newPath) ?: JSONObject()
            if (form.artist.isNotBlank()) m.put("a", artist)
            if (form.title.isNotBlank()) m.put("t", title)
            if (form.album.isNotBlank()) m.put("al", form.album.trim())
            if (form.year.isNotBlank()) m.put("y", form.year.trim())
            if (form.number.isNotBlank()) m.put("n", form.number.trim())
            if (form.disc.isNotBlank()) m.put("d", form.disc.trim())
            if (form.removeCover) m.remove("aa")
            form.cover?.let { bytes ->
                val coverPath = "${s.folder}/$dir$base.jpg"
                files[coverPath] = Gh.blob(s.token, s.repo, bytes)
                m.put("aa", coverPath)
            }
            if (m.length() > 0) meta.put(newPath, m) else meta.remove(newPath)

            if (single && form.lyrics != null) {
                val text = form.lyrics.replace("\r", "").trimEnd()
                if (text.isBlank()) lyr.remove(newPath) else lyr.put(newPath, text)
            }
        }

        val changed = StringBuilder()
        if (meta.toString() != lib.meta.toString()) {
            files[metaPath(s)] = Gh.blob(s.token, s.repo, meta.toString(1).toByteArray())
            changed.append("meta ")
        }
        if (lyr.toString() != lib.lyrics.toString()) {
            files[lyricsPath(s)] = Gh.blob(s.token, s.repo, lyr.toString(1).toByteArray())
            changed.append("lyrics ")
        }
        if (files.isEmpty()) return "Ничего не изменено."
        Gh.commit(s.token, s.repo, s.branch, files, "Правка треков: ${targets.size}")
        return "Сохранено: ${targets.size} шт."
    }

    /** Карточка артиста: имя, описание и фото. */
    fun artistCard(s: Session, lib: Library, key: String, origName: String, name: String, bio: String, photo: ByteArray?, removePhoto: Boolean): String {
        val meta = JSONObject(lib.meta.toString())
        val files = LinkedHashMap<String, String?>()
        val cards = meta.optJSONObject("@artists") ?: JSONObject()
        val card = JSONObject()
        if (name.isNotBlank() && name.trim() != origName) card.put("n", name.trim())
        if (bio.isNotBlank()) card.put("bio", bio.trim())
        val old = lib.artists.firstOrNull { it.key == key }?.img
        var img: String? = if (removePhoto) null else old
        if (photo != null) {
            val path = "${s.folder}/_artists/${slug(origName)}-${System.currentTimeMillis().toString(36)}.jpg"
            files[path] = Gh.blob(s.token, s.repo, photo)
            img = path
            if (old != null && old != path) files[old] = null
        } else if (removePhoto && old != null) {
            files[old] = null
        }
        if (img != null) card.put("img", img)
        if (card.length() > 0) cards.put(key, card) else cards.remove(key)
        if (cards.length() > 0) meta.put("@artists", cards) else meta.remove("@artists")
        files[metaPath(s)] = Gh.blob(s.token, s.repo, meta.toString(1).toByteArray())
        Gh.commit(s.token, s.repo, s.branch, files, "Карточка артиста: $origName")
        return "Карточка сохранена. Сайт обновится через пару минут."
    }

    /** Решения по заявкам «Хочу послушать»: ключ → «ok» или «no». Пустой s удаляет решение. */
    fun wishes(s: Session, lib: Library, decisions: Map<String, String?>): String {
        val meta = JSONObject(lib.meta.toString())
        val w = meta.optJSONObject("@wishes") ?: JSONObject()
        decisions.forEach { (k, v) ->
            if (v == null) w.remove(k) else w.put(k, JSONObject().put("s", v).put("t", System.currentTimeMillis()))
        }
        if (w.length() > 0) meta.put("@wishes", w) else meta.remove("@wishes")
        if (meta.toString() == lib.meta.toString()) return "Ничего не изменено."
        val files = mapOf(metaPath(s) to Gh.blob(s.token, s.repo, meta.toString(1).toByteArray()))
        Gh.commit(s.token, s.repo, s.branch, files, "Хочу послушать: решения")
        return "Сохранено. Слушатели увидят решения в течение пары минут."
    }

    /** Загрузка новых файлов в папку жанра одним коммитом. */
    fun upload(s: Session, files: List<Pair<String, ByteArray>>, genre: String): Int {
        val map = LinkedHashMap<String, String?>()
        val dir = if (genre.isBlank()) "" else "${clean(genre)}/"
        for ((name, bytes) in files) {
            map["${s.folder}/$dir${clean(name)}"] = Gh.blob(s.token, s.repo, bytes)
        }
        Gh.commit(s.token, s.repo, s.branch, map, "Загрузка треков: ${files.size}")
        return files.size
    }

    private fun metaPath(s: Session) = "${s.folder}/meta.json"
    private fun lyricsPath(s: Session) = "${s.folder}/lyrics.json"

    private fun slug(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40).ifBlank { "artist" }
}
