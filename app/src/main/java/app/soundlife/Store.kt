package app.soundlife

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject

private lateinit var sp: SharedPreferences
fun prefs(ctx: Context): SharedPreferences {
    if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("soundlife", Context.MODE_PRIVATE)
    return sp
}

/** Лайки (♥). Хранятся на устройстве; в шаге 4 синхронизируются с Supabase. */
object Favorites {
    var ids by mutableStateOf<Set<String>>(emptySet())
        private set
    /** Сервис подписывается, чтобы обновлять сердечко в шторке. */
    var onChange: (() -> Unit)? = null

    fun init(ctx: Context) { ids = prefs(ctx).getStringSet("fav", emptySet())!!.toSet() }
    fun has(id: String?) = id != null && id in ids
    /** Слияние с аккаунтом (без повторной синхронизации). */
    fun mergeRemote(remote: Set<String>) {
        ids = ids + remote
        sp.edit().putStringSet("fav", ids).apply()
    }
    fun toggle(id: String) {
        ids = if (id in ids) ids - id else ids + id
        sp.edit().putStringSet("fav", ids).apply()
        onChange?.invoke()
        Sync.schedule()
    }
}

/** Подписка на альбом или исполнителя Яндекса. kind: "artist" или "album"; для альбома sub — исполнитель. */
data class YmSub(val kind: String, val id: String, val title: String, val sub: String, val img: String)

/** Подписки на артистов и альбомы (Медиатека). Хранятся на устройстве; позже — синхронизация с Supabase. */
object Subs {
    var artists by mutableStateOf<Set<String>>(emptySet())
        private set
    var albums by mutableStateOf<Set<String>>(emptySet())
        private set

    fun init(ctx: Context) {
        val p = prefs(ctx)
        artists = p.getStringSet("sub_artists", emptySet())!!.toSet()
        albums = p.getStringSet("sub_albums", emptySet())!!.toSet()
        loadYm(p)
    }

    /** Яндекс-подписки: список хранится как JSON. */
    var ym by mutableStateOf<List<YmSub>>(emptyList())
        private set

    fun isYm(kind: String, id: String) = ym.any { it.kind == kind && it.id == id }

    fun toggleYm(s: YmSub) {
        ym = if (isYm(s.kind, s.id)) ym.filterNot { it.kind == s.kind && it.id == s.id } else ym + s
        saveYm()
        Sync.schedule()
    }

    /** Слияние с аккаунтом (без повторной синхронизации). */
    fun mergeRemote(a: Set<String>, b: Set<String>, y: List<YmSub>) {
        artists = artists + a
        albums = albums + b
        ym = (ym + y).distinctBy { it.kind + it.id }
        sp.edit().putStringSet("sub_artists", artists).putStringSet("sub_albums", albums).apply()
        saveYm()
    }

    private fun loadYm(p: SharedPreferences) {
        val a = JSONArray(p.getString("sub_ym", "[]") ?: "[]")
        ym = (0 until a.length()).map {
            val o = a.getJSONObject(it)
            YmSub(o.optString("kind"), o.optString("id"), o.optString("title"), o.optString("sub"), o.optString("img"))
        }
    }

    private fun saveYm() {
        val a = JSONArray()
        ym.forEach { a.put(JSONObject().put("kind", it.kind).put("id", it.id).put("title", it.title).put("sub", it.sub).put("img", it.img)) }
        sp.edit().putString("sub_ym", a.toString()).apply()
    }
    fun toggleArtist(name: String) {
        artists = if (name in artists) artists - name else artists + name
        sp.edit().putStringSet("sub_artists", artists).apply()
        Sync.schedule()
    }
    fun toggleAlbum(name: String) {
        albums = if (name in albums) albums - name else albums + name
        sp.edit().putStringSet("sub_albums", albums).apply()
        Sync.schedule()
    }
}

/** Плейлисты: название -> список путей треков. Хранятся на устройстве. */
object Playlists {
    var lists by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set
    /** Трек, для которого открыт диалог «В плейлист» (null — диалог закрыт). */
    var pendingTrack by mutableStateOf<String?>(null)

    fun init(ctx: Context) {
        val o = JSONObject(prefs(ctx).getString("playlists", "{}") ?: "{}")
        lists = o.keys().asSequence().associateWith { k ->
            val a = o.getJSONArray(k)
            List(a.length()) { a.getString(it) }
        }
    }
    private fun save() {
        val o = JSONObject()
        lists.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        sp.edit().putString("playlists", o.toString()).apply()
    }
    /** Слияние с аккаунтом: объединённые списки по названию (без повторной синхронизации). */
    fun mergeRemote(merged: Map<String, List<String>>) {
        lists = lists + merged
        save()
    }

    fun create(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || n in lists) return false
        lists = lists + (n to emptyList())
        save(); return true
    }
    fun delete(name: String) { lists = lists - name; save() }
    fun add(name: String, path: String) {
        val cur = lists[name] ?: return
        if (path in cur) return
        lists = lists + (name to cur + path); save()
    }
    fun remove(name: String, path: String) {
        val cur = lists[name] ?: return
        lists = lists + (name to cur - path); save()
    }
}

/** Варианты цвета акцента (ARGB). */
val ACCENTS: List<Pair<Long, String>> = listOf(
    0xFFC6FF3DL to "Салатовый",
    0xFF1ED760L to "Зелёный",
    0xFF4FC3F7L to "Голубой",
    0xFFFF8A65L to "Коралловый",
    0xFFFFFFFFL to "Белый",
)

/** Настройки приложения. */
object Settings {
    const val KEY_MIX = "mix"
    const val KEY_ACCENT = "accent"
    /** true = музыка играет поверх TikTok и других приложений (не берёт аудиофокус). */
    var mix by mutableStateOf(false)
        private set
    /** Индекс цвета из ACCENTS. */
    var accentIx by mutableIntStateOf(0)
        private set
    /** Цвет с сайта (ARGB), если задан: перекрывает пресеты ACCENTS. */
    var accentCustom by mutableStateOf<Long?>(null)
        private set
    private var siteT = 0L
    val accentArgb: Long get() = accentCustom ?: ACCENTS[accentIx].first

    fun init(ctx: Context) {
        val p = prefs(ctx)
        mix = p.getBoolean(KEY_MIX, false)
        accentIx = p.getInt(KEY_ACCENT, 0).coerceIn(0, ACCENTS.lastIndex)
        accentCustom = p.getLong("accent_custom", -1L).takeIf { it != -1L }
        siteT = p.getLong("site_set_t", 0L)
        Palette.set(p.getLong("site_bg", 0xFF000000L))
        anim = p.getBoolean("f_anim", true)
        adminOn = p.getBoolean("f_admin", false)
    }
    /** Пункт «Админ-панель» в настройках для разработчиков. */
    var adminOn by mutableStateOf(false)
        private set
    fun setAdminPanel(v: Boolean) { adminOn = v; sp.edit().putBoolean("f_admin", v).apply() }
    /** Анимации появления экранов и элементов (как на сайте, «без анимаций»). */
    var anim by mutableStateOf(true)
        private set
    fun setAnimOn(v: Boolean) { anim = v; sp.edit().putBoolean("f_anim", v).apply() }
    /** Сброс всех настроек к исходным (как «Сбросить настройки» на сайте). */
    fun resetAll() {
        setAccent(0)
        changeMix(false)
        setAnimOn(true)
    }
    fun changeMix(v: Boolean) { mix = v; sp.edit().putBoolean(KEY_MIX, v).apply() }
    fun setAccent(i: Int) {
        accentIx = i
        accentCustom = null
        sp.edit().putInt(KEY_ACCENT, i).remove("accent_custom").apply()
    }

    /** Акцент и фон с сайта (#rrggbb), применяем, если настройки сайта новее последних. */
    fun applySite(acc: String, bg: String, t: Long) {
        if (t <= siteT) return
        siteT = t
        val e = sp.edit().putLong("site_set_t", t)
        parseHex(acc)?.let { accentCustom = it; e.putLong("accent_custom", it) }
        parseHex(bg)?.let { Palette.set(it); e.putLong("site_bg", it) }
        e.apply()
    }

    private fun parseHex(h: String): Long? =
        if (h.length == 7 && h.startsWith("#")) h.substring(1).toLongOrNull(16)?.let { 0xFF000000L or it } else null
}
