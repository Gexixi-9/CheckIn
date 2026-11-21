@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.gexixi.checkin

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val WEATHERS = listOf("☀️", "🌤️", "⛅", "☁️", "🌧️", "⛈️", "❄️", "🌫️", "💨")

/** 双指缩放：只在两根手指同时按着时才接管，单指上下滑动照常滚动 */
fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (ev.changes.size >= 2) {
                val z = ev.calculateZoom()
                if (z != 1f) {
                    onZoom(z)
                    ev.changes.forEach { it.consume() }
                }
            }
        } while (ev.changes.any { it.pressed })
    }
}

private fun dateTitle(date: LocalDate, month: Boolean): String =
    if (month) "" + date.year + "年" + date.monthValue + "月"
    else "" + date.monthValue + "月" + date.dayOfMonth + "日 周" + "一二三四五六日"[date.dayOfWeek.value - 1]

// ============================ 日历：什么时间段完成了什么，同时发生的左右错开 ============================
@Composable
fun CalendarScreen() {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(false) }
    var pickWeather by remember { mutableStateOf(false) }
    var sheetId by remember { mutableStateOf<String?>(null) }
    var newTask by remember { mutableStateOf(false) }
    var editTaskId by remember { mutableStateOf<String?>(null) }
    val weather = Repo.data.weather[date.toString()]
    // 日期放到顶部 Today 旁边（顶栏右侧），下面一行只留翻页和切换
    val slot = LocalHeaderSlot.current
    val title = dateTitle(date, month)
    SideEffect {
        slot.value = { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = C.ink, maxLines = 1, softWrap = false) }
    }
    DisposableEffect(Unit) { onDispose { slot.value = null } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { date = if (month) date.minusMonths(1) else date.minusDays(1) }) { Text("‹", fontSize = 24.sp, color = C.ink) }
                // 天气图标：点一下选这一天的天气
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { pickWeather = true }, contentAlignment = Alignment.Center) {
                    Text(weather ?: "☁️", fontSize = 20.sp, modifier = Modifier.graphicsLayer { alpha = if (weather == null) 0.35f else 1f })
                }
                TextButton(onClick = { date = LocalDate.now() }) { Text("今天", color = C.ink, fontWeight = FontWeight.Bold) }
                TextButton(onClick = { month = !month }) { Text(if (month) "日" else "月", color = C.ink) }
                TextButton(onClick = { date = if (month) date.plusMonths(1) else date.plusDays(1) }) { Text("›", fontSize = 24.sp, color = C.ink) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (month) MonthGrid(date) { date = it; month = false }
                else DayView(date, onOpen = { sheetId = it }, onEditTask = { editTaskId = it })
            }
        }
        if (!month) {
            MomentBar(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                onOpen = { sheetId = it },
                onTask = { newTask = true }
            )
        }
    }
    sheetId?.let { id ->
        SessionSheet(id) {
            // 点了「文字」却什么都没写，就不留空记录
            val s = Repo.data.sessions.firstOrNull { it.id == id }
            if (s != null && s.tagId == "x_note" && s.note.isBlank() && s.audio.isBlank() && s.photos.isEmpty()) Repo.deleteSession(id)
            sheetId = null
        }
    }
    if (newTask) {
        TaskEditor(
            null, { newTask = false }, presetDate = LocalDate.now().toString(), presetTime = java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }
        ) { Repo.addTask(it); newTask = false }
    }
    editTaskId?.let { id ->
        val t = Repo.data.tasks.firstOrNull { it.id == id }
        if (t != null) TaskEditor(t, { editTaskId = null }) { Repo.updateTask(it); editTaskId = null } else editTaskId = null
    }
    if (pickWeather) {
        AlertDialog(
            onDismissRequest = { pickWeather = false },
            title = { Text("这一天的天气") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WEATHERS.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { w ->
                                Box(
                                    Modifier.size(46.dp).clip(CircleShape)
                                        .background(if (w == weather) Color(0x22000000) else Color.Transparent)
                                        .clickable { Repo.setWeather(date.toString(), w); pickWeather = false },
                                    contentAlignment = Alignment.Center
                                ) { Text(w, fontSize = 26.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { Repo.setWeather(date.toString(), ""); pickWeather = false }) { Text("不显示") } },
            dismissButton = { TextButton(onClick = { pickWeather = false }) { Text("取消") } }
        )
    }
}

/** 日历底栏：文字 / 录音 / 待办。点了就在「当下这个时间」写进日历 */
@Composable
private fun MomentBar(modifier: Modifier, onOpen: (String) -> Unit, onTask: () -> Unit) {
    val ctx = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recFile by remember { mutableStateOf<File?>(null) }
    var recStart by remember { mutableLongStateOf(0L) }
    val now by produceState(System.currentTimeMillis(), recorder) {
        while (true) { value = System.currentTimeMillis(); delay(500) }
    }

    fun begin() {
        try {
            val dir = File(ctx.filesDir, "notes")
            dir.mkdirs()
            val f = File(dir, "rec_" + uid() + ".m4a")
            val r = MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r; recFile = f; recStart = System.currentTimeMillis()
        } catch (e: Exception) {
            Toast.makeText(ctx, "录音启动失败：" + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    fun finish() {
        val r = recorder ?: return
        val f = recFile
        val ok = try { r.stop(); true } catch (e: RuntimeException) { false }
        try { r.release() } catch (_: Exception) {}
        recorder = null
        if (ok && f != null && f.length() > 0) {
            val end = maxOf(System.currentTimeMillis(), recStart + 60000L)
            val id = Repo.addMoment("", f.path, recStart, end)
            onOpen(id)
        } else {
            f?.delete()
            Toast.makeText(ctx, "录音太短，没有保存", Toast.LENGTH_SHORT).show()
        }
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else Toast.makeText(ctx, "需要录音权限才能录音", Toast.LENGTH_SHORT).show()
    }
    DisposableEffect(Unit) {
        onDispose { try { recorder?.release() } catch (_: Exception) {} }
    }

    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF2FFFFFF), Color(0xD9FFFFFF))))
            .border(1.dp, Color(0x22000000), shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MomentButton("✎", "文字", false) { onOpen(Repo.addMoment("", "")) }
        MomentButton(
            if (recorder != null) "⏹" else "🎙",
            if (recorder != null) fmt(now - recStart) else "录音", recorder != null
        ) {
            if (recorder != null) finish()
            else perm.launch(Manifest.permission.RECORD_AUDIO)
        }
        MomentButton("◯", "待办", false, onTask)
    }
}

@Composable
private fun MomentButton(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(24.dp))
            .background(if (active) C.ink else Color(0x0F000000))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 15.sp, color = if (active) Color.White else C.ink)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (active) Color.White else C.ink)
    }
}

@Composable
fun DayView(date: LocalDate, onOpen: (String) -> Unit, onEditTask: (String) -> Unit) {
    val d = Repo.data
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var hourDp by remember { mutableFloatStateOf(56f) }
    val scroll = rememberScrollState(with(density) { (56.dp * 7).roundToPx() })
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(20000) }
    }
    val ds = Repo.dayStart(date)
    val evs = dayEvents(d, date, now)
    // 块最矮 24dp：按这个高度分车道，上下相邻的块不会压在一起
    evs.forEach { it.wantMs = (wantDp(it) / hourDp * 3600000f).toLong() }   // 有文字/照片/录音的块自动撑高
    assignLanes(evs, (24f / hourDp * 3600000f).toLong())
    var actionEv by remember { mutableStateOf<Ev?>(null) }
    var editSession by remember { mutableStateOf<Session?>(null) }
    val allDay = d.tasks.filter { !it.done && it.date == date.toString() && it.time.isBlank() }

    Column(
        Modifier.fillMaxSize()
            .pinchZoom { z ->
                val old = hourDp
                val nw = (old * z).coerceIn(40f, 260f)
                hourDp = nw
                scope.launch { scroll.scrollBy(scroll.value * (nw / old - 1f)) }
            }
            .verticalScroll(scroll)
    ) {
        if (allDay.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("今天的待办（没定具体时间）", fontSize = 11.sp, color = C.gray)
                allDay.forEach { t ->
                    val col = pcol(Repo.list(t.listId).color)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF4F4F2))
                            .clickable { onEditTask(t.id) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CheckCircle(false, col, 20.dp) { Repo.requestSettle(t.id) }
                        Spacer(Modifier.width(8.dp))
                        Text(t.title, fontSize = 14.sp, color = C.ink, maxLines = 1)
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(hourDp.dp * 24)) {
            val left = 44.dp
            val w = maxWidth - left - 8.dp
            for (h in 0..23) {
                Text("%02d:00".format(h), Modifier.offset(x = 4.dp, y = hourDp.dp * h), fontSize = 10.sp, color = C.gray)
                Box(Modifier.offset(x = left, y = hourDp.dp * h + 7.dp).width(w).height(1.dp).background(C.grid))
            }
            if (date == LocalDate.now()) {
                val ny = (now - ds) / 3600000f
                Box(Modifier.offset(x = left, y = hourDp.dp * ny + 7.dp).width(w).height(1.dp).background(C.ink.copy(alpha = 0.28f)))
                Box(Modifier.offset(x = left - 3.dp, y = hourDp.dp * ny + 5.dp).size(5.dp).clip(CircleShape).background(C.ink.copy(alpha = 0.45f)))
            }
            evs.forEach { e ->
                val topH = (e.st - ds) / 3600000f
                val durH = (e.vEn - e.st) / 3600000f
                // 块与块之间留缝（左右 6dp、上下各 2dp），和参考图一样不贴在一起
                val bh = hourDp.dp * durH - 4.dp
                val pos = Modifier.offset(x = left + w * (e.lane.toFloat() / e.lanes), y = hourDp.dp * topH + 9.dp)
                    .width(w / e.lanes - 6.dp).height(bh)
                if (e.isTask) {
                    val col = pcol(e.tag.color)
                    // 有具体时间的待办：白底圆角卡片，左边空心圆圈，点圆圈走结算，点卡片改任务
                    Row(
                        pos.clip(RoundedCornerShape(10.dp)).background(Color.White)
                            .border(1.dp, Color(0xFFE6E6E2), RoundedCornerShape(10.dp))
                            .clickable { onEditTask(e.taskId) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CheckCircle(false, col, 20.dp) { Repo.requestSettle(e.taskId) }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(e.name, fontSize = 13.sp, color = C.ink, maxLines = 1)
                            if (bh >= 40.dp) Text(hm(e.st), fontSize = 11.sp, color = C.gray)
                        }
                    }
                } else {
                    EventCard(e, bh, pos, onClick = { onOpen(e.s.id) }, onLong = { actionEv = e })
                }
            }
        }
        DiarySection(date.toString())
        Spacer(Modifier.height(96.dp))
    }

    actionEv?.let { e ->
        AlertDialog(
            onDismissRequest = { actionEv = null },
            title = { Text(e.emoji + " " + e.name) },
            text = { Text(hm(e.s.start) + " – " + (if (e.s.end == null) "进行中" else hm(e.s.end))) },
            confirmButton = { TextButton(onClick = { editSession = e.s; actionEv = null }) { Text("修改") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { Repo.deleteSession(e.s.id); actionEv = null }) { Text("删除", color = C.red) }
                    TextButton(onClick = { actionEv = null }) { Text("取消") }
                }
            }
        )
    }
    editSession?.let { s ->
        EditSessionDialog(s, { editSession = null }) { ns -> Repo.updateSession(ns.id) { ns }; editSession = null }
    }
}

/**
 * 时间段色块（完全参照参考图）：圆角浅色卡片 + 里面一根缩进的圆头竖条（不贴边）；
 * 从上到下依次是：照片（小圆角方块）→ 图标和名称 → 小钟表 + 时间范围 · 时长 → 录音条 → 文字。
 * 即时记录不显示名称，直接显示内容。高度不够的内容往后丢，不会互相压住。
 */
@Composable
private fun EventCard(e: Ev, bh: Dp, modifier: Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    val col = pcol(e.tag.color)
    val s = e.s
    val moment = e.tag.id == "x_note"
    val compact = bh < 40.dp
    val pad = if (compact) 3.dp else 6.dp
    var room = bh - pad * 2
    val showPh = s.photos.isNotEmpty() && room >= 40.dp
    if (showPh) room -= 42.dp
    val showTitle = !moment && room >= 16.dp
    if (showTitle) room -= 18.dp
    val showTime = !moment && !compact && room >= 14.dp
    if (showTime) room -= 16.dp
    val showAudio = s.audio.isNotBlank() && room >= 34.dp
    if (showAudio) room -= 38.dp
    val noteLines = if (s.note.isNotBlank()) (room / 15.dp).toInt().coerceIn(0, 6) else 0
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.clip(shape).background(col.copy(alpha = 0.20f)).border(1.dp, col.copy(alpha = 0.22f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        Row(Modifier.fillMaxSize().padding(start = 6.dp, end = 8.dp, top = pad, bottom = pad)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(col))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                if (showPh) {
                    Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        s.photos.take(3).forEach { p ->
                            AsyncImage(File(p), null, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        }
                    }
                }
                if (showTitle) {
                    Text(
                        e.emoji + " " + e.name + (if (compact) " · " + fmtMin(e.en - e.st) else ""),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.ink, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (showTime) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClockGlyph(11.dp, C.ink.copy(alpha = 0.55f))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            hm(e.st) + " - " + (if (s.end == null) "进行中" else hm(e.en)) + " · " + fmtMin(e.en - e.st),
                            fontSize = 11.sp, color = C.ink.copy(alpha = 0.6f), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                if (showAudio) AudioWave(s.audio, C.ink, Modifier.padding(vertical = 2.dp))
                if (noteLines > 0) {
                    Text(
                        s.note, fontSize = 12.sp, color = C.ink.copy(alpha = 0.75f), maxLines = noteLines,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 长按「修改」：图标、名称、开始/结束时间 */
@Composable
fun EditSessionDialog(s: Session, onDismiss: () -> Unit, onSave: (Session) -> Unit) {
    val ctx = LocalContext.current
    val tag = Repo.tag(s.tagId)
    var emoji by remember { mutableStateOf(s.emoji.ifBlank { tag?.emoji ?: "⭐" }) }
    var title by remember { mutableStateOf(s.title.ifBlank { tag?.name ?: "" }) }
    var st by remember { mutableLongStateOf(s.start) }
    var en by remember { mutableLongStateOf(s.end ?: 0L) }
    val base = Repo.dayStart(Instant.ofEpochMilli(s.start).atZone(ZoneId.systemDefault()).toLocalDate())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改这条记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("图标") }, singleLine = true)
                OutlinedTextField(title, { title = it }, label = { ReqLabel("名称") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("开始", Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        pickTime(ctx, hm(st)) { t -> st = base + t.take(2).toLong() * 3600000L + t.drop(3).take(2).toLong() * 60000L }
                    }) { Text(hm(st)) }
                }
                if (s.end != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("结束", Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            pickTime(ctx, hm(en)) { t -> en = base + t.take(2).toLong() * 3600000L + t.drop(3).take(2).toLong() * 60000L }
                        }) { Text(hm(en)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank() && (s.end == null || en > st)) {
                    onSave(s.copy(
                        emoji = if (emoji == tag?.emoji) "" else emoji,
                        title = if (title.trim() == tag?.name) "" else title.trim(),
                        start = st, end = if (s.end == null) null else en
                    ))
                }
            }) { Text("保存", color = C.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 点一个色块：从下往上滑出详情，可以写所思所感、拍照/选图 */
@Composable
fun SessionSheet(id: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val s = Repo.data.sessions.firstOrNull { it.id == id }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var camFile by remember { mutableStateOf<File?>(null) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = camFile
        if (ok && f != null) Repo.updateSession(id) { it.copy(photos = it.photos + f.path) } else f?.delete()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) copyUri(ctx, uri, File(ctx.filesDir, "notes"), "n_")?.let { p -> Repo.updateSession(id) { it.copy(photos = it.photos + p) } }
    }
    if (s == null) return
    val tag = Repo.tag(s.tagId)
    val col = pcol(tag?.color ?: 6)
    val day = Instant.ofEpochMilli(s.start).atZone(ZoneId.systemDefault()).toLocalDate()
    var note by remember(id) { mutableStateOf(s.note) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = Color.White) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(s.emoji.ifBlank { tag?.emoji ?: "" } + " " + s.title.ifBlank { tag?.name ?: "" }, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
            Text(
                fmtMin((s.end ?: System.currentTimeMillis()) - s.start) + (if (s.end == null) "（进行中）" else ""),
                color = col, fontWeight = FontWeight.Medium
            )
            Row(Modifier.fillMaxWidth().dashedBottom().padding(vertical = 8.dp)) {
                Text("" + day.year + "年" + day.monthValue + "月" + day.dayOfMonth + "日", Modifier.weight(1f), color = C.ink)
                Text(hm(s.start) + " – " + (if (s.end == null) "进行中" else hm(s.end)), color = C.gray)
            }
            Row(Modifier.fillMaxWidth().dashedBottom().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("分组", Modifier.weight(1f), color = C.ink)
                Box(Modifier.size(10.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(6.dp))
                Text((tag?.group ?: "") + (if (s.auto) " · 自动" else " · 手动"), color = C.gray)
            }
            if (s.audio.isNotBlank()) AudioWave(s.audio, C.ink, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF2F2F0)).padding(10.dp))
            OutlinedTextField(
                note, { note = it; Repo.updateSession(id) { x -> x.copy(note = it) } },
                label = { Text("所思所感") }, minLines = 3, modifier = Modifier.fillMaxWidth()
            )
            if (s.photos.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.photos.forEach { p ->
                        Box(Modifier.size(110.dp).clip(RoundedCornerShape(12.dp))) {
                            AsyncImage(File(p), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Text(
                                "✕", Modifier.align(Alignment.TopEnd).clickable {
                                    Repo.updateSession(id) { x -> x.copy(photos = x.photos - p) }
                                    try { File(p).delete() } catch (_: Exception) {}
                                }.padding(6.dp),
                                color = Color.White, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RippleButton("📷 拍照", {
                    val dir = File(ctx.filesDir, "notes")
                    dir.mkdirs()
                    val f = File(dir, "n_" + uid() + ".jpg")
                    camFile = f
                    cam.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".fp", f))
                })
                RippleButton("🖼 相册", { pick.launch("image/*") })
            }
        }
    }
}

@Composable
fun MonthGrid(date: LocalDate, onPick: (LocalDate) -> Unit) {
    val d = Repo.data
    val zone = ZoneId.systemDefault()
    val busy = remember(d.sessions.size, d.sessions.lastOrNull()?.end) {
        d.sessions.map { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate().toString() }.toSet()
    }
    val diaryDays = d.diary.filter { it.files.isNotEmpty() || it.text.isNotBlank() }.map { it.date }.toSet()
    val first = date.withDayOfMonth(1)
    val lead = first.dayOfWeek.value - 1
    val days = date.lengthOfMonth()
    Column(Modifier.padding(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            "一二三四五六日".forEach { Text("" + it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = C.gray) }
        }
        val cells = (0 until lead).map { 0 } + (1..days).toList()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (i in 0 until 7) {
                    val dn = week.getOrNull(i) ?: 0
                    Box(Modifier.weight(1f).height(54.dp), contentAlignment = Alignment.TopCenter) {
                        if (dn > 0) {
                            val day = date.withDayOfMonth(dn)
                            val key = day.toString()
                            val today = day == LocalDate.now()
                            Column(
                                Modifier.clip(RoundedCornerShape(10.dp)).clickable { onPick(day) }.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape).background(if (today) C.ink else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) { Text(dn.toString(), color = if (today) Color.White else C.ink, fontSize = 14.sp) }
                                Row {
                                    if (key in busy) Box(Modifier.padding(1.dp).size(5.dp).clip(CircleShape).background(C.accent))
                                    if (key in diaryDays) Text("📓", fontSize = 9.sp)
                                    val wx = d.weather[key]
                                    if (wx != null) Text(wx, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Text("红点 = 有计时记录，📓 = 有日记", fontSize = 11.sp, color = C.gray, modifier = Modifier.padding(top = 8.dp))
    }
}


// ============================ 每日日记：导入 iPad 导出的 JPG / PDF ============================
@Composable
fun DiarySection(date: String) {
    val ctx = LocalContext.current
    val e = Repo.diaryOf(date)
    var viewer by remember(date) { mutableIntStateOf(-1) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val dir = File(ctx.filesDir, "diary/" + date)
        val paths = uris.mapNotNull { copyUri(ctx, it, dir, "d_") }
        if (paths.isNotEmpty()) Repo.diaryAddFiles(date, paths)
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("日记", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = C.ink)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { pick.launch(arrayOf("image/*", "application/pdf")) }) { Text("＋ 导入 JPG / PDF", color = C.accent) }
        }
        if (e.files.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                e.files.forEachIndexed { i, f ->
                    Box(Modifier.size(100.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).clickable { viewer = i }) {
                        if (f.endsWith(".pdf", true)) PdfThumb(f)
                        else AsyncImage(File(f), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Text(
                            "✕", Modifier.align(Alignment.TopEnd).clickable { Repo.diaryRemoveFile(date, f) }.padding(6.dp),
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else Text("这一天还没有日记。从微信文件传输助手/网盘把 iPad 导出的文件发到手机，再点上面导入。", fontSize = 12.sp, color = C.gray)
    }
    if (viewer >= 0) DiaryViewer(e.files, viewer) { viewer = -1 }
}

private data class PageRef(val path: String, val idx: Int)

fun pdfPageCount(path: String): Int = try {
    ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }
} catch (e: Exception) { 0 }

fun renderPdfPage(path: String, idx: Int, width: Int): Bitmap? = try {
    ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { r ->
            r.openPage(idx).use { p ->
                val h = (width.toFloat() * p.height / p.width).toInt()
                val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }
} catch (e: Exception) { null }

@Composable
fun PdfThumb(path: String) {
    val bmp by produceState<Bitmap?>(null, path) { value = withContext(Dispatchers.IO) { renderPdfPage(path, 0, 300) } }
    bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("PDF", color = C.gray) }
}

@Composable
private fun DiaryViewer(files: List<String>, startFile: Int, onClose: () -> Unit) {
    val pages = remember(files) {
        files.flatMap { f ->
            if (f.endsWith(".pdf", true)) (0 until pdfPageCount(f)).map { PageRef(f, it) } else listOf(PageRef(f, 0))
        }
    }
    if (pages.isEmpty()) { onClose(); return }
    val start = pages.indexOfFirst { it.path == files.getOrNull(startFile) }.coerceAtLeast(0)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val pager = rememberPagerState(initialPage = start, pageCount = { pages.size })
            HorizontalPager(pager, Modifier.fillMaxSize()) { p -> ZoomPage(pages[p]) }
            Text(
                "" + (pager.currentPage + 1) + " / " + pages.size, Modifier.align(Alignment.BottomCenter).padding(16.dp),
                color = Color.White, fontSize = 13.sp
            )
            Text("✕", Modifier.align(Alignment.TopEnd).clickable(onClick = onClose).padding(20.dp), color = Color.White, fontSize = 22.sp)
        }
    }
}

@Composable
private fun ZoomPage(ref: PageRef) {
    val bmp by produceState<Bitmap?>(null, ref) {
        value = withContext(Dispatchers.IO) {
            if (ref.path.endsWith(".pdf", true)) renderPdfPage(ref.path, ref.idx, 1400)
            else BitmapFactory.decodeFile(ref.path, BitmapFactory.Options().apply { inSampleSize = 2 })
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                off = if (scale > 1f) off + pan else Offset.Zero
            }
        },
        contentAlignment = Alignment.Center
    ) {
        bmp?.let {
            Image(
                it.asImageBitmap(), null,
                Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = off.x, translationY = off.y),
                contentScale = ContentScale.Fit
            )
        }
    }
}
