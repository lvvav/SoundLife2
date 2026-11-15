package app.soundlife

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.Player

/** Таймер сна: через заданное время ставит воспроизведение на паузу. Живёт, пока открыто приложение. */
object SleepTimer {
    private val handler = Handler(Looper.getMainLooper())
    private var pauser: Runnable? = null

    /** Минуты до остановки; 0 — выключен. */
    var minutes by mutableIntStateOf(0)

    val options = listOf(0, 15, 30, 60)

    fun next(current: Int): Int = options[(options.indexOf(current) + 1).let { if (it < 0) 0 else it } % options.size]

    fun set(c: Player, min: Int) {
        pauser?.let { handler.removeCallbacks(it) }
        pauser = null
        minutes = min
        if (min > 0) {
            val r = Runnable {
                c.pause()
                minutes = 0
                pauser = null
            }
            pauser = r
            handler.postDelayed(r, min * 60_000L)
        }
    }
}
