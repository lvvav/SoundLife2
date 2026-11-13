package app.soundlife

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Статистика прослушивания в формате сайта: st (секунды, плейлисты, пропуски, дни, часы),
 * dt (секунды по дням и трекам), cnt (запуски), hist (последние 50 треков).
 * Хранится на устройстве и в аккаунте. Слияние — по максимуму, как на сайте, поэтому ничего не сбрасывается.
 * Все методы синхронизированы: их вызывают плеер (главный поток) и синхронизация (фоновый).
 */
object Stats {
    private const val PREF = "stats_v1"
    private var appCtx: Context? = null

    private val sec = HashMap<String, Double>()
    private val pl = HashMap<String, Int>()
    private val sk = HashMap<String, Int>()
    private val day = HashMap<String, Double>()
    private val hr = DoubleArray(24)
    private val dtDay = HashMap<String, HashMap<String, Double>>()
    private val cnt = HashMap<String, Int>()
    private val hist = ArrayList<String>()

    private var dirty = false
    private var curKey = ""
    private var curLis = 0.0
    private var curDur = 0.0

    @Synchronized
    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        val raw = prefs(ctx).getString(PREF, null) ?: return
        try {
            val o = JSONObject(raw)
            mergeFromSite(o.optJSONObject("st"), o.optJSONObject("dt"), o.optJSONObject("cnt"), o.optJSONArray("hist"))
        } catch (e: Exception) {
            // повреждённые данные не должны ломать запуск
        }
        dirty = false
    }

    /** Одна секунда игры текущего трека. */
    @Synchronized
    fun tick(key: String, durSec: Double) {
        if (key.isEmpty()) return
        if (key != curKey) {
            curKey = key
            curLis = 0.0
        }
        curDur = durSec
        curLis += 1.0
        sec[key] = (sec[key] ?: 0.0) + 1.0
        val today = todayKey()
        day[today] = (day[today] ?: 0.0) + 1.0
        hr[Calendar.getInstance().get(Calendar.HOUR_OF_DAY)] += 1.0
        val m = dtDay.getOrPut(today) { HashMap() }
        m[key] = (m[key] ?: 0.0) + 1.0
        dirty = true
    }

    /** Трек начали играть: запуск и история. */
    @Synchronized
    fun started(key: String) {
        if (key.isEmpty()) return
        cnt[key] = (cnt[key] ?: 0) + 1
        hist.remove(key)
        hist.add(0, key)
        while (hist.size > 50) hist.removeAt(hist.size - 1)
        curKey = key
        curLis = 0.0
        curDur = 0.0
        dirty = true
        Sync.schedule()
    }

    /** Трек закончился или переключён. ended = дослушан до конца. Как на сайте: плейлист или пропуск. */
    @Synchronized
    fun leave(ended: Boolean) {
        if (curKey.isEmpty()) return
        val need = if (curDur > 0) minOf(30.0, curDur * 0.5) else 30.0
        if (ended || curLis >= need) pl[curKey] = (pl[curKey] ?: 0) + 1
        else if (curLis < 20) sk[curKey] = (sk[curKey] ?: 0) + 1
        curKey = ""
        curLis = 0.0
        curDur = 0.0
        dirty = true
        Sync.schedule()
    }

    /** Сохранить на устройство, если что-то изменилось. */
    @Synchronized
    fun saveIfDirty() {
        if (!dirty) return
        val ctx = appCtx ?: return
        val (st, dt) = toSite(null)
        val o = JSONObject().put("st", st).put("dt", dt).put("cnt", cntJson()).put("hist", histJson())
        prefs(ctx).edit().putString(PREF, o.toString()).apply()
        dirty = false
    }

    /** Всего прослушано, часы (для настроек). */
    @Synchronized
    fun totalHours(): Double = sec.values.sum() / 3600.0

    /** Слияние с данными аккаунта (поля st, dt, cnt, hist). По максимуму, история — объединением. */
    @Synchronized
    fun mergeFromSite(st: JSONObject?, dt: JSONObject?, cntO: JSONObject?, histA: JSONArray?) {
        if (st != null) {
            st.optJSONObject("sec")?.let { o -> for (k in o.keyList()) sec[k] = maxOf(sec[k] ?: 0.0, o.optDouble(k, 0.0)) }
            st.optJSONObject("pl")?.let { o -> for (k in o.keyList()) pl[k] = maxOf(pl[k] ?: 0, o.optInt(k, 0)) }
            st.optJSONObject("sk")?.let { o -> for (k in o.keyList()) sk[k] = maxOf(sk[k] ?: 0, o.optInt(k, 0)) }
            st.optJSONObject("day")?.let { o -> for (k in o.keyList()) day[k] = maxOf(day[k] ?: 0.0, o.optDouble(k, 0.0)) }
            st.optJSONArray("hr")?.let { a ->
                for (i in 0 until minOf(24, a.length())) hr[i] = maxOf(hr[i], a.optDouble(i, 0.0))
            }
        }
        if (dt != null) {
            // на сайте треки в dt записаны числовыми id: переводим их обратно в ключи
            val idToKey = HashMap<String, String>()
            dt.optJSONObject("ks")?.let { ks -> for (k in ks.keyList()) idToKey[ks.optString(k)] = k }
            dt.optJSONObject("d")?.let { d ->
                for (dayKey in d.keyList()) {
                    val m = d.optJSONObject(dayKey) ?: continue
                    val mine = dtDay.getOrPut(dayKey) { HashMap() }
                    for (id in m.keyList()) {
                        val key = idToKey[id] ?: continue
                        mine[key] = maxOf(mine[key] ?: 0.0, m.optDouble(id, 0.0))
                    }
                }
            }
        }
        cntO?.let { o -> for (k in o.keyList()) cnt[k] = maxOf(cnt[k] ?: 0, o.optInt(k, 0)) }
        histA?.let { a ->
            val merged = LinkedHashSet<String>()
            merged += hist
            for (i in 0 until a.length()) merged += a.optString(i)
            hist.clear()
            hist.addAll(merged.take(50))
        }
        dirty = true
    }

    /** Текущие данные в формате сайта. oldO — прежнее поле dt, из него сохраняем «o». */
    @Synchronized
    fun toSite(oldO: JSONObject?): Pair<JSONObject, JSONObject> {
        val st = JSONObject()
            .put("sec", JSONObject(sec))
            .put("pl", JSONObject(pl))
            .put("sk", JSONObject(sk))
            .put("day", JSONObject(day))
            .put("hr", JSONArray(hr.toList()))

        val keys = dtDay.values.flatMap { it.keys }.toSortedSet()
        val ids = HashMap<String, String>()
        keys.forEachIndexed { i, k -> ids[k] = i.toString() }
        val ks = JSONObject()
        ids.forEach { (k, id) -> ks.put(k, id) }
        val d = JSONObject()
        dtDay.forEach { (dayKey, m) ->
            val o = JSONObject()
            m.forEach { (k, v) -> o.put(ids[k]!!, v) }
            d.put(dayKey, o)
        }
        val dt = JSONObject()
            .put("ks", ks)
            .put("d", d)
            .put("n", ids.size)
            .put("o", oldO ?: JSONObject())
        return Pair(st, dt)
    }

    @Synchronized
    fun cntJson(): JSONObject {
        val o = JSONObject()
        cnt.forEach { (k, v) -> o.put(k, v) }
        return o
    }

    @Synchronized
    fun histJson(): JSONArray = JSONArray(hist.toList())

    /** Секунды прослушивания по дням за последние days дней, сдвиг offset дней назад. От старых к новым. */
    fun dailySec(days: Int, offset: Int = 0): List<Pair<String, Double>> {
        val out = ArrayList<Pair<String, Double>>()
        for (i in (days - 1 + offset) downTo offset) {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val k = "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
            out.add(k to (day[k] ?: 0.0))
        }
        return out
    }

    private fun todayKey(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun JSONObject.keyList(): List<String> = keys().asSequence().toList()
}
