package app.soundlife

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext

/** Экран «Итоги»: время за период, сравнение с предыдущим, график по дням. Как на сайте. */
@Composable
fun StatsDialog(onClose: () -> Unit) {
    var period by remember { mutableIntStateOf(7) }
    val series = remember(period) { Stats.dailySec(period) }
    val prevTotal = remember(period) { Stats.dailySec(period, period).sumOf { it.second } }
    val total = series.sumOf { it.second }
    val maxV = (series.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    val minutes = (total / 60).toInt()
    val avgMin = minutes / period.coerceAtLeast(1)
    val delta = if (prevTotal > 0) ((total - prevTotal) / prevTotal * 100).toInt() else null
    val periods = listOf(1 to "1 день", 7 to "7 дней", 31 to "31 день", 365 to "365 дней")
    var showAch by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Итоги", color = TextC, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, "Закрыть", tint = Mut, modifier = Modifier.clickable { onClose() }.padding(8.dp))
            }
            Text("Ваша музыка в цифрах", color = Mut, fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                periods.forEach { (days, label) ->
                    val sel = period == days
                    Box(
                        Modifier.clip(ShapePill).background(if (sel) TextC else Hover)
                            .clickable { period = days }.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(label, color = if (sel) Bg else TextC, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(ShapeCard).background(Panel).padding(16.dp)) {
                Text("ВЫ СЛУШАЛИ · ${periods.first { it.first == period }.second.uppercase()}", color = Mut, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$minutes", color = TextC, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text("мин", color = TextC, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (delta != null) {
                    val up = delta >= 0
                    Text(
                        (if (up) "▲ на $delta% больше" else "▼ на ${-delta}% меньше") + ", чем за прошлые $period дн.",
                        color = if (up) Acc else Mut, fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("$avgMin мин", color = TextC, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("в среднем за день", color = Mut, fontSize = 12.sp)
                    }
                    Column {
                        Text("%.1f ч".format(total / 3600), color = TextC, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("всего", color = Mut, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(ShapeCard).background(Panel).padding(16.dp)) {
                Text("ПО ДНЯМ", color = Mut, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().height(140.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(if (period > 31) 1.dp else 6.dp),
                ) {
                    series.forEach { (_, v) ->
                        val h = (140f * (v / maxV).toFloat()).coerceAtLeast(2f)
                        Box(Modifier.weight(1f).height(h.dp).clip(ShapeSmall).background(if (v == maxV) Acc else Line))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showAch = true }) { Text("Достижения", color = Acc) }
                TextButton(onClick = { shareStats(ctx, minutes, period) }) { Text("Поделиться", color = Acc) }
            }
            if (showAch) AchievementsDialog { showAch = false }
            Spacer(Modifier.height(24.dp))
        }
    }
}
