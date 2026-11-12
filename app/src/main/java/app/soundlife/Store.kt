package app.soundlife

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
    fun toggle(id: String) {
        ids = if (id in ids) ids - id else ids + id
        sp.edit().putStringSet("fav", ids).apply()
        onChange?.invoke()
    }
}

/** Настройки приложения. */
object Settings {
    const val KEY_MIX = "mix"
    /** true = музыка играет поверх TikTok и других приложений (не берёт аудиофокус). */
    var mix by mutableStateOf(false)
        private set

    fun init(ctx: Context) { mix = prefs(ctx).getBoolean(KEY_MIX, false) }
    fun setMix(v: Boolean) { mix = v; sp.edit().putBoolean(KEY_MIX, v).apply() }
}
