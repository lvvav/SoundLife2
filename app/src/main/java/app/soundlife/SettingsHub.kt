package app.soundlife

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/** Настройки в том же порядке, что и на сайте: главный список и подэкраны. */
@Composable
internal fun SettingsHub(onClose: () -> Unit) {
    var page by rememberSaveable { mutableStateOf("") }
    var showAccount by remember { mutableStateOf(false) }
    var showUpd by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showAdmin by remember { mutableStateOf(false) }
    if (showAdmin) AdminDialog { showAdmin = false }
    if (showAccount) SettingsDialog { showAccount = false }
    if (showUpd) UpdatesDialog { showUpd = false }
    if (showInfo) InfoDialog { showInfo = false }
    val back: () -> Unit = { if (page.isEmpty()) onClose() else page = "" }
    val title = when (page) {
        "look" -> "Внешний вид"
        "play" -> "Воспроизведение"
        "notif" -> "Уведомления"
        "reset" -> "Сброс"
        "dev" -> "Для разработчиков"
        "privacy" -> "Конфиденциальность и приватность"
        "devices" -> "Устройства"
        "traffic" -> "Трафик и офлайн-режим"
        "quality" -> "Качество медиафайлов"
        "info" -> "Информация и поддержка"
        else -> "Настройки"
    }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Bg).padding(top = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextC) }
                Text(title, color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            when (page) {
                "" -> HubMain(
                    onAccount = { showAccount = true },
                    onPage = { page = it },
                    onUpdates = { showUpd = true },
                    onInfo = { showInfo = true },
                )
                "look" -> LookPage()
                "play" -> PlayPage()
                "notif" -> NotifPage()
                "reset" -> ResetPage()
                "dev" -> DevPage(onOpenAdmin = { showAdmin = true })
                "privacy" -> PrivacyPage()
                "devices" -> DevicesPage()
                "traffic" -> TrafficPage()
                "quality" -> QualityPage()
            }
        }
    }
}

@Composable
private fun HubMain(onAccount: () -> Unit, onPage: (String) -> Unit, onUpdates: () -> Unit, onInfo: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifState = if (Notifier.canPost(ctx)) "включены" else "выключены"
    val groups = listOf(
        "Аккаунт" to listOf(
            HubItem("Аккаунт", if (Auth.signedIn) (Auth.account.ifEmpty { "Вход выполнен" }) else "Вход, ник, аватарка", Icons.Filled.Person, onAccount),
        ),
        "Внешний вид" to listOf(
            HubItem("Тема и акцент", "Салатовый, цвет фона, скругление", Icons.Filled.Palette) { onPage("look") },
        ),
        "Воспроизведение" to listOf(
            HubItem("Воспроизведение", if (Settings.mix) "Смешивать с другими приложениями" else "Обычное", Icons.Filled.PlayArrow) { onPage("play") },
            HubItem("Трафик и офлайн-режим", "Офлайн-память и экономия трафика", Icons.Filled.Refresh) { onPage("traffic") },
            HubItem("Качество медиафайлов", "Исходное качество, без сжатия", Icons.Filled.Info) { onPage("quality") },
        ),
        "Связь" to listOf(
            HubItem("Уведомления", "Сообщения и звонки: $notifState", Icons.Filled.Notifications) { onPage("notif") },
            HubItem("Конфиденциальность и приватность", "Медиатека и «что я слушаю»", Icons.Filled.Person) { onPage("privacy") },
            HubItem("Устройства и «Слушать вместе»", "Комнаты и вход по коду", Icons.Filled.Build) { onPage("devices") },
        ),
        "Обслуживание" to listOf(
            HubItem("Обновления", "Что нового в приложении", Icons.Filled.Refresh, onUpdates),
            HubItem("Проверить обновление", "Скачать и установить без выхода", Icons.Filled.Refresh) {
                scope.launch { checkForUpdateNow(ctx) }
            },
            HubItem("Информация и поддержка", "О сайте, горячие клавиши, версия", Icons.Filled.Info, onInfo),
            HubItem("Сброс настроек", "Вернуть всё по умолчанию", Icons.Filled.Refresh) { onPage("reset") },
            HubItem("Для разработчиков", if (Settings.adminOn) "Админ-панель включена" else "Выключено", Icons.Filled.Build) { onPage("dev") },
        ),
    )
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        groups.forEach { (label, rows) ->
            item(key = "h_$label") { SubHeader(label) }
            items(rows, key = { it.title }) { it ->
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = it.onClick).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(it.title, color = TextC, fontSize = 16.sp)
                        Text(it.sub, color = Mut, fontSize = 13.sp, maxLines = 2)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Mut)
                }
            }
        }
    }
}

@Composable
private fun InfoLine(title: String, sub: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextC, fontSize = 15.sp)
            Text(sub, color = Mut, fontSize = 13.sp)
        }
        trailing?.invoke()
    }
}

private class HubItem(val title: String, val sub: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: () -> Unit)

@Composable
private fun LookPage() {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SubHeader("Цвет акцента") }
        itemsIndexed(ACCENTS) { i, (argb, name) ->
            Row(
                Modifier.fillMaxWidth().clickable { Settings.setAccent(i) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(argb)))
                Spacer(Modifier.width(14.dp))
                Text(name, color = TextC, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (Settings.accentCustom == null && Settings.accentIx == i) Icon(Icons.Filled.Check, null, tint = Acc)
            }
        }
        item { SubHeader("Эффекты") }
        item { ToggleRow("Анимации", "Плавное появление элементов", Settings.anim) { Settings.setAnimOn(it) } }
    }
}

@Composable
private fun PlayPage() {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ToggleRow(
                "Смешивать с другими приложениями",
                "Музыка играет поверх TikTok и других приложений, не приглушая их",
                Settings.mix,
            ) { Settings.changeMix(it) }
        }
        item {
            ToggleRow(
                "Моно-звук",
                "Левый и правый канал смешиваются — удобно, когда слушаешь одним наушником",
                Settings.mono,
            ) { Settings.changeMono(it) }
        }
    }
}

@Composable
private fun NotifPage() {
    val ctx = LocalContext.current
    val can = Notifier.canPost(ctx)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (can) "Уведомления разрешены. Сообщения и входящие звонки будут приходить." else "Уведомления выключены. Разрешите их, чтобы получать сообщения и звонки.",
            color = Mut, fontSize = 14.sp,
        )
        if (!can) {
            Button(
                onClick = { (ctx as? Activity)?.let { Notifier.askPermission(it) } },
                colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = OnAcc),
            ) { Text("Разрешить уведомления") }
        }
    }
}

@Composable
private fun ResetPage() {
    var done by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Вернуть цвет акцента, анимации и режим воспроизведения к исходным. Аккаунт и плейлисты не затрагиваются.", color = Mut, fontSize = 14.sp)
        Button(
            onClick = { Settings.resetAll(); done = true },
            colors = ButtonDefaults.buttonColors(containerColor = Hover, contentColor = TextC),
        ) { Text("Сбросить настройки") }
        if (done) Text("Настройки сброшены", color = Acc, fontSize = 13.sp)
    }
}

@Composable
private fun DevPage(onOpenAdmin: () -> Unit) {
    Column {
        ToggleRow("Админ-панель", "Правка названий, артистов, альбомов и обложек альбомов. Вход по почте или нику, паролю и секретному слову", Settings.adminOn) { Settings.setAdminPanel(it) }
        if (Settings.adminOn) {
            Button(
                onClick = onOpenAdmin,
                colors = ButtonDefaults.buttonColors(containerColor = Acc, contentColor = OnAcc),
                modifier = Modifier.padding(16.dp),
            ) { Text("Открыть админ-панель") }
        }
    }
}

@Composable
private fun PrivacyPage() {
    Column {
        InfoLine("Показывать мою медиатеку", "друзья видят мои альбомы, артистов и недавние лайки")
        InfoLine("Показывать, что я слушаю", "друзья видят трек, который играет у вас сейчас")
        Text("Если выключить оба пункта, включится приватный режим: друзья ничего не увидят о вашей музыке. Ник и аватарка остаются видны в поиске людей.",
            color = Mut, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun DevicesPage() {
    Column {
        InfoLine("Устройства", "других устройств онлайн нет: откройте SoundLife с этим аккаунтом на другом устройстве")
        InfoLine("Слушать вместе", "музыка играет одновременно у всех в комнате, пауза и треки может менять любой")
    }
}

@Composable
private fun TrafficPage() {
    Column {
        SubHeader("Офлайн и приложение")
        InfoLine("Мои файлы", "свои треки только для вас: хранятся на этом устройстве, никому не видны")
        InfoLine("Офлайн-память", "Сохраните избранное или очистите память")
    }
}

@Composable
private fun QualityPage() {
    Column {
        InfoLine("Исходное качество", "треки играют из файлов как есть, без сжатия: mp3, m4a или FLAC")
        Text("Чтобы тратить меньше трафика, включите «Экономия трафика» в разделе «Трафик и офлайн-режим»: следующие треки не будут догружаться заранее. Скачанные офлайн треки не используют интернет.",
            color = Mut, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(text.uppercase(), color = Mut, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextC, fontSize = 15.sp)
            Text(sub, color = Mut, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Acc, checkedThumbColor = OnAcc, checkedBorderColor = Acc,
                uncheckedTrackColor = Hover, uncheckedBorderColor = Line, uncheckedThumbColor = Mut,
            ),
        )
    }
}
