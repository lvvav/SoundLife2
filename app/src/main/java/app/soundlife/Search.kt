package app.soundlife

/**
 * Нечёткий поиск как на сайте: раскладка (ghbdtn ↔ привет), транслит (privet ↔ привет), опечатки.
 */
private const val LAY_E = "qwertyuiop[]asdfghjkl;'zxcvbnm,./`"
private const val LAY_R = "йцукенгшщзхъфывапролджэячсмитьбю.ё"

private val R2L = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh",
    'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
    'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts",
    'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
)
private val L2R = mapOf(
    "sch" to "щ", "sh" to "ш", "ch" to "ч", "zh" to "ж", "yu" to "ю", "ya" to "я", "ts" to "ц", "yo" to "ё",
    "a" to "а", "b" to "б", "c" to "к", "d" to "д", "e" to "е", "f" to "ф", "g" to "г", "h" to "х", "i" to "и",
    "j" to "дж", "k" to "к", "l" to "л", "m" to "м", "n" to "н", "o" to "о", "p" to "п", "q" to "к", "r" to "р",
    "s" to "с", "t" to "т", "u" to "у", "v" to "в", "w" to "в", "x" to "кс", "y" to "й", "z" to "з",
)
private val LAT_TOKEN = Regex("sch|sh|ch|zh|yu|ya|ts|yo|[a-z]")

/** Запрос, набранный в другой раскладке: ghbdtn → привет. */
fun layFix(q: String): String = q.map { c -> val k = LAY_E.indexOf(c); if (k > -1) LAY_R[k] else c }.joinToString("")

/** Запрос русскими буквами, набранный латиницей в русской раскладке: привет → ghbdtn. */
fun layFixR(q: String): String = q.map { c -> val k = LAY_R.indexOf(c); if (k > -1) LAY_E[k] else c }.joinToString("")

fun ru2lat(q: String): String = q.lowercase().map { c -> R2L[c] ?: c.toString() }.joinToString("")

fun lat2ru(q: String): String = LAT_TOKEN.replace(q.lowercase()) { m -> L2R[m.value] ?: m.value }

/** Варианты запроса: транслит в обе стороны, если отличаются от исходного. */
fun translit(q: String): List<String> {
    val base = q.lowercase()
    return listOf(ru2lat(q), lat2ru(q)).filter { it != base }.distinct()
}

/** Нормализация: без регистра, ё = е, без знаков. */
fun fzNorm(x: String): String =
    x.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** Расстояние Левенштейна с ограничением max. */
fun lev(a: String, b: String, max: Int): Int {
    if (kotlin.math.abs(a.length - b.length) > max) return max + 1
    var p = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        for (j in 1..b.length) {
            val c = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(p[j] + 1, cur[j - 1] + 1, p[j - 1] + c)
        }
        p = cur
    }
    return p[b.length]
}

/** Совпадение слова запроса со словом текста. 0 — точное вхождение; 99 — нет. */
fun fzWord(q: String, w: String, extra: Int = 0): Double {
    if (w.contains(q)) return 0.0
    val m = if (q.length <= 2) 0 else (if (q.length <= 5) 1 else 2) + extra
    if (m == 0) return 99.0
    val d = lev(q, w, m)
    if (d <= m) return d.toDouble()
    if (w.length > q.length) {
        val d2 = lev(q, w.substring(0, q.length), m)
        if (d2 <= m) return d2 + 0.5
    }
    return 99.0
}

/** Оценка текста по словам запроса: меньше — лучше, -1 — не подходит. */
fun fzScore(qw: List<String>, text: String, extra: Int = 0): Double {
    val ws = fzNorm(text).split(' ')
    var tot = 0.0
    for (q in qw) {
        var best = 99.0
        for (w in ws) {
            val d = fzWord(q, w, extra)
            if (d < best) best = d
            if (best == 0.0) break
        }
        if (best >= 99.0) return -1.0
        tot += best
    }
    return tot
}

/**
 * Треки по запросу: сначала точное вхождение; если пусто — раскладка, транслит, потом опечатки.
 */
fun <T> searchFiltered(items: List<T>, q: String, text: (T) -> String): List<T> {
    val query = q.trim().lowercase()
    if (query.isEmpty()) return emptyList()
    fun exact(s: String) = items.filter { text(it).lowercase().contains(s) }

    val first = exact(query)
    if (first.size >= 3) return first
    for (alt in listOf(layFix(query), layFixR(query)) + translit(query)) {
        if (alt.isEmpty() || alt == query) continue
        val r = exact(alt)
        if (r.size > first.size) return r
    }
    val words = fzNorm(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return first
    val scored = items.mapNotNull { x ->
        val sc = fzScore(words, text(x))
        if (sc >= 0) x to sc else null
    }.sortedBy { it.second }.map { it.first }
    return if (scored.size > first.size) scored.take(40) else first
}
