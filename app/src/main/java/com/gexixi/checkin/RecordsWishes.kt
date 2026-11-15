@file:OptIn(ExperimentalMaterial3Api::class)

package com.gexixi.checkin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

// ============================ 记录页：生活记录（只记一天去哪了，不发小红花） ============================
@Composable
fun RecordsScreen() {
    val ctx = LocalContext.current
    val d = Repo.data
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }
    var group by remember { mutableStateOf(d.widgetGroup) }
    var showAdd by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Tag?>(null) }
    var last by remember { mutableStateOf<Pair<Tag, Long>?>(null) }
    val dayStart = Repo.dayStart()
    fun todayMs(tag: Tag): Long = d.sessions.filter { it.tagId == tag.id }.sumOf {
        (minOf(it.end ?: now, dayStart + 86400000L) - maxOf(it.start, dayStart)).coerceAtLeast(0L)
    }
    val tagMap = d.tags.associateBy { it.id }
    val runningN = d.sessions.count { it.end == null }

    LazyColumn(
        Modifier.fillMaxSize().paper(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("生活记录", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { manage = !manage }) { Text(if (manage) "完成" else "管理", color = C.gray) }
                Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = C.ink)) { Text("＋标签") }
            }
        }
        item {
            Text(
                if (manage) "点标签可以删除" else if (runningN > 0) "正在进行 " + runningN + " 项 · 点一下结束，可以同时计多个" else "点标签开始计时，再点一下结束。这里不发小红花。",
                fontSize = 12.sp, color = C.gray
            )
        }
        last?.let { (tag, ms) ->
            if (tag.repov) item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = C.note)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("刚结束：" + tag.emoji + tag.name + " " + fmtMin(ms), Modifier.weight(1f), color = C.ink)
                        Button(onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("repov", tag.name + " " + fmtMin(ms)))
                            val i = ctx.packageManager.getLaunchIntentForPackage("com.kilo.repov")
                            if (i != null) ctx.startActivity(i)
                            else Toast.makeText(ctx, "没找到 repov（已复制：标签名+用时）", Toast.LENGTH_LONG).show()
                            last = null
                        }) { Text("去 repov 记录") }
                    }
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ALL_GROUPS.forEach { g ->
                    FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) })
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
        items(d.tags.filter { it.group == group }.chunked(4)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tag ->
                    val run = d.sessions.firstOrNull { it.tagId == tag.id && it.end == null && !it.auto }
                    val col = pcol(tag.color)
                    Column(
                        Modifier.weight(1f).clickable {
                            if (manage) del = tag
                            else {
                                val ended = Repo.toggleTag(tag.id)
                                if (ended != null) last = tag to (ended.end!! - ended.start)
                            }
                        },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape)
                                .background(col.copy(alpha = if (run != null) 0.85f else 0.28f))
                                .then(if (run != null) Modifier.border(3.dp, col, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) { Text(tag.emoji, fontSize = 30.sp) }
                        Text(tag.name, fontSize = 12.sp, color = C.ink, maxLines = 1)
                        Text(
                            if (run != null) fmt(now - run.start) else fmtMin(todayMs(tag)),
                            fontSize = 10.sp, color = if (run != null) C.red else C.gray
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
                Text(tag.emoji + " " + tag.name + (if (s.auto) "（自动）" else "") + "　" + range, fontSize = 14.sp, color = C.ink)
            }
        }
    }
    if (showAdd) TagDialog(group, { showAdd = false }) { Repo.addTag(it); showAdd = false }
    del?.let { t ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("删除标签「" + t.name + "」？") },
            text = { Text("它的计时记录也会一起删除。") },
            confirmButton = { TextButton(onClick = { Repo.deleteTag(t.id); del = null }) { Text("删除", color = C.red) } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("取消") } }
        )
    }
}

@Composable
fun TagDialog(group0: String, onDismiss: () -> Unit, onSave: (Tag) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("⭐") }
    var group by remember { mutableStateOf(group0) }
    var color by remember { mutableIntStateOf(6) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("图标（一个 emoji）") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    ALL_GROUPS.forEach { g ->
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
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(Tag(uid(), name.trim(), emoji.ifBlank { "⭐" }, group, color)) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ============================ 心愿超市 ============================
@Composable
fun WishScreen() {
    val ctx = LocalContext.current
    val d = Repo.data
    var showAdd by remember { mutableStateOf(false) }
    val ongoing = d.wishes.filter { it.kind == 0 && it.status == 0 }
    val rewards = d.wishes.filter { it.kind == 1 }
    val done = d.wishes.filter { it.kind == 0 && it.status == 1 }.sortedByDescending { it.doneDate }
    LazyColumn(
        Modifier.fillMaxSize().paper(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("心愿超市", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Text("可用小红花 " + flowerIcon() + " " + Repo.balance(), color = C.red, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = C.ink)) { Text("许个新心愿") }
            }
        }
        item { Text("进行中心愿", color = C.gray, fontSize = 13.sp) }
        if (ongoing.isEmpty()) item { Text("还没有，点右上角许一个（可以拍零食、也可以写“看一场电影”）", fontSize = 13.sp, color = C.gray) }
        items(ongoing, key = { it.id }) { w ->
            WishRow(w, "兑换", Repo.balance() >= w.cost, onAct = {
                val err = Repo.redeem(w.id)
                if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_SHORT).show()
            }, onDelete = { Repo.deleteWish(w.id) })
        }
        if (rewards.isNotEmpty()) {
            item { Spacer(Modifier.height(6.dp)); Text("任务奖励（完成任务直接领取，不扣小红花）", color = C.gray, fontSize = 13.sp) }
            items(rewards, key = { it.id }) { w ->
                Row(Modifier.fillMaxWidth().dashedBottom().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎁", fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(w.name, Modifier.weight(1f), color = C.ink)
                    Text(if (w.status == 0) "待领取" else "已领取 " + w.doneDate, fontSize = 12.sp, color = if (w.status == 0) C.red else C.gray)
                }
            }
        }
        item { Spacer(Modifier.height(6.dp)); Text("已实现心愿", color = C.gray, fontSize = 13.sp) }
        if (done.isEmpty()) item { Text("还没有兑换过", fontSize = 13.sp, color = C.gray) }
        items(done, key = { it.id }) { w ->
            Row(Modifier.fillMaxWidth().dashedBottom().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = C.gray)
                Spacer(Modifier.width(10.dp))
                Text(w.name, Modifier.weight(1f), color = C.gray)
                Text(w.doneDate, fontSize = 12.sp, color = C.gray)
            }
        }
    }
    if (showAdd) WishDialog({ showAdd = false }) { Repo.addWish(it); showAdd = false }
}

@Composable
fun WishRow(w: Wish, actLabel: String, canAct: Boolean, onAct: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEEEEA)), contentAlignment = Alignment.Center) {
                if (w.photo.isNotEmpty()) AsyncImage(File(w.photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text("🍬", fontSize = 26.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(w.name, fontWeight = FontWeight.Medium, color = C.ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(flowerIcon() + " " + w.cost, color = C.red)
                    Spacer(Modifier.width(8.dp))
                    StarRow(w.stars, 12.sp)
                }
            }
            Button(onClick = onAct, enabled = canAct, colors = ButtonDefaults.buttonColors(containerColor = C.ink)) { Text(actLabel) }
            TextButton(onClick = onDelete) { Text("✕", color = C.gray) }
        }
    }
}

@Composable
fun WishDialog(onDismiss: () -> Unit, onSave: (Wish) -> Unit) {
    val ctx = LocalContext.current
    val p = Repo.data.prefs
    var name by remember { mutableStateOf("") }
    var stars by remember { mutableIntStateOf(3) }
    var cost by remember { mutableStateOf(p.wishStarFlowers.getOrElse(2) { 9 }.toString()) }
    var manual by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) copyUri(ctx, uri, ctx.filesDir, "wish_")?.let { photo = it }
    }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) saveBitmap(ctx, bmp, "wish_")?.let { photo = it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("许个新心愿") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("想要什么（零食、看场电影、睡个懒觉都行）") }, singleLine = true)
                Text("想要程度")
                StarRow(stars, 32.sp) {
                    stars = it
                    if (!manual) cost = p.wishStarFlowers.getOrElse(it - 1) { it * 3 }.toString()
                }
                OutlinedTextField(
                    cost, { cost = it.filter { c -> c.isDigit() }.take(4); manual = true },
                    label = { Text("需要小红花") }, singleLine = true
                )
                Row {
                    OutlinedButton(onClick = { cam.launch(null) }) { Text("📷 拍照") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { pick.launch("image/*") }) { Text("🖼 相册") }
                }
                if (photo.isNotEmpty()) Text("图片已选 ✓（只存在手机本地）", fontSize = 12.sp, color = C.gray)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(Wish(name = name.trim(), cost = cost.toIntOrNull() ?: 9, stars = stars, photo = photo))
            }) { Text("保存", color = C.red) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
