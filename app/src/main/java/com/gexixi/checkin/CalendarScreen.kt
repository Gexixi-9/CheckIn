@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.gexixi.checkin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
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
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private class Ev(val tag: Tag, val st: Long, val en: Long, val auto: Boolean) {
    var lane = 0
    var lanes = 1
}

// ============================ 日历：什么时间段完成了什么，重叠的并排显示 ============================
@Composable
fun CalendarScreen() {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().paper()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { date = if (month) date.minusMonths(1) else date.minusDays(1) }) { Text("‹", fontSize = 24.sp, color = C.ink) }
            Text(
                if (month) "" + date.year + "年" + date.monthValue + "月" else "" + date.monthValue + "月" + date.dayOfMonth + "日  周" + "一二三四五六日"[date.dayOfWeek.value - 1],
                Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = C.ink
            )
            TextButton(onClick = { date = if (month) date.plusMonths(1) else date.plusDays(1) }) { Text("›", fontSize = 24.sp, color = C.ink) }
            TextButton(onClick = { date = LocalDate.now() }) { Text("今天", color = C.red) }
            TextButton(onClick = { month = !month }) { Text(if (month) "日" else "月", color = C.gray) }
        }
        if (month) MonthGrid(date) { date = it; month = false } else DayView(date)
    }
}

@Composable
fun DayView(date: LocalDate) {
    val d = Repo.data
    val density = LocalDensity.current
    val hourDp = 56.dp
    val scroll = rememberScrollState(with(density) { (hourDp * 7).roundToPx() })
    val ds = Repo.dayStart(date)
    val de = ds + 86400000L
    val now = System.currentTimeMillis()
    val evs = d.sessions.mapNotNull { s ->
        val tag = d.tags.firstOrNull { it.id == s.tagId } ?: return@mapNotNull null
        val st = maxOf(s.start, ds)
        val en = minOf(s.end ?: now, de)
        if (en > st) Ev(tag, st, en, s.auto) else null
    }.sortedBy { it.st }
    // 重叠的并排：先分车道，再算每组重叠有几列
    val cluster = mutableListOf<Ev>()
    val laneEnds = mutableListOf<Long>()
    var clusterEnd = 0L
    fun flush() { cluster.forEach { it.lanes = laneEnds.size.coerceAtLeast(1) }; cluster.clear(); laneEnds.clear() }
    for (e in evs) {
        if (cluster.isNotEmpty() && e.st >= clusterEnd) flush()
        var idx = laneEnds.indexOfFirst { it <= e.st }
        if (idx < 0) { laneEnds.add(e.en); idx = laneEnds.size - 1 } else laneEnds[idx] = e.en
        e.lane = idx
        cluster.add(e)
        clusterEnd = maxOf(clusterEnd, e.en)
    }
    flush()

    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(hourDp * 24)) {
            val left = 44.dp
            val w = maxWidth - left - 8.dp
            for (h in 0..23) {
                Text("%02d:00".format(h), Modifier.offset(x = 4.dp, y = hourDp * h), fontSize = 10.sp, color = C.gray)
                Box(Modifier.offset(x = left, y = hourDp * h + 7.dp).width(w).height(1.dp).background(C.grid))
            }
            evs.forEach { e ->
                val topMin = (e.st - ds) / 60000f
                val durMin = (e.en - e.st) / 60000f
                val h = hourDp * (durMin / 60f)
                val col = pcol(e.tag.color)
                Box(
                    Modifier.offset(x = left + w * (e.lane.toFloat() / e.lanes), y = hourDp * (topMin / 60f) + 7.dp)
                        .width(w / e.lanes).height(if (h < 20.dp) 20.dp else h).padding(end = 2.dp)
                        .clip(RoundedCornerShape(5.dp)).background(col.copy(alpha = 0.32f))
                ) {
                    Row {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(col))
                        Text(
                            e.tag.emoji + e.tag.name + " | " + fmtMin(e.en - e.st), Modifier.padding(start = 4.dp, top = 2.dp),
                            fontSize = 11.sp, color = C.ink, maxLines = 2
                        )
                    }
                }
            }
        }
        DiarySection(date.toString())
        Spacer(Modifier.height(24.dp))
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
