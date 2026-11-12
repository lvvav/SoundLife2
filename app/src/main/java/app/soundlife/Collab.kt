package app.soundlife

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Совместные плейлисты, как на сайте: таблицы collab_playlists, collab_tracks, collab_members. Хранятся на сервере. */
object Collab {
    data class Pl(val id: String, val name: String, val owner: String)

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }

    private fun token(): String = Auth.token() ?: throw RuntimeException("Сначала войдите в аккаунт")
    private fun me(): String = Auth.userId() ?: throw RuntimeException("Сначала войдите в аккаунт")
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Плейлисты, где я владелец или участник. */
    fun list(): List<Pl> {
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/collab_playlists?select=id,owner,name&order=created_at.asc", token(), null, null))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Pl(o.optString("id"), o.optString("name"), o.optString("owner"))
        }
    }

    /** Треки плейлистов: id плейлиста -> ключи треков по порядку добавления. */
    fun tracks(ids: List<String>): Map<String, List<String>> {
        if (ids.isEmpty()) return emptyMap()
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/collab_tracks?select=playlist_id,track_key&playlist_id=in.(${ids.joinToString(",") { enc(it) }})&order=added_at.asc", token(), null, null))
        val out = HashMap<String, MutableList<String>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.getOrPut(o.optString("playlist_id")) { ArrayList() }.add(o.optString("track_key"))
        }
        return out
    }

    fun create(name: String, owner: String): String {
        val id = UUID.randomUUID().toString()
        val row = JSONObject().put("id", id).put("owner", owner).put("name", name)
        Sync.rest("POST", "/rest/v1/collab_playlists", token(), JSONArray().put(row).toString(), "return=minimal")
        return id
    }

    fun addTrack(pid: String, key: String) {
        val row = JSONObject().put("playlist_id", pid).put("track_key", key).put("added_by", me())
        Sync.rest("POST", "/rest/v1/collab_tracks?on_conflict=playlist_id,track_key", token(), JSONArray().put(row).toString(), "resolution=ignore-duplicates,return=minimal")
    }

    fun removeTrack(pid: String, key: String) {
        Sync.rest("DELETE", "/rest/v1/collab_tracks?playlist_id=eq.$pid&track_key=eq.${enc(key)}", token(), null, null)
    }

    /** Переименовать может только владелец. PATCH через OkHttp: HttpURLConnection не умеет PATCH. */
    fun rename(pid: String, name: String) {
        val req = Request.Builder()
            .url("${BuildConfig.SUPA_URL}/rest/v1/collab_playlists?id=eq.$pid")
            .header("apikey", BuildConfig.SUPA_KEY)
            .header("Authorization", "Bearer ${token()}")
            .header("Prefer", "return=minimal")
            .patch(JSONObject().put("name", name).toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { if (!it.isSuccessful) throw RuntimeException("Не удалось переименовать") }
    }

    /** Удалить плейлист у всех (владелец). */
    fun delete(pid: String) {
        Sync.rest("DELETE", "/rest/v1/collab_playlists?id=eq.$pid", token(), null, null)
    }

    /** Выйти из плейлиста (участник). */
    fun leave(pid: String) {
        Sync.rest("DELETE", "/rest/v1/collab_members?playlist_id=eq.$pid&user_id=eq.${me()}", token(), null, null)
    }

    fun members(pid: String): List<String> {
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/collab_members?select=user_id&playlist_id=eq.$pid&order=added_at.asc", token(), null, null))
        return (0 until arr.length()).map { arr.getJSONObject(it).optString("user_id") }
    }

    fun addMember(pid: String, uid: String) {
        val row = JSONObject().put("playlist_id", pid).put("user_id", uid)
        try {
            Sync.rest("POST", "/rest/v1/collab_members", token(), JSONArray().put(row).toString(), "return=minimal")
        } catch (e: Exception) {
            throw RuntimeException(if (e.message.orEmpty().contains("23505")) "Этот человек уже в плейлисте" else "Не удалось добавить")
        }
    }

    fun removeMember(pid: String, uid: String) {
        Sync.rest("DELETE", "/rest/v1/collab_members?playlist_id=eq.$pid&user_id=eq.$uid", token(), null, null)
    }
}
