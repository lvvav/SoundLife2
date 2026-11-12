package app.soundlife

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

/** Публичные плейлисты из библиотеки: таблица public_playlists, как на сайте. */
object PubPlaylists {
    var names by mutableStateOf<Set<String>>(emptySet())
        private set
    private var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        names = prefs(ctx).getStringSet("pub_names", emptySet())?.toSet() ?: emptySet()
    }

    fun isPublic(name: String) = name in names

    /** Стабильный id: первые 8 знаков владельца и хеш имени плейлиста. */
    private fun pid(name: String): String {
        val uid = Auth.userId() ?: throw RuntimeException("Сначала войдите в аккаунт")
        return uid.take(8) + "-" + Integer.toHexString(name.hashCode())
    }

    private fun row(name: String, tracks: List<String>): JSONObject {
        val arr = JSONArray()
        tracks.forEach { arr.put(it) }
        return JSONObject()
            .put("id", pid(name))
            .put("owner", Auth.userId().orEmpty())
            .put("name", name)
            .put("tracks", arr)
            .put("updated_at", Instant.now().toString())
    }

    /** Включить или выключить публичность. Вызывать в фоне. */
    fun set(name: String, on: Boolean, tracks: List<String>) {
        val tk = Auth.token() ?: throw RuntimeException("Сначала войдите в аккаунт")
        if (on) {
            Sync.rest("POST", "/rest/v1/public_playlists?on_conflict=id", tk, JSONArray().put(row(name, tracks)).toString(), "resolution=merge-duplicates,return=minimal")
            save(names + name)
        } else {
            Sync.rest("DELETE", "/rest/v1/public_playlists?id=eq." + URLEncoder.encode(pid(name), "UTF-8"), tk, null, null)
            save(names - name)
        }
    }

    /** Обновить треки публичного плейлиста. Вызывать в фоне. */
    fun push(name: String, tracks: List<String>) {
        if (!isPublic(name)) return
        val tk = Auth.token() ?: return
        Sync.rest("POST", "/rest/v1/public_playlists?on_conflict=id", tk, JSONArray().put(row(name, tracks)).toString(), "resolution=merge-duplicates,return=minimal")
    }

    private fun save(s: Set<String>) {
        names = s
        appCtx?.let { prefs(it).edit().putStringSet("pub_names", s).apply() }
    }
}
