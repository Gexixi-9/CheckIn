@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.gexixi.checkin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 真正完成任务：发金币 + 播一条随机鼓励音频 */
fun completeWith(ctx: Context, id: String, coins: Int?, note: String, comment: String): Cel? {
    val r = Repo.completeTask(id, coins, note, comment) ?: return null
    val ch = Media.cheer(ctx, Repo.data.prefs.character)
    return r.copy(ch = ch.ch, text = ch.text)
}

/** 勾子任务：勾完最后一个，卡片闪一下，再进结算窗；中间的勾一个就播一条鼓励语音 */
fun tickSub(ctx: Context, cur: Task, idx: Int, scope: CoroutineScope) {
    val wasDone = cur.subs[idx].done
    val allDone = Repo.toggleSub(cur.id, idx)
    if (!wasDone) {
        if (allDone) scope.launch { delay(750); Repo.requestSettle(cur.id) }
        else Media.cheer(ctx, Repo.data.prefs.character)
    }
}

// ============================ 规划页（便当格） ============================
@Composable
fun PlanPage(onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = Repo.data
    val cur = Repo.currentTask()
    val visible = Repo.visibleTasks()
    var creating by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    val shopColor by animateColorAsState(if (d.shopOpen) SHOP_BG_TOP else Color.White, tween(280), label = "shop")
    val shopFg = if (d.shopOpen) Color.White else C.ink

    BentoPage(header = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GearButton(onSettings)
            Spacer(Modifier.width(12.dp))
            Text("规划", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.ink)
        }
    }) { cell ->
        // 最大的卡：当前任务，任务名用最大字号
        val tl = cur?.let { Repo.list(it.listId) }
        Bento(
            Modifier.fillMaxWidth().height(cell * 2 + 12.dp),
            color = if (tl != null) soft(tl.color, 0.22f) else C.note, title = "当前任务",
            detail = { TaskDetailScreen() }
        ) {
            if (cur == null || tl == null) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("现在没有任务", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Text("点下面的「＋任务」或「AI拆分」", fontSize = 14.sp, color = C.gray)
                }
            } else {
                val col = pcol(tl.color)
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tl.emoji + " " + tl.name, color = darker(col, 0.75f), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        StarRow(cur.stars, 18.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(cur.title, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, color = C.ink, maxLines = 4)
                    if (cur.goal.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(cur.goal, fontSize = 14.sp, color = C.gray, maxLines = 2)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(coinIcon() + " × " + cur.flowers, color = C.ink, fontWeight = FontWeight.Medium)
                        if (cur.reward.isNotBlank()) {
                            Spacer(Modifier.width(10.dp))
                            ChestIcon(20.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(cur.reward, color = C.ink, maxLines = 1, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { Repo.requestSettle(cur.id) },
                        modifier = Modifier.fillMaxWidth().height(52.dp).magnetic(),
                        colors = ButtonDefaults.buttonColors(containerColor = C.ink)
                    ) { Text("完成", fontSize = 19.sp) }
                }
            }
        }
        BentoRow {
            // 商城开关卡：开启时主色底、白字；关闭时白底，点不开
            Bento(Modifier.size(cell), color = shopColor, detailColor = SHOP_BG_TOP, title = "游戏商城", detail = { ShopScreen() }, enabled = d.shopOpen) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("商城", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = shopFg)
                        ElasticSwitch(d.shopOpen, { Repo.setShopOpen(it) }, onColor = Color(0xFF8A8A8E))
                    }
                    Spacer(Modifier.weight(1f))
                    ChestIcon(34.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(if (d.shopOpen) "点开逛逛" else "已关闭", fontSize = 13.sp, color = shopFg)
                }
            }
            Bento(Modifier.size(cell), color = soft(10, 0.3f), title = "金币", detail = { CoinLogScreen() }) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("金币", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = C.ink)
                    Spacer(Modifier.weight(1f))
                    Text(coinIcon() + " " + Repo.balance(), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = darker(C.gold, 0.75f))
                    Text("今天 +" + Repo.todayFlowers() + " / 目标 " + d.prefs.dailyGoal, fontSize = 11.sp, color = C.gray)
                    Spacer(Modifier.height(4.dp))
                    Bar(Repo.todayFlowers().toFloat() / d.prefs.dailyGoal.coerceAtLeast(1), C.gold, Modifier.fillMaxWidth())
                }
            }
        }
        // 进度任务：子任务连成一条线，实心 = 已完成，空心 = 还没做；直接点圆圈勾选
        Bento(Modifier.fillMaxWidth().height(cell), color = soft(11, 0.28f), enabled = false) {
            if (cur == null || cur.subs.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Center) {
                    Text("进度", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = C.ink)
                    Text(if (cur == null) "没有当前任务" else "这个任务没有子任务", fontSize = 13.sp, color = C.gray)
                }
            } else {
                val col = pcol(Repo.list(cur.listId).color)
                val n = cur.subs.count { it.done }
                val next = cur.subs.firstOrNull { !it.done }
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("进度  $n/" + cur.subs.size, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = C.ink)
                        Spacer(Modifier.width(10.dp))
                        Text(if (next != null) "下一步：" + next.text else "全部完成", Modifier.weight(1f), fontSize = 12.sp, color = C.gray, maxLines = 1)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        cur.subs.forEachIndexed { i, s ->
                            if (i > 0) {
                                Box(Modifier.weight(1f).height(3.dp).background(if (cur.subs[i - 1].done && s.done) col else col.copy(alpha = 0.25f)))
                            }
                            CheckCircle(s.done, col, 32.dp) { tickSub(ctx, cur, i, scope) }
                        }
                    }
                }
            }
        }
        BentoRow {
            Bento(Modifier.size(cell), color = soft(4, 0.4f), title = "待办", detail = { TodoScreen() }) {
                val pending = Repo.pendingTasks()
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("待办", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = C.ink)
                    Spacer(Modifier.weight(1f))
                    Text(pending.size.toString(), fontSize = 54.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Text("个未完成", fontSize = 12.sp, color = C.gray)
                }
            }
            // 启动状态：压力 × 能量 四象限
            Bento(Modifier.size(cell), color = soft(2, 0.4f), title = "启动状态", detail = { StateScreen() }) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("启动状态", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = C.ink)
                    Spacer(Modifier.weight(1f))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(listOf(1, 4), listOf(2, 3)).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { s ->
                                    val c = visible.count { it.startState == s }
                                    Box(
                                        Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x66FFFFFF)),
                                        contentAlignment = Alignment.Center
                                    ) { Text(STATE_EMOJI[s] + " " + c, fontSize = 13.sp, color = C.ink) }
                                }
                            }
                        }
                    }
                }
            }
        }
        BentoRow {
            Bento(Modifier.size(cell), color = C.ink, onClick = { creating = true }) {
                Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.magnetic().size(64.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Text("＋", fontSize = 34.sp, color = C.ink)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("新任务", color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
            Bento(Modifier.size(cell), color = soft(9, 0.35f), onClick = { showAi = true }) {
                Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.magnetic().size(64.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        AndroidHeadIcon(40.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("AI 拆分", color = C.ink, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    if (creating) TaskEditor(null, { creating = false }) { Repo.addTask(it); creating = false }
    if (showAi) AiDialog { showAi = false }
}

// ============================ 当前任务详情（卡片展开后） ============================
@Composable
fun TaskDetailScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cur = Repo.currentTask()
    val others = Repo.visibleTasks().filter { it.id != cur?.id }
    var editing by remember { mutableStateOf<Task?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            if (cur == null) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = C.note)) {
                    Text("现在没有任务", Modifier.padding(28.dp), color = C.ink)
                }
            } else {
                CurrentCard(
                    t = cur,
                    onDone = { Repo.requestSettle(cur.id) },
                    onTick = { idx -> tickSub(ctx, cur, idx, scope) },
                    onSpeak = { Voice.announce(ctx, cur) },
                    onEdit = { editing = cur },
                    onDelete = { Repo.deleteTask(cur.id) }
                )
            }
        }
        if (cur != null && others.isNotEmpty()) item { Text("下一个：" + others.first().title, fontSize = 12.sp, color = C.gray) }
        item { Text("点下面任意待办，让它成为当前任务", fontSize = 12.sp, color = C.gray) }
        items(others, key = { it.id }) { t ->
            TodoRow(t, onDone = { Repo.requestSettle(t.id) }, onPush = { Repo.setCurrent(t.id); Voice.announce(ctx, t) })
        }
    }
    editing?.let { e -> TaskEditor(e, { editing = null }) { Repo.updateTask(it); editing = null } }
}

/** 当前任务卡：有子任务时，整张卡的底色从左往右按完成比例填充；每勾一项平滑推进，全部完成时整张卡闪一下 */
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
    val frac = if (t.subs.isEmpty()) 0f else t.subs.count { it.done }.toFloat() / t.subs.size
    val fillA by animateFloatAsState(frac, tween(550), label = "fill")
    val flash = remember { Animatable(0f) }
    LaunchedEffect(frac) {
        if (t.subs.isNotEmpty() && frac >= 1f) {
            flash.snapTo(0f)
            flash.animateTo(0.75f, tween(260))
            flash.animateTo(0f, tween(480))
        }
    }
    Card(
        Modifier.fillMaxWidth().rotate(-1f),
        colors = CardDefaults.cardColors(containerColor = C.note),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .drawBehind {
                    if (t.subs.isNotEmpty()) drawRect(col.copy(alpha = 0.3f), size = Size(size.width * fillA, size.height))
                }
                .drawWithContent {
                    drawContent()
                    if (flash.value > 0f) drawRect(Color.White.copy(alpha = flash.value))
                }
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tl.emoji + " " + tl.name, color = darker(col, 0.8f), fontWeight = FontWeight.Bold)
                    if (t.startState in 1..4) Text("　" + STATE_EMOJI[t.startState] + STATE_NAMES[t.startState], fontSize = 12.sp, color = C.gray)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("完成奖励：" + coinIcon() + " × " + t.flowers, color = C.ink)
                    if (t.reward.isNotBlank()) {
                        Text("　+ ", color = C.ink)
                        ChestIcon(20.dp)
                        Text(" " + t.reward, color = C.ink)
                    }
                }
                Button(
                    onClick = onDone, modifier = Modifier.fillMaxWidth().height(54.dp).magnetic(),
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
}

@Composable
fun TodoRow(t: Task, onDone: () -> Unit, onPush: () -> Unit, current: Boolean = false) {
    val tl = Repo.list(t.listId)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPush).dashedBottom().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckCircle(false, pcol(tl.color), 28.dp, onDone)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title + (if (current) "　· 当前" else ""), fontSize = 16.sp, color = C.ink)
            val meta = listOf(t.date, t.time).filter { it.isNotEmpty() }.joinToString(" ")
            if (meta.isNotEmpty()) Text(meta, fontSize = 11.sp, color = C.gray)
        }
        StarRow(t.stars, 12.sp)
        if (t.subs.isNotEmpty()) Text(t.subs.size.toString(), color = C.gray, modifier = Modifier.padding(start = 8.dp))
    }
}

// ============================ 待办列表（所有没完成的任务，大数字加列表名，行间虚线） ============================
@Composable
fun TodoScreen() {
    val ctx = LocalContext.current
    val d = Repo.data
    val cur = Repo.currentTask()
    val td = Repo.today()
    var filter by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Task?>(null) }
    val all = Repo.pendingTasks().filter { filter.isEmpty() || it.listId == filter }
    val ready = all.filter { it.date.isEmpty() || it.date <= td }     // 现在就能做的（含当前任务）
    val later = all.filter { it.date.isNotEmpty() && it.date > td }   // 日期还没到的
    val headList = d.lists.firstOrNull { it.id == filter }
    val color = pcol(headList?.color ?: 6)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.fillMaxWidth()) {
                Text(all.size.toString(), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Text(headList?.name ?: "待办", fontSize = 18.sp, color = darker(color, 0.75f), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFB8B8B2)))
            }
        }
        item {
            ChipFlow {
                FilterChip(selected = filter.isEmpty(), onClick = { filter = "" }, label = { Text("全部") })
                d.lists.forEach { l ->
                    FilterChip(selected = filter == l.id, onClick = { filter = l.id }, label = { Text(l.emoji + l.name) })
                }
            }
        }
        if (ready.isNotEmpty()) {
            item { Text("现在可以做（点一下设为当前任务）", fontSize = 12.sp, color = C.gray) }
            items(ready, key = { "r" + it.id }) { t ->
                TodoRow(
                    t, current = t.id == cur?.id, onDone = { Repo.requestSettle(t.id) },
                    onPush = { Repo.setCurrent(t.id); Voice.announce(ctx, t) }
                )
            }
        }
        if (later.isNotEmpty()) {
            item { Text("之后（日期还没到，点一下可以修改）", fontSize = 12.sp, color = C.gray) }
            items(later, key = { "l" + it.id }) { t ->
                TodoRow(t, onDone = { Repo.requestSettle(t.id) }, onPush = { editing = t })
            }
        }
        if (all.isEmpty()) item { Text("这里还没有待办。点规划页的「新任务」或日历底栏的「待办」就会出现在这里。", color = C.gray, fontSize = 13.sp) }
    }
    editing?.let { e -> TaskEditor(e, { editing = null }) { Repo.updateTask(it); editing = null } }
}

// ============================ 按启动状态分类（压力 × 能量） ============================
@Composable
fun StateScreen() {
    val ctx = LocalContext.current
    val all = Repo.visibleTasks()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(1, 2, 3, 4, 0).forEach { s ->
            val list = all.filter { it.startState == s }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(STATE_EMOJI[s] + " " + (if (s == 0) "还没设置状态" else STATE_NAMES[s]) + "  " + list.size, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = C.ink)
                    if (s > 0) Text(STATE_HINTS[s], fontSize = 12.sp, color = C.gray)
                }
            }
            items(list, key = { "st" + s + it.id }) { t ->
                TodoRow(t, onDone = { Repo.requestSettle(t.id) }, onPush = { Repo.setCurrent(t.id); Voice.announce(ctx, t) })
            }
        }
    }
}

// ============================ 金币记录 ============================
@Composable
fun CoinLogScreen() {
    val d = Repo.data
    val logs = d.flowerLog.sortedByDescending { it.ts }.take(60)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text(coinIcon() + " " + Repo.balance(), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = darker(C.gold, 0.75f))
            Text("累计获得 " + d.earned + "　已花 " + d.spent, fontSize = 13.sp, color = C.gray)
            Spacer(Modifier.height(10.dp))
        }
        items(logs) { l ->
            val title = d.tasks.firstOrNull { it.id == l.taskId }?.title ?: "（已删除的任务）"
            Row(Modifier.fillMaxWidth().dashedBottom().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = C.ink, maxLines = 1)
                    Text(java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.getDefault()).format(java.util.Date(l.ts)), fontSize = 11.sp, color = C.gray)
                }
                Text("+" + l.n, color = darker(C.gold, 0.75f), fontWeight = FontWeight.Bold)
            }
        }
        if (logs.isEmpty()) item { Text("还没有金币记录，完成任务就会有", color = C.gray, fontSize = 13.sp) }
    }
}

// ============================ 任务详情 / 新建 ============================
@Composable
fun TaskEditor(initial: Task?, onDismiss: () -> Unit, presetDate: String = "", presetTime: String = "", onSave: (Task) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = Repo.data
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var hasDate by remember { mutableStateOf(initial?.date?.isNotEmpty() ?: true) }
    var date by remember { mutableStateOf(initial?.date?.ifEmpty { Repo.today() } ?: presetDate.ifEmpty { Repo.today() }) }
    var hasTime by remember { mutableStateOf(initial?.time?.isNotEmpty() ?: presetTime.isNotEmpty()) }
    var time by remember { mutableStateOf(initial?.time?.ifEmpty { "09:00" } ?: presetTime.ifEmpty { "09:00" }) }
    var rep by remember { mutableIntStateOf(initial?.repeat ?: 0) }
    var every by remember { mutableStateOf((initial?.every ?: 1).toString()) }
    var weekdays by remember { mutableStateOf(initial?.weekdays ?: emptyList()) }
    var repEnd by remember { mutableStateOf(initial?.repeatEnd ?: "") }
    var listId by remember { mutableStateOf(initial?.listId ?: d.lists.first().id) }
    var state by remember { mutableIntStateOf(initial?.startState ?: 0) }
    var goal by remember { mutableStateOf(initial?.goal ?: "") }
    var cond by remember { mutableStateOf(initial?.condition ?: "") }
    var subs by remember { mutableStateOf(initial?.subs?.joinToString("\n") { it.text } ?: "") }
    var stars by remember { mutableIntStateOf(initial?.stars ?: 1) }
    var flowers by remember { mutableStateOf((initial?.flowers ?: Repo.flowersFor(initial?.stars ?: 1)).toString()) }
    var manual by remember { mutableStateOf(initial != null) }
    var reward by remember { mutableStateOf(initial?.reward ?: "") }
    var busy by remember { mutableStateOf(false) }
    // 新建标签（任务清单）：写完名字回车，就直接生成一个，再接着写下一个
    var newEmoji by remember { mutableStateOf("📌") }
    var newColor by remember { mutableIntStateOf(6) }
    var newName by remember { mutableStateOf("") }

    fun build(): Task {
        val old = initial?.subs?.associateBy { it.text } ?: emptyMap()
        val newSubs = subs.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .map { Sub(it, old[it]?.done ?: false, old[it]?.stars ?: 1) }
        val base = initial ?: Task(title = title)
        return base.copy(
            title = title.trim(), note = note.trim(), url = url.trim(), listId = listId, startState = state,
            goal = goal.trim(), condition = cond.trim(), subs = newSubs,
            stars = stars, flowers = flowers.toIntOrNull() ?: Repo.flowersFor(stars),
            date = if (hasDate) date else "", time = if (hasTime) time else "",
            repeat = rep, every = every.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            weekdays = if (rep == 2) weekdays.sorted() else emptyList(),
            repeatEnd = if (rep > 0) repEnd else "", reward = reward.trim()
        )
    }

    fun applyFilled(r: Task) {
        goal = r.goal; cond = r.condition
        subs = r.subs.joinToString("\n") { it.text }
        stars = r.stars; flowers = r.flowers.toString(); manual = true
        if (reward.isBlank()) reward = r.reward
    }

    fun aiFill(thenSave: Boolean) {
        if (Repo.data.prefs.dsKey.isBlank()) {
            if (thenSave) onSave(build()) else Toast.makeText(ctx, "先在设置里填 DeepSeek API Key", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        scope.launch {
            try {
                val r = Ai.fill(Repo.data.prefs, build())
                applyFilled(r)
                busy = false
                if (thenSave) onSave(build())
            } catch (e: Exception) {
                busy = false
                Toast.makeText(ctx, "AI 没填成：" + (e.message ?: ""), Toast.LENGTH_LONG).show()
                if (thenSave) onSave(build())
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f), shape = RoundedCornerShape(20.dp), color = C.paper) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("取消", color = C.gray) }
                    Text(if (initial == null) "新任务" else "任务详情", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    TextButton(enabled = !busy, onClick = {
                        if (title.isNotBlank()) {
                            val empty = goal.isBlank() && cond.isBlank() && subs.isBlank()
                            if (initial == null && empty) aiFill(true) else onSave(build())
                        }
                    }) { Text(if (busy) "AI 填写中…" else "保存", color = C.accent, fontWeight = FontWeight.Bold) }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(title, { title = it }, label = { ReqLabel("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(url, { url = it }, label = { Text("URL（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("日期", Modifier.weight(1f))
                        if (hasDate) RippleButton(date, { pickDate(ctx, date) { date = it } })
                        Spacer(Modifier.width(8.dp))
                        ElasticSwitch(hasDate, { hasDate = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("具体时间（会出现在日历里，不会提醒）", Modifier.weight(1f), fontSize = 13.sp)
                        if (hasTime) RippleButton(time, { pickTime(ctx, time) { time = it } })
                        Spacer(Modifier.width(8.dp))
                        ElasticSwitch(hasTime, { hasTime = it })
                    }
                    Text("重复")
                    ChipFlow {
                        listOf("不重复", "每天", "每周", "每月", "每年").forEachIndexed { i, n ->
                            FilterChip(selected = rep == i, onClick = { rep = i }, label = { Text(n) })
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
                            ChipFlow {
                                listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, n ->
                                    val wd = i + 1
                                    FilterChip(
                                        selected = wd in weekdays,
                                        onClick = { weekdays = if (wd in weekdays) weekdays - wd else weekdays + wd },
                                        label = { Text("周" + n) }
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("结束重复", Modifier.weight(1f))
                            RippleButton(if (repEnd.isEmpty()) "一直重复" else repEnd, { pickDate(ctx, repEnd) { repEnd = it } })
                            if (repEnd.isNotEmpty()) TextButton(onClick = { repEnd = "" }) { Text("清除") }
                        }
                    }

                    Text("标签（决定颜色和 emoji；只属于任务，和「记录」里的标签、分组互不相通）", fontSize = 13.sp)
                    ChipFlow {
                        Repo.data.lists.forEach { l ->
                            FilterChip(selected = listId == l.id, onClick = { listId = l.id }, label = { Text(l.emoji + l.name) })
                        }
                    }
                    Text("新建标签：选图标和颜色，写名字后按回车就生成，可以连着写下一个", fontSize = 12.sp, color = C.gray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newEmoji, { newEmoji = it.take(4) }, singleLine = true, modifier = Modifier.width(72.dp))
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(
                            newName, { newName = it }, singleLine = true, label = { Text("标签名，回车生成") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (newName.isNotBlank()) {
                                    val l = TaskList("l_" + uid(), newName.trim(), newEmoji.ifBlank { "📌" }, newColor)
                                    Repo.addList(l)
                                    listId = l.id
                                    newName = ""
                                }
                            })
                        )
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        (0..11).forEach { i ->
                            Box(
                                Modifier.padding(4.dp).size(30.dp).clip(CircleShape).background(pcol(i))
                                    .then(if (i == newColor) Modifier.border(3.dp, C.ink, CircleShape) else Modifier)
                                    .clickable { newColor = i }
                            )
                        }
                    }

                    Text("启动状态（压力 × 能量，AI 会按它换拆法）")
                    ChipFlow {
                        (0..4).forEach { s ->
                            FilterChip(selected = state == s, onClick = { state = s }, label = { Text(STATE_EMOJI[s] + STATE_NAMES[s]) })
                        }
                    }
                    if (state in 1..4) Text(STATE_HINTS[state], fontSize = 12.sp, color = C.gray)

                    Text("下面这几项留空的话，保存时 AI 会自动填好；也可以手动改。", fontSize = 12.sp, color = C.gray)
                    RippleButton(
                        if (busy) "AI 填写中…" else "✨ 现在就让 AI 填（本次目标 / 达成条件 / 子任务 / 难度 / 奖励）",
                        { if (title.isNotBlank() && !busy) aiFill(false) }, Modifier.fillMaxWidth(), enabled = !busy
                    )
                    OutlinedTextField(goal, { goal = it }, label = { Text("本次目标（做完之后「我学会了什么」）") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cond, { cond = it }, label = { Text("达成条件（做到什么才算）") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(subs, { subs = it }, label = { Text("子任务（一行一个，每个都拆到最小一步）") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Text("难度")
                    StarRow(stars, 34.sp) { stars = it; if (!manual) flowers = Repo.flowersFor(it).toString() }
                    OutlinedTextField(
                        flowers, { flowers = it.filter { c -> c.isDigit() }.take(3); manual = true },
                        label = { Text("完成奖励金币数（结算时 AI 可能微调）") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(reward, { reward = it }, label = { Text("完成后的宝箱奖励（可选，直接领取）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
    var state by remember { mutableIntStateOf(0) }
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
                    Text("现在的启动状态（可选）", fontSize = 12.sp, color = C.gray)
                    ChipFlow {
                        (0..4).forEach { s ->
                            FilterChip(selected = state == s, onClick = { state = s }, label = { Text(STATE_EMOJI[s] + STATE_NAMES[s]) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RippleButton("📷 拍照", { cam.launch(null) })
                        Spacer(Modifier.width(8.dp))
                        RippleButton("🖼 相册", { pick.launch("image/*") })
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
                                Text("　" + coinIcon() + r.flowers, fontSize = 13.sp)
                                if (r.reward.isNotBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    ChestIcon(16.dp)
                                    Text(" " + r.reward, fontSize = 13.sp)
                                }
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
                        try { result = Ai.split(Repo.data.prefs, text, img, state) }
                        catch (e: Exception) { err = e.message ?: "失败" }
                        loading = false
                    }
                }) { Text("生成") }
            } else {
                TextButton(onClick = {
                    result.forEachIndexed { i, t -> Repo.addTask(t, makeCurrent = i == 0) }
                    Voice.announce(ctx, result.first())
                    onDismiss()
                }) { Text("确认加入", color = C.accent) }
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

// ============================ 完成前的结算窗：先对照达成条件自检、写感想，再由 AI 结算金币 ============================
@Composable
fun SettleDialog(taskId: String, onResult: (Cel?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = Repo.data.tasks.firstOrNull { it.id == taskId }
    if (t == null) { LaunchedEffect(Unit) { onResult(null) }; return }
    val p = Repo.data.prefs
    var achieved by remember { mutableIntStateOf(2) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val cap = if (p.rewardCap > 0) p.rewardCap else 10
    val planned = t.flowers.coerceAtMost(cap)
    AlertDialog(
        onDismissRequest = { if (!busy) onResult(null) },
        title = { Text("完成前先对照一下") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (t.goal.isNotBlank()) Text("本次目标：" + t.goal, fontSize = 13.sp, color = C.gray)
                if (t.condition.isNotBlank()) Text("达成条件：" + t.condition, fontSize = 14.sp, color = C.ink)
                ChipFlow {
                    listOf("还没达到", "部分达成", "完全达成").forEachIndexed { i, n ->
                        FilterChip(selected = achieved == i, onClick = { achieved = i }, label = { Text(n) })
                    }
                }
                OutlinedTextField(
                    note, { note = it }, label = { Text("写下这次的获得 / 感想") }, minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("预设金币 " + coinIcon() + planned + "，结算后可能和预设不一样。", fontSize = 12.sp, color = C.gray)
                if (err.isNotEmpty()) Text(err, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                if (busy) CircularProgressIndicator()
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (p.settleAi && p.dsKey.isNotBlank()) {
                    busy = true; err = ""
                    scope.launch {
                        try {
                            val (coins, comment) = Ai.settle(p, t, achieved, note)
                            busy = false
                            onResult(completeWith(ctx, t.id, coins, note, comment))
                        } catch (e: Exception) {
                            busy = false
                            err = "AI 结算失败（" + (e.message ?: "") + "），可以点「按预设领取」"
                        }
                    }
                } else onResult(completeWith(ctx, t.id, null, note, ""))
            }) { Text("结算", color = C.accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                TextButton(enabled = !busy, onClick = { onResult(completeWith(ctx, t.id, null, note, "")) }) { Text("按预设领取") }
                TextButton(enabled = !busy, onClick = { onResult(null) }) { Text("取消") }
            }
        }
    )
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
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(26.dp), color = Color.White, shadowElevation = 8.dp) {
                    Text(
                        c.text.ifBlank { "干得漂亮！" }, Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = C.ink
                    )
                }
                Text("▼", color = Color.White, fontSize = 16.sp)
                AsyncImage(
                    model = "file:///android_asset/popup/" + c.ch + ".gif", contentDescription = null,
                    imageLoader = loader, modifier = Modifier.size(200.dp), contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(30.dp), color = Color.White, shadowElevation = 6.dp) {
                    Row(Modifier.padding(horizontal = 26.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(coinIcon(), fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("金币 +" + c.gain, color = darker(C.gold, 0.75f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xE6FFFFFF)) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (c.comment.isNotBlank()) Text(c.comment, fontSize = 13.sp, color = C.ink, textAlign = TextAlign.Center)
                        if (c.planned >= 0 && c.planned != c.gain) Text("预设 " + c.planned + " → 实际 " + c.gain, fontSize = 12.sp, color = C.gray)
                        Text("今天已得 " + c.todayN + "（目标 " + c.goal + "）", fontSize = 13.sp, color = C.ink)
                        Bar(c.todayN.toFloat() / c.goal.coerceAtLeast(1), C.gold, Modifier.width(160.dp))
                        if (c.reward.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("宝箱奖励：", fontSize = 13.sp, color = C.ink)
                                ChestIcon(18.dp)
                                Text(" " + c.reward, fontSize = 13.sp, color = C.ink)
                            }
                        }
                        if (c.hint.isNotBlank()) Text(c.hint, fontSize = 13.sp, color = C.accent, fontWeight = FontWeight.Medium)
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
