@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.gexixi.checkin

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

// 商城配色：简约黑白，不用彩色
val SHOP_BG_TOP = Color(0xFF141414)
private val SHOP_BG_BOTTOM = Color(0xFF000000)
private val ACCENT = Color.White
private val PANEL = Color(0x1AFFFFFF)
private val PLATE = Color.White
private val TEXT_DIM = Color(0xFFA8A8A8)

private val EMOJI_CHOICES = listOf(
    "🍬", "🍫", "🍪", "🍰", "🧋", "🍿", "🍦", "🍜", "☕", "🥤", "🛍️", "🎮",
    "🎬", "📚", "🎧", "🛌", "💄", "👗", "🧸", "🎁", "✈️", "🏖️", "💆", "🍓"
)

/** 游戏商城：深色场景，中间展示柜 + 价格，下面是商品列表；兑换成功弹「兑换成功」 */
@Composable
fun ShopScreen() {
    val ctx = LocalContext.current
    val d = Repo.data
    var tab by remember { mutableIntStateOf(0) }          // 0 商品 1 宝箱 2 已实现
    var selId by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf<Wish?>(null) }
    var delWish by remember { mutableStateOf<Wish?>(null) }

    val goods = d.wishes.filter { it.kind == 0 && it.status == 0 }
    val chests = d.wishes.filter { it.kind == 1 }
    val done = d.wishes.filter { it.kind == 0 && it.status == 1 }.sortedByDescending { it.doneDate }
    val list = when (tab) { 0 -> goods; 1 -> chests; else -> done }
    val sel = list.firstOrNull { it.id == selId } ?: list.firstOrNull()
    val bal = Repo.balance()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SHOP_BG_TOP, SHOP_BG_BOTTOM)))) {
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.horizontalScroll(rememberScrollState()).weight(1f)) {
                    listOf("商品", "宝箱", "已实现").forEachIndexed { i, n ->
                        Box(
                            Modifier.padding(end = 8.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (tab == i) ACCENT else PANEL)
                                .clickable { tab = i; selId = "" }.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                n, fontWeight = FontWeight.Medium,
                                color = if (tab == i) Color(0xFF111111) else TEXT_DIM
                            )
                        }
                    }
                }
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(PANEL)
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(coinIcon(), fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(bal.toString(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(18.dp))

            // 展示柜
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(200.dp).clip(CircleShape).background(Color(0x14FFFFFF)).border(2.dp, Color(0x66FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        sel == null -> Text("空空如也", color = TEXT_DIM)
                        sel.photo.isNotEmpty() -> AsyncImage(File(sel.photo), null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        sel.kind == 1 -> ChestIcon(100.dp)
                        else -> Text(sel.emoji.ifBlank { "🍬" }, fontSize = 88.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 商品名牌：米色底深色字，和深色背景拉开对比
                Box(
                    Modifier.fillMaxWidth(0.78f).clip(RoundedCornerShape(10.dp)).background(PLATE).padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(sel?.name ?: "—", color = Color(0xFF111111), fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                if (sel != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (sel.kind == 1) "任务宝箱" else "标价：", color = TEXT_DIM, fontSize = 14.sp)
                        if (sel.kind == 0) {
                            Text(" " + coinIcon() + " ", fontSize = 15.sp)
                            Text(sel.cost.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            StarRow(sel.stars, 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // 兑换区
            Row(Modifier.fillMaxWidth().height(86.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("我的金币", color = TEXT_DIM, fontSize = 12.sp)
                    Text(coinIcon() + " " + bal, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (sel != null && sel.kind == 0 && sel.status == 0) {
                        Text(
                            if (bal >= sel.cost) "够了，可以兑换" else "还差 " + (sel.cost - bal) + " 枚",
                            color = if (bal >= sel.cost) ACCENT else Color(0xFF9A9A9A), fontSize = 12.sp
                        )
                    }
                }
                when {
                    sel != null && sel.kind == 0 && sel.status == 0 -> RedeemButton(
                        "兑换", bal >= sel.cost,
                        {
                            val err = Repo.redeem(sel.id)
                            if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_SHORT).show() else success = sel
                        },
                        Modifier.magnetic()
                    )
                    sel != null && sel.kind == 1 -> Text(if (sel.status == 0) "完成任务后自动领取" else "已领取 " + sel.doneDate, color = TEXT_DIM, fontSize = 13.sp)
                    sel != null -> Text("已实现 " + sel.doneDate, color = TEXT_DIM, fontSize = 13.sp)
                }
            }

            // 货架
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("货架", color = TEXT_DIM, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAdd = true }) { Text("＋ 上架新商品", color = ACCENT) }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.id }) { w ->
                    val isSel = sel?.id == w.id
                    val tag = when {
                        w.kind == 1 -> if (w.status == 0) "待领取" else "已领取"
                        w.status == 1 -> "已实现"
                        bal >= w.cost -> "可兑换"
                        else -> "差 " + (w.cost - bal)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) Color(0x33FFFFFF) else PANEL)
                            .combinedClickable(onClick = { selId = w.id }, onLongClick = { delWish = w })
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (w.kind == 0 && w.emoji.isNotBlank()) Text(w.emoji + " ", fontSize = 15.sp)
                        Text(w.name, Modifier.weight(1f), color = if (isSel) ACCENT else Color(0xFFE8E8E8), maxLines = 1)
                        if (w.kind == 0) Text(coinIcon() + w.cost + "  ", fontSize = 12.sp, color = TEXT_DIM)
                        Text(tag, color = if (tag == "可兑换" || tag == "待领取") ACCENT else Color(0xFF8A8A8A), fontSize = 12.sp)
                    }
                }
                if (list.isEmpty()) item { Text("这里还没有东西。长按商品可以下架。", color = Color(0xFF8A8A8A), fontSize = 13.sp) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
        success?.let { w -> SuccessPopup(w) { success = null } }
    }
    if (showAdd) WishDialog({ showAdd = false }) { Repo.addWish(it); showAdd = false }
    delWish?.let { w ->
        AlertDialog(
            onDismissRequest = { delWish = null },
            title = { Text("下架「" + w.name + "」？") },
            confirmButton = { TextButton(onClick = { Repo.deleteWish(w.id); delWish = null }) { Text("下架", color = C.red) } },
            dismissButton = { TextButton(onClick = { delWish = null }) { Text("取消") } }
        )
    }
}

/** 兑换成功：大字「兑换成功」，有图片就把图片放中间，只有文字就把文字放大放中间 */
@Composable
private fun SuccessPopup(w: Wish, onClose: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(w.id) { a.snapTo(0f); a.animateTo(1f, tween(450)) }
    Box(
        Modifier.fillMaxSize().background(Color(0xEE000000)).graphicsLayer { alpha = a.value }
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.graphicsLayer { scaleX = 0.9f + 0.1f * a.value; scaleY = 0.9f + 0.1f * a.value },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("兑换成功", color = ACCENT, fontSize = 38.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Text("✦  ✦ ✦", color = ACCENT, fontSize = 16.sp)
            Spacer(Modifier.height(18.dp))
            if (w.photo.isNotEmpty()) {
                Box(Modifier.size(230.dp).clip(CircleShape).border(2.dp, Color(0x88FFFFFF), CircleShape)) {
                    AsyncImage(File(w.photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.height(12.dp))
                Text(w.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            } else {
                Text(w.emoji.ifBlank { "🍬" }, fontSize = 72.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    w.name, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 30.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A)).padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text("成交价：" + coinIcon() + " " + w.cost, color = Color.White, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(24.dp))
            Text("点任意处关闭", color = Color(0xFF8A8A8A), fontSize = 12.sp)
        }
    }
}

@Composable
fun WishDialog(onDismiss: () -> Unit, onSave: (Wish) -> Unit) {
    val ctx = LocalContext.current
    val p = Repo.data.prefs
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🍬") }
    var stars by remember { mutableIntStateOf(3) }
    var cost by remember { mutableStateOf(p.wishStarFlowers.getOrElse(2) { 9 }.toString()) }
    var manual by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf("") }
    var camFile by remember { mutableStateOf<File?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) copyUri(ctx, uri, File(ctx.filesDir, "wishes"), "wish_")?.let { photo = it }
    }
    // 拍照用完整分辨率的文件（以前用的是系统给的小缩略图，所以显示出来很糊）
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = camFile
        if (ok && f != null) photo = f.path else f?.delete()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上架新商品") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { ReqLabel("想要什么（零食、看场电影、睡个懒觉都行）") }, singleLine = true)
                Text("想要程度")
                StarRow(stars, 32.sp) {
                    stars = it
                    if (!manual) cost = p.wishStarFlowers.getOrElse(it - 1) { it * 3 }.toString()
                }
                OutlinedTextField(
                    cost, { cost = it.filter { c -> c.isDigit() }.take(4); manual = true },
                    label = { ReqLabel("标价（金币）") }, singleLine = true
                )
                Text("圆圈里的图标（没传图片时显示）")
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    EMOJI_CHOICES.forEach { e ->
                        Box(
                            Modifier.padding(end = 4.dp).size(40.dp).clip(CircleShape)
                                .background(if (emoji == e) Color(0x33000000) else Color.Transparent)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
                OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("或者自己输入一个表情") }, singleLine = true)
                Row {
                    RippleButton("📷 拍照", {
                        val dir = File(ctx.filesDir, "wishes")
                        dir.mkdirs()
                        val f = File(dir, "wish_" + uid() + ".jpg")
                        camFile = f
                        cam.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".fp", f))
                    })
                    Spacer(Modifier.width(8.dp))
                    RippleButton("🖼 相册", { pick.launch("image/*") })
                }
                if (photo.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(File(photo), null, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        TextButton(onClick = { photo = "" }) { Text("移除图片") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onSave(Wish(name = name.trim(), cost = cost.toIntOrNull() ?: 9, stars = stars, photo = photo, emoji = emoji.ifBlank { "🍬" }))
                }
            }) { Text("上架", color = C.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
