package app.soundlife

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.session.MediaController

/** Общая оболочка для полноэкранных окон: заголовок и кнопка закрыть. */
@Composable
private fun FullScreenDialog(title: String, subtitle: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Bg).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = TextC, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, "Закрыть", tint = Mut, modifier = Modifier.clickable { onClose() }.padding(8.dp))
            }
            if (subtitle.isNotEmpty()) Text(subtitle, color = Mut, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private class Ach(val title: String, val sub: String, val icon: String, val value: Double, val goal: Double)

/** Достижения: считаются по статистике и любимым, прогресс виден у каждого. */
@Composable
fun AchievementsDialog(onClose: () -> Unit) {
    val streak = remember { Stats.dailySec(365).reversed().takeWhile { it.second > 0 }.count() }
    val hours = remember { Stats.totalHours() }
    val likes = Favorites.ids.size
    val list = listOf(
        Ach("Первые шаги", "Послушать 10 минут", "🎧", hours * 60, 10.0),
        Ach("Коллекционер", "25 треков в избранном", "💗", likes.toDouble(), 25.0),
        Ach("Три дня подряд", "Слушать музыку 3 дня подряд", "🔥", streak.toDouble(), 3.0),
        Ach("10 часов музыки", "Набрать 10 часов прослушивания", "⏱", hours, 10.0),
        Ach("Фонотека", "100 треков в избранном", "💖", likes.toDouble(), 100.0),
        Ach("Неделя подряд", "Слушать музыку 7 дней подряд", "🚀", streak.toDouble(), 7.0),
    )
    val done = list.count { it.value >= it.goal }
    FullScreenDialog("Достижения", "$done из ${list.size}", onClose) {
        LazyColumn {
            items(list) { a ->
                val ok = a.value >= a.goal
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Panel),
                        contentAlignment = Alignment.Center,
                    ) { Text(a.icon, fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, color = TextC, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(a.sub, color = Mut, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(ShapePill).background(Line)) {
                            Box(Modifier.fillMaxWidth((a.value / a.goal).coerceIn(0.0, 1.0).toFloat()).height(4.dp).clip(ShapePill).background(Acc))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (ok) "Получено" else "${a.value.toInt().coerceAtMost(a.goal.toInt())}/${a.goal.toInt()}",
                        color = if (ok) Acc else Mut, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Недавние: последние прослушанные треки из библиотеки, нажатие запускает. */
@Composable
fun RecentDialog(tracks: List<Track>, c: MediaController?, onClose: () -> Unit) {
    val recent = remember(tracks) {
        Stats.recentKeys(60).mapNotNull { k -> tracks.indexOfFirst { it.path == k }.takeIf { it >= 0 } }
    }
    FullScreenDialog("Недавние", "${recent.size} треков", onClose) {
        if (recent.isEmpty()) {
            Text("Пока пусто. Послушайте что-нибудь, и тут появятся треки.", color = Mut, fontSize = 15.sp)
        } else {
            LazyColumn {
                items(recent) { idx ->
                    val t = tracks[idx]
                    Row(
                        Modifier.fillMaxWidth().clickable { c?.playTracks(tracks, idx, false); onClose() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = TextC, maxLines = 1, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(t.artist, color = Mut, maxLines = 1, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Обновления: что изменилось в последних версиях. */
@Composable
fun UpdatesDialog(onClose: () -> Unit) {
    val notes = listOf(
        "Достижения, итоги за периоды и «Моя волна» на главной",
        "«Слушаем вместе»: комнаты по коду, как на сайте",
        "Звонки в приложении, как на сайте (через фоновое соединение)",
        "Уведомления о новых сообщениях без Firebase",
        "Вход на компьютере по QR",
        "Видеокружки в чате",
        "Светлые темы берутся с сайта",
    )
    FullScreenDialog("Обновления", "Версия ${BuildConfig.VERSION_NAME}", onClose) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            notes.forEach { n ->
                Text("• $n", color = TextC, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

/** Информация и поддержка: версия сборки и как написать разработчику. */
@Composable
fun InfoDialog(onClose: () -> Unit) {
    FullScreenDialog("Информация", "", onClose) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("SoundLife ${BuildConfig.VERSION_NAME} · сборка ${BuildConfig.VERSION_CODE}", color = TextC, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Text("Вопросы и ошибки: напишите разработчику или в чате SoundLife. Пришлите версию сборки и что делали перед ошибкой.", color = Mut, fontSize = 14.sp)
        }
    }
}

/** Поделиться итогами текстом через системное окно. */
fun shareStats(ctx: android.content.Context, minutes: Int, days: Int) {
    val text = "Я слушал $minutes мин за $days дн. в SoundLife"
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Поделиться итогами"))
}
