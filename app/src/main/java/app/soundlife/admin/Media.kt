package app.soundlife.admin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.util.LruCache
import java.net.URL
import java.net.URLEncoder

/** Ссылки на файлы репозитория (raw), как на сайте. */
object Raw {
    fun url(repo: String, branch: String, path: String): String {
        val enc = path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return "https://raw.githubusercontent.com/$repo/$branch/$enc"
    }
}

/** Кэш обложек: картинки уменьшаются до 300 px, чтобы не забивать память. */
object Covers {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun load(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        val bytes = URL(url).openStream().use { it.readBytes() }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (opts.outWidth / sample > 600 || opts.outHeight / sample > 600) sample *= 2
        val real = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, real) ?: return null
        cache.put(url, bmp)
        return bmp
    }
}

/** Простой плеер: очередь треков, пауза, перемотка, перемешка. */
object Player {
    private var mp: MediaPlayer? = null
    var queue: List<Track> = emptyList()
        private set
    var index: Int = -1
        private set
    var repo: String = ""
    var branch: String = ""
    var onChange: (() -> Unit)? = null

    val current: Track? get() = queue.getOrNull(index)
    val isPlaying: Boolean get() = mp?.isPlaying == true
    val position: Int get() = runCatching { mp?.currentPosition ?: 0 }.getOrDefault(0)
    val duration: Int get() = runCatching { mp?.duration ?: 0 }.getOrDefault(0)

    fun play(list: List<Track>, start: Int, shuffle: Boolean = false) {
        if (list.isEmpty()) return
        queue = if (shuffle) list.shuffled() else list
        index = if (shuffle) 0 else start.coerceIn(0, list.size - 1)
        startCurrent()
    }

    fun toggle() {
        val p = mp ?: return startCurrent()
        if (p.isPlaying) p.pause() else p.start()
        onChange?.invoke()
    }

    fun next() {
        if (queue.isEmpty()) return
        index = (index + 1) % queue.size
        startCurrent()
    }

    fun prev() {
        if (queue.isEmpty()) return
        if (position > 3000) { seek(0); return }
        index = if (index <= 0) queue.size - 1 else index - 1
        startCurrent()
    }

    fun seekBy(deltaMs: Int) {
        val target = (position + deltaMs).coerceIn(0, duration.coerceAtLeast(0))
        seek(target)
    }

    fun seek(ms: Int) {
        runCatching { mp?.seekTo(ms) }
        onChange?.invoke()
    }

    private fun startCurrent() {
        val t = current ?: return
        runCatching { mp?.release() }
        val p = MediaPlayer()
        mp = p
        p.setOnCompletionListener { next() }
        p.setOnPreparedListener { it.start(); onChange?.invoke() }
        p.setOnErrorListener { _, _, _ -> onChange?.invoke(); true }
        p.setDataSource(Raw.url(repo, branch, t.path))
        p.prepareAsync()
        onChange?.invoke()
    }

    fun stop() {
        runCatching { mp?.release() }
        mp = null
    }
}
