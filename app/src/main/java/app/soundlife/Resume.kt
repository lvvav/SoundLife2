package app.soundlife

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject

/** Запоминает очередь и место в треке, чтобы после перезапуска плеер показывал, что слушали, и продолжал с той же секунды. */
object Resume {
    private const val PREFS = "resume"
    private const val KEY = "state"
    private var lastSave = 0L

    /** Сохраняет текущую очередь и позицию. Пишет не чаще раза в 2 секунды, если не force. */
    fun save(ctx: Context, c: Player, force: Boolean = false) {
        if (c.mediaItemCount == 0) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSave < 2000) return
        lastSave = now
        try {
            val arr = JSONArray()
            for (i in 0 until c.mediaItemCount) {
                val item = c.getMediaItemAt(i)
                val m = item.mediaMetadata
                arr.put(JSONObject()
                    .put("id", item.mediaId ?: "")
                    .put("uri", item.localConfiguration?.uri?.toString() ?: "")
                    .put("t", m.title?.toString() ?: "")
                    .put("a", m.artist?.toString() ?: "")
                    .put("al", m.albumTitle?.toString() ?: "")
                    .put("art", m.artworkUri?.toString() ?: ""))
            }
            val obj = JSONObject()
                .put("items", arr)
                .put("index", c.currentMediaItemIndex)
                .put("pos", c.currentPosition)
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, obj.toString()).apply()
        } catch (e: Exception) {
            // сохранение не критично: при ошибке просто не запоминаем
        }
    }

    /** Загружает сохранённую очередь на паузе, без автозапуска. */
    fun restore(ctx: Context, c: Player) {
        try {
            val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
            val obj = JSONObject(raw)
            val arr = obj.getJSONArray("items")
            if (arr.length() == 0) return
            val items = ArrayList<MediaItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val art = o.optString("art")
                items.add(MediaItem.Builder()
                    .setMediaId(o.optString("id"))
                    .setUri(o.optString("uri"))
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(o.optString("t"))
                        .setArtist(o.optString("a"))
                        .setAlbumTitle(o.optString("al"))
                        .setArtworkUri(if (art.isNotBlank()) Uri.parse(art) else null)
                        .build())
                    .build())
            }
            val index = obj.optInt("index", 0).coerceIn(0, items.size - 1)
            c.setMediaItems(items, index, obj.optLong("pos", 0L))
            c.prepare()
            c.playWhenReady = false
        } catch (e: Exception) {
            // нет сохранения или оно повреждено: начинаем с пустого плеера
        }
    }
}
