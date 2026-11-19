@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.gexixi.checkin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate

// ============================ 记录页（便当格） ============================
@Composable
fun RecordPage(onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val d = Repo.data
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }
    val today = LocalDate.now()
    val dayStart = Repo.dayStart(today)
    var askFinish by remember { mutableStateOf(false) }
    BentoPage(header = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GearButton(onSettings)
            Spacer(Modifier.width(12.dp))
            Text("记录", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.ink)
        }
    }) { cell ->
        // 最大的卡：日历，横跨两列、高度两格，名字叫 Today
        Bento(Modifier.fillMaxWidth().height(cell * 2 + 12.dp), color = C.note, title = "Today", detail = { CalendarScreen() }) {
            Row(Modifier.fillMaxSize().padding(20.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text("Today", fontSize = 46.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = C.ink)
                    Text(
                        "" + today.monthValue + "月" + today.dayOfMonth + "日  周" + "一二三四五六日"[today.dayOfWeek.value - 1],
                        fontSize = 15.sp, color = C.gray
                    )
                    Spacer(Modifier.weight(1f))
                    val w = d.weather[today.toString()]
                    if (w != null) Text(w, fontSize = 30.sp)
                    val ms = unionMs(dayEvents(d, today, now, withTasks = false).map { it.st to it.vEn.coerceAtMost(it.en) })
                    Text("今天已记录", fontSize = 12.sp, color = C.gray)
                    Text(fmtDur(ms / 60000), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.ink)
                }
                MiniDay(Modifier.width(120.dp).fillMaxHeight())
            }
        }
        BentoRow {
            // 记录栏：10 个最常做的事，做过的实心高亮，没做的变浅，右上角 x/10
            val quick = Repo.quickTags()
            val doneIds = d.sessions.filter { it.start >= dayStart || it.end == null }.map { it.tagId }.toSet()
            val doneN = quick.count { it.id in doneIds }
            Bento(Modifier.size(cell), color = soft(5, 0.3f), title = "记录", detail = { RecordsScreen() }) {
                Text("记录", Modifier.align(Alignment.TopStart).padding(14.dp), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = C.ink)
                Text("$doneN/10", Modifier.align(Alignment.TopEnd).padding(14.dp), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = darker(pcol(5)))
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quick.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { t ->
                                val on = t.id in doneIds
                                Box(
                                    Modifier.size(24.dp).clip(CircleShape).background(if (on) pcol(t.color) else Color(0x14000000)),
                                    contentAlignment = Alignment.Center
                                ) { Text(t.emoji, fontSize = 13.sp, modifier = Modifier.alpha(if (on) 1f else 0.3f)) }
                            }
                        }
                    }
                }
            }
            // 番茄钟：点一下直接在卡片上正向计时；再点一下结束，弹出新页面填「这段计时是干什么的」，写进日历
            val sw = d.stopwatchStart
            val running = sw > 0L
            val swColor by animateColorAsState(if (running) Color(0xFFFF6A2B) else soft(10, 0.28f), tween(300), label = "sw")
            Bento(
                Modifier.size(cell), color = swColor,
                onClick = { if (running) askFinish = true else Repo.startStopwatch() }
            ) {
                val sec = if (running) ((now - sw) / 1000).toInt() else 0
                val txt = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60) else "%02d:%02d".format(sec / 60, sec % 60)
                Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        "专注计时", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (running) Color(0xCCFFFFFF) else C.gray
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        txt, fontSize = if (sec >= 3600) 34.sp else 46.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                        color = if (running) Color.White else C.gray
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(if (running) "再点一下结束" else "点一下开始", fontSize = 12.sp, color = if (running) Color(0xE6FFFFFF) else C.gray)
                }
            }
        }
        BentoRow {
            val e = Repo.diaryOf(today.toString())
            Bento(Modifier.size(cell), color = soft(7, 0.3f), title = "日记", detail = { DiaryPage() }) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("📓", fontSize = 30.sp)
                    Spacer(Modifier.weight(1f))
                    Text("日记", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = C.ink)
                    Text(if (e.files.isEmpty()) "今天还没导入" else "今天 " + e.files.size + " 个文件", fontSize = 12.sp, color = C.gray)
                }
            }
            val p = d.prefs
            val autoN = d.sessions.count { it.auto && it.start >= dayStart }
            Bento(Modifier.size(cell), color = if (p.autoOn) soft(4, 0.4f) else Color.White, enabled = false) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("自动记录", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = C.ink)
                        ElasticSwitch(p.autoOn, { v -> Repo.setPrefs { it.copy(autoOn = v) } }, onColor = pcol(5))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(if (p.autoOn) "今天自动记了 $autoN 条" else "已关闭", fontSize = 13.sp, color = C.ink)
                    Text(
                        if (Tracker.hasUsageAccess(ctx)) "少于 " + p.minMin + " 分钟的不记" else "还没开使用情况访问权限",
                        fontSize = 11.sp, color = C.gray
                    )
                }
            }
        }
        // Notion 联动：播客 / 购物 / 阅读 / 小说
        Bento(Modifier.fillMaxWidth().height(cell), color = Color.White, enabled = false) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Notion", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = C.ink)
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.prefs.notion.forEach { n ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF2F2EE))
                                .bounceClick { Notion.open(ctx, n.url) }.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(n.emoji, fontSize = 26.sp)
                            Text(n.name, fontSize = 12.sp, color = C.ink)
                        }
                    }
                }
            }
        }
    }
    if (askFinish) StopwatchDialog(if (d.stopwatchStart > 0L) now - d.stopwatchStart else 0L) { askFinish = false }
}

/** 番茄钟结束：这段计时是干什么的 */
@Composable
private fun StopwatchDialog(elapsedMs: Long, onClose: () -> Unit) {
    val d = Repo.data
    val tags = (Repo.quickTags() + d.tags.filter { !it.hidden() }).distinctBy { it.id }
    var tagId by remember { mutableStateOf("x_focus") }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("这段计时是干什么的？（" + fmtMin(elapsedMs) + "）") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("写一句，比如：写论文第三章") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("归到哪个标签（决定日历里的颜色）", fontSize = 12.sp, color = C.gray)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = tagId == "x_focus", onClick = { tagId = "x_focus" }, label = { Text("⏱️ 不归类") })
                    tags.forEach { t ->
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = tagId == t.id, onClick = { tagId = t.id }, label = { Text(t.emoji + t.name) })
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("所思所感（可选）") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { Repo.finishStopwatch(tagId, title, note); onClose() }) { Text("写入日历", color = C.accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { Repo.cancelStopwatch(); onClose() }) { Text("放弃这段", color = C.red) }
                TextButton(onClick = onClose) { Text("继续计时") }
            }
        }
    )
}

/** Today 卡里的迷你日程：24 小时竖向，同时发生的左右错开 */
@Composable
fun MiniDay(modifier: Modifier) {
    Canvas(modifier) {
        val d = Repo.data
        val ds = Repo.dayStart()
        val evs = dayEvents(d, LocalDate.now())
        assignLanes(evs, 25 * 60000L)
        val w = this.size.width
        val h = this.size.height
        for (hr in listOf(0, 6, 12, 18, 24)) {
            val y = h * hr / 24f
            drawLine(Color(0x22000000), Offset(0f, y), Offset(w, y), 1f)
        }
        evs.forEach { e ->
            val y0 = (e.st - ds) / 86400000f * h
            val y1 = (e.vEn - ds) / 86400000f * h
            val lw = w / e.lanes
            drawRoundRect(
                pcol(e.tag.color).copy(alpha = 0.8f), Offset(e.lane * lw, y0),
                Size((lw - 2f).coerceAtLeast(2f), (y1 - y0).coerceAtLeast(3f)), CornerRadius(5f)
            )
        }
    }
}

@Composable
fun DiaryPage() {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var mode by remember { mutableIntStateOf(0) }       // 0 日  1 周  2 月
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("日", "周", "月").forEachIndexed { i, n ->
                FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(n) })
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { date = LocalDate.now() }) { Text("今天", color = C.ink, fontWeight = FontWeight.Bold) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val step: (Int) -> Unit = { k ->
                date = when (mode) { 0 -> date.plusDays(k.toLong()); 1 -> date.plusWeeks(k.toLong()); else -> date.plusMonths(k.toLong()) }
            }
            TextButton(onClick = { step(-1) }) { Text("‹", fontSize = 24.sp, color = C.ink) }
            val mon = date.minusDays((date.dayOfWeek.value - 1).toLong())
            Text(
                when (mode) {
                    0 -> "" + date.monthValue + "月" + date.dayOfMonth + "日  周" + "一二三四五六日"[date.dayOfWeek.value - 1]
                    1 -> "" + mon.monthValue + "月" + mon.dayOfMonth + "日 – " + mon.plusDays(6).monthValue + "月" + mon.plusDays(6).dayOfMonth + "日"
                    else -> "" + date.year + "年" + date.monthValue + "月"
                },
                Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = C.ink
            )
            TextButton(onClick = { step(1) }) { Text("›", fontSize = 24.sp, color = C.ink) }
        }
        when (mode) {
            0 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { DiarySection(date.toString()); Spacer(Modifier.height(24.dp)) }
            1 -> DiaryWeek(date) { date = it; mode = 0 }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { MonthGrid(date) { date = it; mode = 0 } }
        }
    }
}

/** 一周总览：七天各有几个日记文件，点哪天就进哪天 */
@Composable
private fun DiaryWeek(date: LocalDate, onPick: (LocalDate) -> Unit) {
    val mon = date.minusDays((date.dayOfWeek.value - 1).toLong())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0..6).forEach { k ->
            val day = mon.plusDays(k.toLong())
            val e = Repo.diaryOf(day.toString())
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF4F4F2))
                    .clickable { onPick(day) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.width(64.dp)) {
                    Text("周" + "一二三四五六日"[k], fontWeight = FontWeight.Bold, color = C.ink)
                    Text("" + day.monthValue + "/" + day.dayOfMonth, fontSize = 12.sp, color = C.gray)
                }
                if (e.files.isEmpty()) Text("没有日记", color = C.gray, fontSize = 13.sp)
                else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    e.files.take(4).forEach { f ->
                        if (f.endsWith(".pdf", true)) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentAlignment = Alignment.Center) { Text("PDF", fontSize = 11.sp, color = C.gray) }
                        } else {
                            coil.compose.AsyncImage(java.io.File(f), null, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        }
                    }
                    if (e.files.size > 4) Text("+" + (e.files.size - 4), color = C.gray)
                }
            }
        }
    }
}

// ============================ 标签组：叠在一起的头像，点开向右依次散开 ============================
/**
 * 收起：最多 4 个圆，每个盖住前一个的 1/3，最后一个圆显示「+数字」。
 * 点开：从左到右每隔 40ms 依次散开，圆变成圆角矩形，里面是分类英文名（衬线体，深一号的同色），下面淡入中文名。
 * 再点一次（点选中的那个）：从右到左依次收回。散开后长按一个分组可以改名/换颜色/删除，最右边的 ＋ 是新增分组。
 */
@Composable
fun GroupStack(groups: List<GroupDef>, selected: String, onSelect: (String) -> Unit, onEdit: (GroupDef?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val gn = groups.size
    val n = gn + 1                       // 最后一个是「＋」
    val d = 44f
    val pillW = 80f
    val ps = (0 until n).map { i ->
        val dl = if (open) i * 40 else (n - 1 - i) * 40
        animateFloatAsState(if (open) 1f else 0f, tween(280, delayMillis = dl), label = "grp$i")
    }
    val xs = FloatArray(n)
    val ws = FloatArray(n)
    var x = 0f
    for (i in 0 until n) {
        val p = ps[i].value
        val isAdd = i == gn
        val wOpen = if (isAdd) d else pillW
        ws[i] = lerpF(if (i < 4 && !isAdd) d else 0f, wOpen, p)
        xs[i] = x
        x += ws[i] + lerpF(if (i < 3 && !isAdd) -d / 3f else 0f, 8f, p)
    }
    Box(Modifier.horizontalScroll(rememberScrollState()).height(70.dp).width((x + 8f).dp)) {
        for (i in 0 until n) {
            val p = ps[i].value
            if (i == gn) {
                Box(
                    Modifier.offset(x = xs[i].dp).size(ws[i].dp, d.dp).alpha(p).clip(CircleShape)
                        .background(Color(0xFFEDEDEA)).clickable(enabled = open) { onEdit(null) },
                    contentAlignment = Alignment.Center
                ) { Text("＋", fontSize = 20.sp, color = C.ink, maxLines = 1, softWrap = false) }
            } else {
                val g = groups[i]
                val col = pcol(g.color)
                val fill = soft(g.color, 0.55f)
                val isSel = g.name == selected
                Column(Modifier.offset(x = xs[i].dp).width(ws[i].dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(ws[i].dp, d.dp)
                            .clip(RoundedCornerShape(lerpF(22f, 12f, p).dp))
                            .background(fill)
                            .then(if (isSel && open) Modifier.border(2.dp, darker(col, 0.6f), RoundedCornerShape(12.dp)) else Modifier)
                            .combinedClickable(
                                onClick = {
                                    if (!open) open = true
                                    else if (isSel) open = false
                                    else onSelect(g.name)
                                },
                                onLongClick = { if (open) onEdit(g) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (i == 3 && gn > 4) {
                            Text("+" + (gn - 3), Modifier.alpha(1f - p), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = darker(col, 0.55f), maxLines = 1, softWrap = false)
                        } else {
                            Text(g.emoji, Modifier.alpha(1f - p), fontSize = 20.sp, maxLines = 1, softWrap = false)
                        }
                        Text(
                            g.en.ifBlank { g.name }, Modifier.alpha(p), fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp, color = darker(col, 0.5f), maxLines = 1, softWrap = false
                        )
                    }
                    Text(
                        g.name, Modifier.alpha(p).wrapContentWidth(unbounded = true), fontSize = 11.sp,
                        color = if (isSel) C.ink else C.gray, maxLines = 1, softWrap = false
                    )
                }
            }
        }
    }
}

/** 新增 / 修改分组：名称、英文名、图标、颜色；修改时可以删除（里面的标签挪到第一个分组） */
@Composable
fun GroupDialog(initial: GroupDef?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var en by remember { mutableStateOf(initial?.en ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "⭐") }
    var color by remember { mutableIntStateOf(initial?.color ?: 6) }
    var err by remember { mutableStateOf("") }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新分组" else "编辑分组") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(8) }, label = { ReqLabel("分组名称") }, singleLine = true)
                OutlinedTextField(en, { en = it.take(12) }, label = { Text("英文名（展开时显示，可不填）") }, singleLine = true)
                OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("图标（收起时显示）") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    (0..11).forEach { i ->
                        Box(
                            Modifier.padding(4.dp).size(32.dp).clip(CircleShape).background(pcol(i))
                                .then(if (i == color) Modifier.border(3.dp, C.ink, CircleShape) else Modifier)
                                .clickable { color = i }
                        )
                    }
                }
                if (err.isNotEmpty()) Text(err, color = C.red, fontSize = 12.sp)
                if (initial != null && Repo.data.groups.size > 1) {
                    TextButton(onClick = { confirmDel = true }) { Text("删除这个分组", color = C.red) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val nm = name.trim()
                val taken = Repo.data.groups.any { it.name == nm && it.name != initial?.name }
                when {
                    nm.isEmpty() -> err = "名称不能空"
                    taken -> err = "已经有这个分组了"
                    else -> {
                        val g = GroupDef(nm, en.trim(), emoji.ifBlank { "⭐" }, color)
                        if (initial == null) Repo.addGroup(g) else Repo.updateGroup(initial.name, g)
                        onDismiss()
                    }
                }
            }) { Text("保存", color = C.accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
    if (confirmDel && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDel = false },
            title = { Text("删除分组「" + initial.name + "」？") },
            text = { Text("里面的标签不会丢，会挪到第一个分组。") },
            confirmButton = { TextButton(onClick = { Repo.deleteGroup(initial.name); confirmDel = false; onDismiss() }) { Text("删除", color = C.red) } },
            dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("取消") } }
        )
    }
}

// ============================ 记录详情：标签计时（只记一天去哪了，不发金币） ============================
@Composable
fun RecordsScreen() {
    val ctx = LocalContext.current
    val d = Repo.data
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }
    var group by remember { mutableStateOf(d.widgetGroup) }
    var editGroup by remember { mutableStateOf<GroupDef?>(null) }
    var addGroup by remember { mutableStateOf(false) }
    if (d.groups.none { it.name == group }) group = d.groups.first().name
    var editing by remember { mutableStateOf<Tag?>(null) }
    var adding by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf<Pair<Tag, Long>?>(null) }
    val dayStart = Repo.dayStart()
    fun todayMs(tag: Tag): Long = d.sessions.filter { it.tagId == tag.id }.sumOf {
        (minOf(it.end ?: now, dayStart + 86400000L) - maxOf(it.start, dayStart)).coerceAtLeast(0L)
    }
    val tagMap = d.tags.associateBy { it.id }
    val runningN = d.sessions.count { it.end == null }

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("生活记录", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Text(
                        if (runningN > 0) "正在进行 $runningN 项 · 点一下结束，可以同时计多个" else "点标签开始计时，再点一下结束。长按标签可以编辑。",
                        fontSize = 12.sp, color = C.gray
                    )
                }
                Button(onClick = { adding = true }, colors = ButtonDefaults.buttonColors(containerColor = C.ink)) { Text("＋标签") }
            }
        }
        last?.let { (tag, ms) ->
            val nk = notionKeyFor(tag.id)
            if (tag.repov || nk.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = C.note)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("刚结束：" + tag.emoji + tag.name + " " + fmtMin(ms), color = C.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (nk.isNotEmpty()) {
                                val lk = Notion.link(nk)
                                Button(
                                    onClick = { Notion.open(ctx, lk?.url ?: "", tag.name + " " + fmtMin(ms)); last = null },
                                    modifier = Modifier.magnetic()
                                ) { Text("去 Notion 写感想") }
                            }
                            if (tag.repov) {
                                Button(onClick = {
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("repov", tag.name + " " + fmtMin(ms)))
                                    val i = ctx.packageManager.getLaunchIntentForPackage("com.kilo.repov")
                                    if (i != null) ctx.startActivity(i)
                                    else Toast.makeText(ctx, "没找到 repov（已复制：标签名+用时）", Toast.LENGTH_LONG).show()
                                    last = null
                                }, modifier = Modifier.magnetic()) { Text("去 repov 记录") }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupStack(d.groups, group, { group = it }) { g -> if (g == null) addGroup = true else editGroup = g }
                Spacer(Modifier.width(8.dp))
                Text("当前：$group", fontSize = 12.sp, color = C.gray)
            }
        }
        items(d.tags.filter { it.group == group && !it.hidden() }.chunked(4)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tag ->
                    val run = d.sessions.firstOrNull { it.tagId == tag.id && it.end == null && !it.auto }
                    val col = pcol(tag.color)
                    Column(
                        Modifier.weight(1f).combinedClickable(
                            onClick = {
                                val ended = Repo.toggleTag(tag.id)
                                if (ended != null) last = tag to (ended.end!! - ended.start)
                            },
                            onLongClick = { editing = tag }
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape)
                                .background(col.copy(alpha = if (run != null) 0.85f else 0.28f))
                                .then(if (run != null) Modifier.border(3.dp, col, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) { Text(tag.emoji, fontSize = 24.sp) }
                        Text(tag.name, fontSize = 12.sp, color = C.ink, maxLines = 1)
                        Text(
                            if (run != null) fmt(now - run.start) else fmtMin(todayMs(tag)),
                            fontSize = 10.sp, color = if (run != null) C.accent else C.gray
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        item { Spacer(Modifier.height(4.dp)); Text("今天的记录", color = C.gray, fontSize = 13.sp) }
        val today = d.sessions.filter { it.start >= dayStart || it.end == null }.sortedByDescending { it.start }.take(40)
        items(today, key = { it.id }) { s ->
            val tag = tagMap[s.tagId]
            if (tag != null) {
                val range = hm(s.start) + "–" + (if (s.end == null) "进行中" else hm(s.end) + "（" + fmtMin(s.end - s.start) + "）")
                Text(s.emoji.ifBlank { tag.emoji } + " " + s.title.ifBlank { tag.name } + (if (s.auto) "（自动）" else "") + "　" + range, fontSize = 14.sp, color = C.ink)
            }
        }
    }
    if (addGroup) GroupDialog(null) { addGroup = false }
    editGroup?.let { g -> GroupDialog(g) { editGroup = null } }
    if (adding) TagDialog(null, group, { adding = false }, { Repo.addTag(it); adding = false }, null)
    editing?.let { t ->
        TagDialog(t, t.group, { editing = null }, { Repo.updateTag(it); editing = null }, { Repo.deleteTag(t.id); editing = null })
    }
}

/** 新建 / 编辑标签：名称、图标、分组、颜色；编辑时可以删除 */
@Composable
fun TagDialog(initial: Tag?, group0: String, onDismiss: () -> Unit, onSave: (Tag) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "⭐") }
    var group by remember { mutableStateOf(group0) }
    var color by remember { mutableIntStateOf(initial?.color ?: groupColor(group0)) }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新标签" else "编辑标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { ReqLabel("名称") }, singleLine = true)
                OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("图标（一个 emoji）") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Repo.data.groups.map { it.name }.forEach { g ->
                        FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) })
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    (0..11).forEach { i ->
                        Box(
                            Modifier.padding(4.dp).size(32.dp).clip(CircleShape).background(pcol(i))
                                .then(if (i == color) Modifier.border(3.dp, C.ink, CircleShape) else Modifier)
                                .clickable { color = i }
                        )
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDel = true }) { Text("删除这个标签（记录也会一起删）", color = C.red) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onSave((initial ?: Tag(uid())).copy(name = name.trim(), emoji = emoji.ifBlank { "⭐" }, group = group, color = color))
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
    if (confirmDel && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDel = false },
            title = { Text("删除标签「" + (initial?.name ?: "") + "」？") },
            text = { Text("它的计时记录也会一起删除。") },
            confirmButton = { TextButton(onClick = { confirmDel = false; onDelete() }) { Text("删除", color = C.red) } },
            dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("取消") } }
        )
    }
}

/** 必填项的标签：文字后面带红色 * */
@Composable
fun ReqLabel(text: String) {
    Row { Text(text); Text(" *", color = C.accent, fontWeight = FontWeight.Bold) }
}
