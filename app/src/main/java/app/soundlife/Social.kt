package app.soundlife

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Люди и публичные плейлисты (таблицы profiles, public_playlists). */
object Social {
    data class Person(val uid: String, val nick: String, val avatar: String)
    data class PubPl(val id: String, val name: String, val tracks: List<String>, val owner: String)

    private fun token(): String = Auth.token() ?: throw RuntimeException("Сначала войдите в аккаунт")

    /** Профиль по нику (без @). null — такого ника нет. */
    fun findByNick(nick: String): Person? {
        val n = nick.trim().lowercase().removePrefix("@")
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/profiles?select=user_id,nick,avatar&nick=eq.${enc(n)}", token(), null, null))
        return arr.optJSONObject(0)?.let { person(it) }
    }

    fun person(uid: String): Person? {
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/profiles?select=user_id,nick,avatar&user_id=eq.${enc(uid)}", token(), null, null))
        return arr.optJSONObject(0)?.let { person(it) }
    }

    fun peopleByIds(uids: List<String>): List<Person> {
        if (uids.isEmpty()) return emptyList()
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/profiles?select=user_id,nick,avatar&user_id=in." + inList(uids), token(), null, null))
        return (0 until arr.length()).map { person(arr.getJSONObject(it)) }
    }

    /** Публичные плейлисты человека. */
    fun playlistsOf(uid: String): List<PubPl> {
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/public_playlists?select=id,name,tracks,owner&owner=eq.${enc(uid)}", token(), null, null))
        return (0 until arr.length()).map { pl(arr.getJSONObject(it)) }
    }

    fun playlistsByIds(ids: List<String>): List<PubPl> {
        if (ids.isEmpty()) return emptyList()
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/public_playlists?select=id,name,tracks,owner&id=in." + inList(ids), token(), null, null))
        return (0 until arr.length()).map { pl(arr.getJSONObject(it)) }
    }

    private fun inList(ids: List<String>) = "(" + ids.joinToString(",") { enc(it) } + ")"

    private fun person(o: JSONObject) = Person(o.str("user_id"), o.str("nick"), o.str("avatar"))

    private fun pl(o: JSONObject): PubPl {
        val a = o.optJSONArray("tracks")
        val tracks = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
        return PubPl(o.str("id"), o.str("name"), tracks, o.str("owner"))
    }

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

/** Подписки на людей (kind = user) и публичные плейлисты (kind = pl). Таблица follows, как на сайте. */
object Follows {
    private const val USER = "user"
    private const val PL = "pl"

    var users by mutableStateOf<List<String>>(emptyList())
        private set
    var playlists by mutableStateOf<List<String>>(emptyList())
        private set
    /** Кэш на время работы: данные людей и плейлистов из подписок. */
    var people by mutableStateOf<Map<String, Social.Person>>(emptyMap())
        private set
    var pubs by mutableStateOf<Map<String, Social.PubPl>>(emptyMap())
        private set
    private var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        val p = prefs(ctx)
        users = strings(p.getString("fol_u", "[]"))
        playlists = strings(p.getString("fol_p", "[]"))
    }

    fun isUser(uid: String) = uid in users
    fun isPl(id: String) = id in playlists

    /** Подписка или отписка от человека. Без входа ничего не делает. */
    fun toggleUser(uid: String) {
        if (Auth.userId() == null || Auth.userId() == uid) return
        val add = uid !in users
        users = if (add) users + uid else users - uid
        save()
        bg { remote(USER, uid, add) }
    }

    fun togglePl(id: String) {
        if (Auth.userId() == null) return
        val add = id !in playlists
        playlists = if (add) playlists + id else playlists - id
        save()
        bg { remote(PL, id, add) }
    }

    /** Загрузка подписок с аккаунта и данных о них. Вызывать в фоне. */
    fun pull() {
        val uid = Auth.userId() ?: return
        val tk = Auth.token() ?: return
        val arr = JSONArray(Sync.rest("GET", "/rest/v1/follows?select=kind,target&follower=eq.$uid", tk, null, null))
        val us = ArrayList<String>()
        val ps = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            when (o.optString("kind")) {
                USER -> us += o.optString("target")
                PL -> ps += o.optString("target")
            }
        }
        users = us
        playlists = ps
        save()
        val pls = Social.playlistsByIds(ps)
        pubs = pls.associateBy { it.id }
        people = Social.peopleByIds((us + pls.map { it.owner }).distinct()).associateBy { it.uid }
    }

    /** При выходе подписки этого аккаунта не показываем. */
    fun clear() {
        users = emptyList()
        playlists = emptyList()
        people = emptyMap()
        pubs = emptyMap()
        save()
    }

    private fun remote(kind: String, target: String, add: Boolean) {
        val uid = Auth.userId() ?: return
        val tk = Auth.token() ?: return
        if (add) {
            val body = JSONObject().put("follower", uid).put("kind", kind).put("target", target)
            Sync.rest("POST", "/rest/v1/follows", tk, body.toString(), "return=minimal")
        } else {
            Sync.rest("DELETE", "/rest/v1/follows?follower=eq.$uid&kind=eq.$kind&target=eq.${URLEncoder.encode(target, "UTF-8")}", tk, null, null)
        }
    }

    private fun bg(block: () -> Unit) {
        Thread { try { block() } catch (_: Exception) { } }.start()
    }

    private fun save() {
        val ctx = appCtx ?: return
        prefs(ctx).edit()
            .putString("fol_u", JSONArray(users).toString())
            .putString("fol_p", JSONArray(playlists).toString())
            .apply()
    }

    private fun strings(s: String?): List<String> {
        val arr = try { JSONArray(s ?: "[]") } catch (e: Exception) { return emptyList() }
        return (0 until arr.length()).map { arr.optString(it) }
    }
}
