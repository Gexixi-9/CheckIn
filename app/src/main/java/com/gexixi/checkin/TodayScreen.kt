@file:OptIn(ExperimentalMaterial3Api::class)

package com.gexixi.checkin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 完成任务：发小红花 + 播一条随机鼓励音频 + 弹窗 */
fun finishTask(ctx: Context, id: String, onCel: (Cel) -> Unit) {
    val r = Repo.completeTask(id) ?: return
    val ch = Media.cheer(ctx, Repo.data.prefs.character)
    onCel(r.copy(ch = ch.ch, text = ch.text))
}

// ============================ 今天（任务）主页 ============================
@Composable
fun TodayScreen(onCel: (Cel) -> Unit) {
    val ctx = LocalContext.current
    val d = Repo.data
    val cur = Repo.currentTask()
    val others = Repo.visibleTasks().filter { it.id != cur?.id }
    var expanded by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    val shown = others.filter { filter.isEmpty() || it.listId == filter }
    val headList = d.lists.firstOrNull { it.id == filter } ?: cur?.let { Repo.list(it.listId) }
    val headColor = pcol(headList?.color ?: 6)

    LazyColumn(
        Modifier.fillMaxSize().paper(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(flowerIcon() + " " + Repo.balance(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.red)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("今天第 " + Repo.todayFlowers() + " 朵", fontSize = 12.sp, color = C.gray)
                        Spacer(Modifier.width(8.dp))
                        Bar(Repo.todayFlowers().toFloat() / d.prefs.dailyGoal.coerceAtLeast(1), C.red, Modifier.width(90.dp))
                    }
                }
                OutlinedButton(onClick = { showAi = true }) { Text("AI拆分") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { creating = true }, colors = ButtonDefaults.buttonColors(containerColor = C.ink)) { Text("＋任务") }
            }
        }
        item {
            if (cur == null) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = C.note)) {
                    Text("现在没有任务\n点右上角「＋任务」或「AI拆分」", Modifier.padding(28.dp), color = C.ink)
                }
            } else {
                CurrentCard(
                    t = cur,
                    onDone = { finishTask(ctx, cur.id, onCel) },
                    onTick = { idx ->
                        val wasDone = cur.subs[idx].done
                        val allDone = Repo.toggleSub(cur.id, idx)
                        if (!wasDone) {
                            if (allDone) finishTask(ctx, cur.id, onCel)
                            else Media.cheer(ctx, d.prefs.character)     // 每勾完一个子任务播一条鼓励语音
                        }
                    },
                    onSpeak = { Voice.announce(ctx, cur) },
                    onEdit = { editing = cur },
                    onDelete = { Repo.deleteTask(cur.id) }
                )
            }
        }
        if (cur != null && others.isNotEmpty()) {
            item { Text("下一个：" + others.first().title, fontSize = 12.sp, color = C.gray) }
        }
        item {
            TodoHeader(others.size, headList?.name ?: "待办", headColor, expanded) { expanded = !expanded }
        }
        if (expanded) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = filter.isEmpty(), onClick = { filter = "" }, label = { Text("全部") })
                    d.lists.forEach { l ->
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = filter == l.id, onClick = { filter = l.id }, label = { Text(l.emoji + l.name) })
                    }
                }
            }
            items(shown, key = { it.id }) { t ->
                TodoRow(
                    t,
                    onDone = { finishTask(ctx, t.id, onCel) },
                    onPush = { Repo.setCurrent(t.id); Voice.announce(ctx, t) }
                )
            }
            if (shown.isEmpty()) item { Text("这里没有待办", color = C.gray, fontSize = 13.sp) }
        }
    }

    if (creating) TaskEditor(null, { creating = false }) { Repo.addTask(it); creating = false }
    editing?.let { e -> TaskEditor(e, { editing = null }) { Repo.updateTask(it); editing = null } }
    if (showAi) AiDialog({ showAi = false })
}

@Composable
fun CurrentCard(
    t: Task, onDone: () -> Unit, onTick: (Int) -> Unit,
    onSpeak: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    val tl = Repo.list(t.listId)
    val col = pcol(tl.color)
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }
    Card(
        Modifier.fillMaxWidth().rotate(-1f),
        colors = CardDefaults.cardColors(containerColor = C.note),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tl.emoji + " " + tl.name, color = col, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                StarRow(t.stars, 20.sp)
            }
            Text(t.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.ink)
            val meta = listOf(t.date, t.time).filter { it.isNotEmpty() }.joinToString(" ")
            if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = C.gray)
            if (t.note.isNotBlank()) Text(t.note, fontSize = 13.sp, color = C.gray)
            if (t.goal.isNotBlank()) Text("本次目标：" + t.goal, color = C.ink)
            if (t.condition.isNotBlank()) Text("达成条件：" + t.condition, color = C.ink)
            if (t.subs.isNotEmpty()) {
                val n = t.subs.count { it.done }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("完成进度 " + n + "/" + t.subs.size, fontWeight = FontWeight.Bold, color = C.ink)
                    Spacer(Modifier.width(10.dp))
                    Bar(n.toFloat() / t.subs.size, col, Modifier.weight(1f))
                }
                t.subs.forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CheckCircle(s.done, col) { onTick(i) }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            s.text, Modifier.weight(1f), color = if (s.done) C.gray else C.ink,
                            textDecoration = if (s.done) TextDecoration.LineThrough else null
                        )
                        StarRow(s.stars, 11.sp)
                        TextButton(onClick = { Repo.toggleTimer(t.id, i) }, contentPadding = PaddingValues(6.dp)) {
                            Text(if (t.timerStart > 0 && t.timerSub == i) "⏸" else "▶", color = col)
                        }
                    }
                }
            }
            if (t.timerStart > 0) Text("⏱ " + fmt(now - t.timerStart + t.workMs), color = col, fontWeight = FontWeight.Bold)
            else if (t.workMs > 0) Text("已用时 " + fmtMin(t.workMs), fontSize = 12.sp, color = C.gray)
            Text("完成奖励：" + flowerIcon() + " × " + t.flowers + (if (t.reward.isNotBlank()) "　+ 🎁 " + t.reward else ""), color = C.ink)
            Button(
                onClick = onDone, modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.ink)
            ) { Text("完成", fontSize = 20.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSpeak) { Text("🔊 播报", color = C.gray) }
                TextButton(onClick = onEdit) { Text("详情", color = C.gray) }
                if (t.url.isNotBlank()) {
                    TextButton(onClick = {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(t.url))) }
                        catch (e: Exception) { Toast.makeText(ctx, "打不开这个链接", Toast.LENGTH_SHORT).show() }
                    }) { Text("🔗 链接", color = C.gray) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("删除", color = C.gray) }
            }
        }
    }
}

@Composable
fun TodoHeader(count: Int, name: String, color: Color, expanded: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(count.toString(), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Text(name, fontSize = 18.sp, color = color, fontWeight = FontWeight.Medium)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(color).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) { Text(if (expanded) "✕" else "☰", color = Color.White, fontSize = 20.sp) }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFB8B8B2)))
    }
}

@Composable
fun TodoRow(t: Task, onDone: () -> Unit, onPush: () -> Unit) {
    val tl = Repo.list(t.listId)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPush).dashedBottom().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckCircle(false, pcol(tl.color), 28.dp, onDone)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontSize = 16.sp, color = C.ink)
            val meta = listOf(t.date, t.time).filter { it.isNotEmpty() }.joinToString(" ")
            if (meta.isNotEmpty()) Text(meta, fontSize = 11.sp, color = C.gray)
        }
        StarRow(t.stars, 12.sp)
        if (t.subs.isNotEmpty()) Text(t.subs.size.toString(), color = C.gray, modifier = Modifier.padding(start = 8.dp))
    }
}

// ============================ 任务详情 / 新建 ============================
@Composable
fun TaskEditor(initial: Task?, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    val ctx = LocalContext.current
    val d = Repo.data
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var hasDate by remember { mutableStateOf(initial?.date?.isNotEmpty() ?: true) }
    var date by remember { mutableStateOf(initial?.date?.ifEmpty { Repo.today() } ?: Repo.today()) }
    var hasTime by remember { mutableStateOf(initial?.time?.isNotEmpty() ?: false) }
    var time by remember { mutableStateOf(initial?.time?.ifEmpty { "09:00" } ?: "09:00") }
    var rep by remember { mutableIntStateOf(initial?.repeat ?: 0) }
    var every by remember { mutableStateOf((initial?.every ?: 1).toString()) }
    var weekdays by remember { mutableStateOf(initial?.weekdays ?: emptyList()) }
    var repEnd by remember { mutableStateOf(initial?.repeatEnd ?: "") }
    var listId by remember { mutableStateOf(initial?.listId ?: d.lists.first().id) }
    var goal by remember { mutableStateOf(initial?.goal ?: "") }
    var cond by remember { mutableStateOf(initial?.condition ?: "") }
    var subs by remember { mutableStateOf(initial?.subs?.joinToString("\n") { it.text } ?: "") }
    var stars by remember { mutableIntStateOf(initial?.stars ?: 1) }
    var flowers by remember { mutableStateOf((initial?.flowers ?: Repo.flowersFor(initial?.stars ?: 1)).toString()) }
    var manual by remember { mutableStateOf(initial != null) }
    var reward by remember { mutableStateOf(initial?.reward ?: "") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f), shape = RoundedCornerShape(20.dp), color = C.paper) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("取消", color = C.gray) }
                    Text(if (initial == null) "新任务" else "任务详情", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    TextButton(onClick = {
                        if (title.isNotBlank()) {
                            val old = initial?.subs?.associateBy { it.text } ?: emptyMap()
                            val newSubs = subs.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                .map { Sub(it, old[it]?.done ?: false, old[it]?.stars ?: 1) }
                            val base = initial ?: Task(title = title)
                            onSave(
                                base.copy(
                                    title = title.trim(), note = note.trim(), url = url.trim(), listId = listId,
                                    goal = goal.trim(), condition = cond.trim(), subs = newSubs,
                                    stars = stars, flowers = flowers.toIntOrNull() ?: Repo.flowersFor(stars),
                                    date = if (hasDate) date else "", time = if (hasTime) time else "",
                                    repeat = rep, every = every.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                    weekdays = if (rep == 2) weekdays.sorted() else emptyList(),
                                    repeatEnd = if (rep > 0) repEnd else "", reward = reward.trim()
                                )
                            )
                        }
                    }) { Text("保存", color = C.red, fontWeight = FontWeight.Bold) }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(title, { title = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(url, { url = it }, label = { Text("URL（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("日期", Modifier.weight(1f))
                        if (hasDate) OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }) { Text(date) }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = hasDate, onCheckedChange = { hasDate = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("时间（只用来排序和显示，不会提醒）", Modifier.weight(1f), fontSize = 13.sp)
                        if (hasTime) OutlinedButton(onClick = { pickTime(ctx, time) { time = it } }) { Text(time) }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = hasTime, onCheckedChange = { hasTime = it })
                    }
                    Text("重复")
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("不重复", "每天", "每周", "每月", "每年").forEachIndexed { i, n ->
                            FilterChip(selected = rep == i, onClick = { rep = i }, label = { Text(n) })
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    if (rep > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("每隔")
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(every, { every = it.filter { c -> c.isDigit() }.take(3) }, singleLine = true, modifier = Modifier.width(80.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(listOf("", "天", "周", "月", "年")[rep])
                        }
                        if (rep == 2) {
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, n ->
                                    val wd = i + 1
                                    FilterChip(
                                        selected = wd in weekdays,
                                        onClick = { weekdays = if (wd in weekdays) weekdays - wd else weekdays + wd },
                                        label = { Text("周" + n) }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("结束重复", Modifier.weight(1f))
                            OutlinedButton(onClick = { pickDate(ctx, repEnd) { repEnd = it } }) { Text(if (repEnd.isEmpty()) "一直重复" else repEnd) }
                            if (repEnd.isNotEmpty()) TextButton(onClick = { repEnd = "" }) { Text("清除") }
                        }
                    }

                    Text("标签（决定颜色和 emoji）")
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        d.lists.forEach { l ->
                            FilterChip(selected = listId == l.id, onClick = { listId = l.id }, label = { Text(l.emoji + l.name) })
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    OutlinedTextField(goal, { goal = it }, label = { Text("本次目标") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cond, { cond = it }, label = { Text("达成条件（做到什么算完成）") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(subs, { subs = it }, label = { Text("子任务（一行一个）") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Text("难度")
                    StarRow(stars, 34.sp) { stars = it; if (!manual) flowers = Repo.flowersFor(it).toString() }
                    OutlinedTextField(
                        flowers, { flowers = it.filter { c -> c.isDigit() }.take(3); manual = true },
                        label = { Text("完成奖励小红花数") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(reward, { reward = it }, label = { Text("完成后的奖励（可选，直接领取）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ============================ AI 拆分 ============================
@Composable
fun AiDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var img by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<List<Task>>(emptyList()) }
    var editIdx by remember { mutableIntStateOf(-1) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) img = imageToBase64(ctx, uri)
    }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) img = bitmapToBase64(bmp)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 拆分任务") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result.isEmpty()) {
                    OutlinedTextField(text, { text = it }, label = { Text("描述你要做的事") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { cam.launch(null) }) { Text("📷 拍照") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { pick.launch("image/*") }) { Text("🖼 相册") }
                    }
                    if (img != null) Text("已选图片 ✓（会上传到你用的 AI 服务商，涉及隐私的内容请留意）", fontSize = 12.sp, color = C.gray)
                }
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                if (loading) CircularProgressIndicator()
                result.forEachIndexed { i, r ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = C.note)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(r.title, fontWeight = FontWeight.Bold)
                            if (r.goal.isNotBlank()) Text("目标：" + r.goal, fontSize = 13.sp)
                            if (r.condition.isNotBlank()) Text("达成条件：" + r.condition, fontSize = 13.sp)
                            r.subs.forEach { Text("○ " + it.text, fontSize = 13.sp) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StarRow(r.stars, 14.sp)
                                Text("　" + flowerIcon() + r.flowers + (if (r.reward.isNotBlank()) "　🎁" + r.reward else ""), fontSize = 13.sp)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { editIdx = i }) { Text("修改") }
                            }
                        }
                    }
                }
                if (result.isNotEmpty()) Text("AI 拆得不一定合理，可以先点「修改」再确认。", fontSize = 12.sp, color = C.gray)
            }
        },
        confirmButton = {
            if (result.isEmpty()) {
                TextButton(enabled = !loading && (text.isNotBlank() || img != null), onClick = {
                    loading = true; err = ""
                    scope.launch {
                        try { result = Ai.split(Repo.data.prefs, text, img) }
                        catch (e: Exception) { err = e.message ?: "失败" }
                        loading = false
                    }
                }) { Text("生成") }
            } else {
                TextButton(onClick = {
                    result.forEachIndexed { i, t -> Repo.addTask(t, makeCurrent = i == 0) }
                    Voice.announce(ctx, result.first())
                    onDismiss()
                }) { Text("确认加入", color = C.red) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
    if (editIdx >= 0 && editIdx < result.size) {
        TaskEditor(result[editIdx], { editIdx = -1 }) { nt ->
            result = result.mapIndexed { i, t -> if (i == editIdx) nt else t }
            editIdx = -1
        }
    }
}

// ============================ 完成弹窗（参照你给的截图） ============================
@Composable
fun CelebrateDialog(c: Cel, onClose: () -> Unit) {
    val loader = rememberGifLoader()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(26.dp), color = Color.White, shadowElevation = 8.dp) {
                    Text(
                        c.text.ifBlank { "干得漂亮！" }, Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = C.ink
                    )
                }
                Text("▼", color = Color.White, fontSize = 16.sp)
                AsyncImage(
                    model = "file:///android_asset/popup/" + c.ch + ".gif", contentDescription = null,
                    imageLoader = loader, modifier = Modifier.size(220.dp), contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(30.dp), color = Color.White, shadowElevation = 6.dp) {
                    Row(Modifier.padding(horizontal = 26.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(flowerIcon(), color = C.pink, fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("小红花 +" + c.gain, color = C.pink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xE6FFFFFF)) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("今天第 " + c.todayN + " 朵（目标 " + c.goal + "）", fontSize = 13.sp, color = C.ink)
                        Bar(c.todayN.toFloat() / c.goal.coerceAtLeast(1), C.pink, Modifier.width(160.dp))
                        Text("本次获得 " + c.gain + " 朵", fontSize = 13.sp, color = C.ink)
                        if (c.reward.isNotBlank()) Text("任务奖励：🎁 " + c.reward, fontSize = 13.sp, color = C.ink)
                        if (c.hint.isNotBlank()) Text(c.hint, fontSize = 13.sp, color = C.red, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF222222)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = Color.White, fontSize = 22.sp) }
            }
        }
    }
}
