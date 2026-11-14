@file:OptIn(ExperimentalMaterial3Api::class)

package com.gexixi.checkin

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

val Red = Color(0xFFE5484D)

data class Cel(val gain: Int, val ch: String)

// ---------------- 小工具 ----------------
fun stars(n: Int) = "⭐".repeat(n)
fun hm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
fun fmt(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}
fun fmtMin(ms: Long): String = if (ms < 60000) "今天 0分" else "今天 ${ms / 60000}分"

fun pickDate(ctx: Context, init: String, onPick: (String) -> Unit) {
    val d = runCatching { LocalDate.parse(init) }.getOrElse { LocalDate.now() }
    DatePickerDialog(ctx, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}
fun pickTime(ctx: Context, onPick: (String) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPick("%02d:%02d".format(h, m)) }, 9, 0, true).show()
}

/** 任务发放：像游戏系统那样语音播报（只在开了硅基流动TTS时） */
fun announce(ctx: Context, scope: CoroutineScope, t: Task) {
    val s = Repo.data.settings
    if (!s.ttsOn) return
    val text = "新任务：" + t.title + "。" + (if (t.goal.isNotBlank()) "目标，" + t.goal + "。" else "") +
        "难度" + t.stars + "星，完成奖励" + t.flowers + "朵小红花。"
    scope.launch {
        try {
            val f = Ai.tts(ctx, s, text)
            Media.playFile(f.path)
        } catch (e: Exception) {
            Toast.makeText(ctx, "语音播报失败：" + e.message, Toast.LENGTH_LONG).show()
        }
    }
}

// ---------------- 根界面 ----------------
@Composable
fun App() {
    val ctx = LocalContext.current
    val loader = remember {
        ImageLoader.Builder(ctx).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }.build()
    }
    var tab by remember { mutableIntStateOf(0) }
    var cel by remember { mutableStateOf<Cel?>(null) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Red)) {
        Scaffold(bottomBar = {
            NavigationBar {
                listOf("🎯" to "任务", "⏱️" to "记录", "🎁" to "心愿", "⚙️" to "设置").forEachIndexed { i, (e, n) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(e) }, label = { Text(n) })
                }
            }
        }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> TasksScreen { cel = it }
                    1 -> RecordsScreen()
                    2 -> WishScreen(loader)
                    else -> SettingsScreen()
                }
            }
        }
        cel?.let { CelebrateDialog(it, loader) { cel = null } }
    }
}

// ---------------- 任务页：一次只突出一个当前任务 ----------------
@Composable
fun TasksScreen(onCelebrate: (Cel) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = Repo.data
    val pending = d.tasks.filter { !it.done }
        .sortedWith(compareBy<Task>({ it.date.ifEmpty { "9999" } }, { it.time.ifEmpty { "99:99" } }))
    val cur = pending.firstOrNull { it.id == d.currentId } ?: pending.firstOrNull()
    var showAdd by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🌺 ${d.flowers}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { showAi = true }) { Text("AI拆分") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { showAdd = true }) { Text("＋任务") }
            }
        }
        item {
            if (cur == null) {
                Card(Modifier.fillMaxWidth()) {
                    Text("现在没有任务 🎉\n点「＋任务」或「AI拆分」添加", Modifier.padding(24.dp))
                }
            } else {
                CurrentCard(
                    t = cur,
                    onDone = {
                        val gain = Repo.completeTask(cur.id)
                        val ch = Media.celebrate(ctx, Repo.data.settings.character)
                        onCelebrate(Cel(gain, ch))
                    },
                    onSpeak = { announce(ctx, scope, cur) },
                    onDelete = { Repo.deleteTask(cur.id) }
                )
            }
        }
        val others = pending.filter { it.id != cur?.id }
        if (others.isNotEmpty()) {
            item { Text("待办（点一下设为当前任务）", color = Color.Gray) }
            items(others, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth().clickable {
                    Repo.setCurrent(t.id)
                    announce(ctx, scope, t)
                }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontWeight = FontWeight.Medium)
                            val meta = listOf(t.date, t.time).filter { it.isNotEmpty() }.joinToString(" ")
                            if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = Color.Gray)
                        }
                        Text(stars(t.stars) + "  🌺" + t.flowers, fontSize = 12.sp)
                        TextButton(onClick = { Repo.deleteTask(t.id) }) { Text("✕", color = Color.Gray) }
                    }
                }
            }
        }
    }
    if (showAdd) TaskDialog({ showAdd = false }) { Repo.addTask(it); showAdd = false }
    if (showAi) AiDialog { showAi = false }
}

@Composable
fun CurrentCard(t: Task, onDone: () -> Unit, onSpeak: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当前任务", color = Red, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(stars(t.stars))
            }
            Text(t.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            val meta = listOf(t.date, t.time).filter { it.isNotEmpty() }.joinToString(" ")
            if (meta.isNotEmpty()) Text("📅 $meta", fontSize = 12.sp, color = Color.Gray)
            if (t.goal.isNotBlank()) Text("本次目标：${t.goal}")
            if (t.condition.isNotBlank()) Text("达成条件：${t.condition}")
            if (t.subs.isNotEmpty()) {
                Text("子任务 ${t.subs.count { it.done }}/${t.subs.size}", fontWeight = FontWeight.Bold)
                t.subs.forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth().clickable { Repo.toggleSub(t.id, i) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = s.done, onCheckedChange = { Repo.toggleSub(t.id, i) })
                        Text(s.text)
                    }
                }
            }
            Text("完成奖励：🌺 × ${t.flowers}")
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("完成！", fontSize = 20.sp)
            }
            Row {
                TextButton(onClick = onSpeak) { Text("🔊 播报") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("删除", color = Color.Gray) }
            }
        }
    }
}

@Composable
fun TaskDialog(onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var cond by remember { mutableStateOf("") }
    var subs by remember { mutableStateOf("") }
    var stars by remember { mutableIntStateOf(1) }
    var flowers by remember { mutableStateOf("1") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var rep by remember { mutableIntStateOf(0) }
    var repEnd by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新任务") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("任务名") }, singleLine = true)
                OutlinedTextField(goal, { goal = it }, label = { Text("本次目标") })
                OutlinedTextField(cond, { cond = it }, label = { Text("达成条件") })
                OutlinedTextField(subs, { subs = it }, label = { Text("子任务（一行一个）") }, minLines = 2)
                Text("难度")
                Row {
                    (1..5).forEach { i ->
                        Text(if (i <= stars) "⭐" else "☆", fontSize = 30.sp, modifier = Modifier.clickable { stars = i }.padding(end = 4.dp))
                    }
                }
                OutlinedTextField(flowers, { flowers = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("完成奖励小红花") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }) { Text(if (date.isEmpty()) "📅 日期" else date) }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = { pickTime(ctx) { time = it } }) { Text(if (time.isEmpty()) "🕐 时间" else time) }
                    if (date.isNotEmpty() || time.isNotEmpty()) TextButton(onClick = { date = ""; time = "" }) { Text("清除") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("不重复", "每天", "每周", "每月").forEachIndexed { i, n ->
                        FilterChip(selected = rep == i, onClick = { rep = i }, label = { Text(n) })
                        Spacer(Modifier.width(6.dp))
                    }
                }
                if (rep > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pickDate(ctx, repEnd) { repEnd = it } }) {
                            Text(if (repEnd.isEmpty()) "🔚 结束重复：永不" else "🔚 结束重复：$repEnd")
                        }
                        if (repEnd.isNotEmpty()) TextButton(onClick = { repEnd = "" }) { Text("清除") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) onSave(
                    Task(
                        title = title.trim(), goal = goal.trim(), condition = cond.trim(),
                        subs = subs.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { Sub(it) },
                        stars = stars, flowers = flowers.toIntOrNull() ?: stars,
                        date = date, time = time, repeat = rep, repeatEnd = if (rep > 0) repEnd else ""
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun AiDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<List<AiTask>>(emptyList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 拆分任务") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("描述你要做的事") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
                if (loading) CircularProgressIndicator()
                result.forEach { r ->
                    Text("• " + r.task.title + "  " + stars(r.task.stars) + " 🌺" + r.task.flowers +
                        (if (r.wish.isNotBlank()) "  🎁" + r.wish else ""))
                }
            }
        },
        confirmButton = {
            if (result.isEmpty()) {
                TextButton(enabled = !loading && text.isNotBlank(), onClick = {
                    loading = true; err = ""
                    scope.launch {
                        try { result = Ai.split(Repo.data.settings, text) } catch (e: Exception) { err = e.message ?: "失败" }
                        loading = false
                    }
                }) { Text("生成") }
            } else {
                TextButton(onClick = { Repo.addAi(result); onDismiss() }) { Text("全部加入待办") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ---------------- 完成任务弹窗：GIF + 鼓励音频 + 小红花 + 可兑换心愿 ----------------
@Composable
fun CelebrateDialog(c: Cel, loader: ImageLoader, onClose: () -> Unit) {
    val d = Repo.data
    val affordable = d.wishes.filter { !it.redeemed && it.cost <= d.flowers }
    Dialog(onDismissRequest = onClose) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AsyncImage(
                    model = "file:///android_asset/popup/${c.ch}.gif",
                    contentDescription = null,
                    imageLoader = loader,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    contentScale = ContentScale.Fit
                )
                Text("任务完成！", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("🌺 +${c.gain}", fontSize = 32.sp, color = Red, fontWeight = FontWeight.Bold)
                Text("现在一共 🌺 ${d.flowers}")
                if (affordable.isNotEmpty()) {
                    Text("现在可以兑换的心愿", fontWeight = FontWeight.Bold)
                    affordable.forEach { w ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text((if (w.ai) "🎁 " else "") + w.name + "（🌺" + w.cost + "）", Modifier.weight(1f))
                            Button(onClick = { Repo.redeem(w.id) }) { Text("兑换") }
                        }
                    }
                }
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("好的") }
            }
        }
    }
}

// ---------------- 记录页：标签计时（生活记录，与任务分开） ----------------
@Composable
fun RecordsScreen() {
    val d = Repo.data
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }
    var showAdd by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Tag?>(null) }
    val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val dayEnd = dayStart + 86400000L
    fun todayMs(tag: Tag): Long = d.sessions.filter { it.tagId == tag.id }.sumOf {
        (minOf(it.end ?: now, dayEnd) - maxOf(it.start, dayStart)).coerceAtLeast(0L)
    }
    val tagMap = d.tags.associateBy { it.id }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("生活记录", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { manage = !manage }) { Text(if (manage) "完成管理" else "管理") }
                Button(onClick = { showAdd = true }) { Text("＋标签") }
            }
        }
        item { Text(if (manage) "点标签可删除" else "点标签开始/结束，可以同时计多个", fontSize = 12.sp, color = Color.Gray) }
        items(d.tags.chunked(3)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tag ->
                    val run = d.sessions.firstOrNull { it.tagId == tag.id && it.end == null }
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier.weight(1f)
                            .clip(shape)
                            .background(Color(tag.color).copy(alpha = if (run != null) 1f else 0.8f))
                            .then(if (run != null) Modifier.border(3.dp, Color.Black.copy(alpha = 0.55f), shape) else Modifier)
                            .clickable { if (manage) del = tag else Repo.toggleTag(tag.id) }
                            .padding(vertical = 14.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(tag.emoji, fontSize = 28.sp)
                            Text(tag.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                if (run != null) fmt(now - run.start) else fmtMin(todayMs(tag)),
                                color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        item { Spacer(Modifier.height(6.dp)); Text("今天的记录", color = Color.Gray) }
        val today = d.sessions.filter { it.start >= dayStart || it.end == null }.sortedByDescending { it.start }.take(40)
        items(today, key = { it.id }) { s ->
            val tag = tagMap[s.tagId]
            if (tag != null) {
                val range = hm(s.start) + "–" + (if (s.end == null) "进行中" else hm(s.end) + "（" + ((s.end - s.start) / 60000) + "分）")
                Text(tag.emoji + " " + tag.name + "   " + range, fontSize = 14.sp)
            }
        }
    }
    if (showAdd) TagDialog({ showAdd = false }) { Repo.addTag(it); showAdd = false }
    del?.let { t ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("删除标签「${t.name}」？") },
            text = { Text("它的计时记录也会一起删除。") },
            confirmButton = { TextButton(onClick = { Repo.deleteTag(t.id); del = null }) { Text("删除", color = Red) } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("取消") } }
        )
    }
}

@Composable
fun TagDialog(onDismiss: () -> Unit, onSave: (Tag) -> Unit) {
    val palette = listOf(0xFF4C8DFF, 0xFFB06BFF, 0xFFFF8A3D, 0xFF2FBF71, 0xFF35C4D6, 0xFFE5484D, 0xFFE0B100, 0xFF8E8E93, 0xFFFF6FA5, 0xFF7C5CFC)
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("⭐") }
    var color by remember { mutableStateOf(palette[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("图标（一个emoji）") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    palette.forEach { c ->
                        Box(
                            Modifier.padding(4.dp).size(34.dp).clip(CircleShape).background(Color(c))
                                .then(if (c == color) Modifier.border(3.dp, Color.Black, CircleShape) else Modifier)
                                .clickable { color = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(Tag(uid(), name.trim(), emoji.ifBlank { "⭐" }, color)) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ---------------- 心愿超市 ----------------
@Composable
fun WishScreen(loader: ImageLoader) {
    val d = Repo.data
    var showAdd by remember { mutableStateOf(false) }
    val mine = d.wishes.filter { !it.ai && !it.redeemed }
    val ai = d.wishes.filter { it.ai && !it.redeemed }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌺 ${d.flowers}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Button(onClick = { showAdd = true }) { Text("＋心愿") }
            }
        }
        item { Text("我的心愿", color = Color.Gray) }
        if (mine.isEmpty()) item { Text("还没有，点右上角添加（可以拍照存零食）") }
        items(mine, key = { it.id }) { WishRow(it, d.flowers, loader) }
        if (ai.isNotEmpty()) {
            item { Text("AI 任务奖励心愿", color = Color.Gray) }
            items(ai, key = { it.id }) { WishRow(it, d.flowers, loader) }
        }
        item { Text("已兑换 ${d.wishes.count { it.redeemed }} 个", color = Color.Gray) }
    }
    if (showAdd) WishDialog({ showAdd = false }) { Repo.addWish(it); showAdd = false }
}

@Composable
fun WishRow(w: Wish, balance: Int, loader: ImageLoader) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEEEEE)), contentAlignment = Alignment.Center) {
                if (w.photo.isNotEmpty()) {
                    AsyncImage(
                        model = File(w.photo), contentDescription = null, imageLoader = loader,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                } else Text(if (w.ai) "🎁" else "🍬", fontSize = 28.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(w.name, fontWeight = FontWeight.Medium)
                Text("🌺 ${w.cost}", color = Red)
            }
            Button(onClick = { Repo.redeem(w.id) }, enabled = balance >= w.cost) { Text("兑换") }
            TextButton(onClick = { Repo.deleteWish(w.id) }) { Text("✕", color = Color.Gray) }
        }
    }
}

@Composable
fun WishDialog(onDismiss: () -> Unit, onSave: (Wish) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("5") }
    var photo by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val f = File(ctx.filesDir, "wish_" + uid() + ".jpg")
                ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                photo = f.path
            } catch (_: Exception) {}
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新心愿") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("想要什么") }, singleLine = true)
                OutlinedTextField(cost, { cost = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("需要小红花（越想要越多）") }, singleLine = true)
                OutlinedButton(onClick = { pick.launch("image/*") }) { Text(if (photo.isEmpty()) "📷 选照片" else "照片已选 ✓ 点击更换") }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(Wish(name = name.trim(), cost = cost.toIntOrNull() ?: 5, photo = photo)) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ---------------- 设置 ----------------
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Repo.data.settings
    fun set(f: (Settings) -> Settings) = Repo.update { it.copy(settings = f(it.settings)) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("完成任务时的鼓励音频", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            (listOf("random" to "随机") + Media.chars).forEach { (k, n) ->
                FilterChip(selected = s.character == k, onClick = { set { it.copy(character = k) } }, label = { Text(n) })
                Spacer(Modifier.width(8.dp))
            }
        }
        OutlinedButton(onClick = { Media.celebrate(ctx, s.character) }) { Text("▶ 试听") }

        Spacer(Modifier.height(12.dp))
        Text("AI 拆分任务（DeepSeek）", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        OutlinedTextField(s.dsKey, { v -> set { it.copy(dsKey = v.trim()) } }, label = { Text("API Key") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.dsModel, { v -> set { it.copy(dsModel = v.trim()) } }, label = { Text("模型") },
            singleLine = true, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(12.dp))
        Text("任务语音播报（硅基流动）", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = s.ttsOn, onCheckedChange = { v -> set { it.copy(ttsOn = v) } })
            Spacer(Modifier.width(8.dp))
            Text("发放任务时语音播报")
        }
        OutlinedTextField(s.ttsKey, { v -> set { it.copy(ttsKey = v.trim()) } }, label = { Text("API Key") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.ttsModel, { v -> set { it.copy(ttsModel = v.trim()) } }, label = { Text("模型") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.ttsVoice, { v -> set { it.copy(ttsVoice = v.trim()) } }, label = { Text("音色") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            scope.launch {
                try {
                    val f = Ai.tts(ctx, Repo.data.settings, "新任务：测试播报。难度三星，完成奖励五朵小红花。")
                    Media.playFile(f.path)
                } catch (e: Exception) {
                    Toast.makeText(ctx, "失败：" + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }) { Text("🔊 试播") }
        Text("完成任务时只播放上面的鼓励音频，不会用语音合成。", fontSize = 12.sp, color = Color.Gray)
    }
}
