@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.gexixi.checkin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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

// ============================ 日历：什么时间段完成了什么，同时发生的左右错开 ============================
@Composable
fun CalendarScreen() {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(false) }
    var pickWeather by remember { mutableStateOf(false) }
    val weather = Repo.data.weather[date.toString()]
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { date = if (month) date.minusMonths(1) else date.minusDays(1) }) { Text("‹", fontSize = 24.sp, color = C.ink) }
            Text(
                if (month) "" + date.year + "年" + date.monthValue + "月" else "" + date.monthValue + "月" + date.dayOfMonth + "日  周" + "一二三四五六日"[date.dayOfWeek.value - 1],
                Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = C.ink
            )
            TextButton(onClick = { date = if (month) date.plusMonths(1) else date.plusDays(1) }) { Text("›", fontSize = 24.sp, color = C.ink) }
            // 天气图标：点一下选这一天的天气
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { pickWeather = true }, contentAlignment = Alignment.Center) {
                Text(weather ?: "☁️", fontSize = 20.sp, modifier = Modifier.graphicsLayer { alpha = if (weather == null) 0.35f else 1f })
            }
            TextButton(onClick = { date = LocalDate.now() }) { Text("今天", color = C.red) }
            TextButton(onClick = { month = !month }) { Text(if (month) "日" else "月", color = C.gray) }
        }
        if (month) MonthGrid(date) { date = it; month = false } else DayView(date)
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

@Composable
fun DayView(date: LocalDate) {
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
    // 块最矮 22dp：按这个高度分车道，上下相邻的块不会压在一起
    assignLanes(evs, (22f / hourDp * 3600000f).toLong())
    var sheetId by remember { mutableStateOf<String?>(null) }
    var actionEv by remember { mutableStateOf<Ev?>(null) }
    var editSession by remember { mutableStateOf<Session?>(null) }

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
        BoxWithConstraints(Modifier.fillMaxWidth().height(hourDp.dp * 24)) {
            val left = 44.dp
            val w = maxWidth - left - 8.dp
            for (h in 0..23) {
                Text("%02d:00".format(h), Modifier.offset(x = 4.dp, y = hourDp.dp * h), fontSize = 10.sp, color = C.gray)
                Box(Modifier.offset(x = left, y = hourDp.dp * h + 7.dp).width(w).height(1.dp).background(C.grid))
            }
            if (date == LocalDate.now()) {
                val ny = (now - ds) / 3600000f
                Box(Modifier.offset(x = left, y = hourDp.dp * ny + 7.dp).width(w).height(2.dp).background(C.red))
            }
            evs.forEach { e ->
                val topH = (e.st - ds) / 3600000f
                val durH = (e.vEn - e.st) / 3600000f
                val bh = hourDp.dp * durH - 1.dp
                val col = pcol(e.tag.color)
                Box(
                    Modifier.offset(x = left + w * (e.lane.toFloat() / e.lanes), y = hourDp.dp * topH + 7.dp)
                        .width(w / e.lanes).height(bh).padding(end = 2.dp)
                        .clip(RoundedCornerShape(6.dp)).background(col.copy(alpha = 0.32f))
                        .combinedClickable(onClick = { sheetId = e.s.id }, onLongClick = { actionEv = e })
                ) {
                    Row {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(col))
                        Column(Modifier.padding(start = 4.dp, top = 2.dp, end = 3.dp)) {
                            Text(
                                e.emoji + e.name + " | " + fmtMin(e.en - e.st),
                                fontSize = 11.sp, color = C.ink, maxLines = 1
                            )
                            // 放大后块变高：有空间就显示所思所感和照片，随块的高度自适应
                            if (bh > 46.dp && e.s.note.isNotBlank()) {
                                val lines = (((bh - 30.dp) / 14.dp).toInt()).coerceIn(1, 8)
                                Text(e.s.note, fontSize = 11.sp, color = C.ink.copy(alpha = 0.8f), maxLines = lines)
                            }
                            if (bh > 70.dp && e.s.photos.isNotEmpty()) {
                                AsyncImage(
                                    File(e.s.photos.first()), null,
                                    Modifier.padding(top = 3.dp).height((bh - 50.dp).coerceIn(24.dp, 160.dp)).clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
        }
        DiarySection(date.toString())
        Spacer(Modifier.height(24.dp))
    }

    sheetId?.let { id -> SessionSheet(id) { sheetId = null } }
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
            }) { Text("保存", color = C.red) }
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
                                    if (key in busy) Box(Modifier.padding(1.dp).size(5.dp).clip(CircleShape).background(C.red))
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
    var note by remember(date) { mutableStateOf(e.text) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val dir = File(ctx.filesDir, "diary/" + date)
        val paths = uris.mapNotNull { copyUri(ctx, it, dir, "d_") }
        if (paths.isNotEmpty()) Repo.diaryAddFiles(date, paths)
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("日记", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = C.ink)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { pick.launch(arrayOf("image/*", "application/pdf")) }) { Text("＋ 导入 JPG / PDF", color = C.red) }
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
        OutlinedTextField(
            note, { note = it; Repo.diarySetText(date, it) }, label = { Text("文字备注（方便以后搜索）") },
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )
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
