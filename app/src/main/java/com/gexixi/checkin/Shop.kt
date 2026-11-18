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
import androidx.compose.foundation.gestures.detectTapGestures
import coil.compose.AsyncImage
import java.io.File

private val GOLD = Color(0xFFE8B84A)
private val PANEL = Color(0x33FFFFFF)

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

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF26292D), Color(0xFF0F1113))))
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.horizontalScroll(rememberScrollState()).weight(1f)) {
                    listOf("商品", "宝箱", "已实现").forEachIndexed { i, n ->
                        Box(
                            Modifier.padding(end = 8.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (tab == i) Color(0x55E8B84A) else PANEL)
                                .clickable { tab = i; selId = "" }.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) { Text(n, color = if (tab == i) GOLD else Color(0xFFCCCCCC), fontWeight = FontWeight.Medium) }
                    }
                }
                Row(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF5A1E22)).border(1.dp, Color(0xFF9B3A40), RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(coinIcon(), fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(bal.toString(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))

            // 展示柜
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(170.dp).clip(CircleShape).background(Color(0x22FFFFFF)).border(2.dp, Color(0x66E8B84A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        sel == null -> Text("空空如也", color = Color(0xFF999999))
                        sel.photo.isNotEmpty() -> AsyncImage(File(sel.photo), null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        sel.kind == 1 -> ChestIcon(90.dp)
                        else -> Text("🍬", fontSize = 72.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.8f).clip(RoundedCornerShape(2.dp)).background(Color(0x66000000)).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Text(sel?.name ?: "—", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                if (sel != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (sel.kind == 1) "任务宝箱" else "标价：", color = Color(0xFFCCCCCC), fontSize = 14.sp)
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
                    Text("我的金币", color = Color(0xFFCCCCCC), fontSize = 12.sp)
                    Text(coinIcon() + " " + bal, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (sel != null && sel.kind == 0 && sel.status == 0) {
                        Text(
                            if (bal >= sel.cost) "够了，可以兑换" else "还差 " + (sel.cost - bal) + " 枚",
                            color = if (bal >= sel.cost) GOLD else Color(0xFFE07070), fontSize = 12.sp
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
                    sel != null && sel.kind == 1 -> Text(if (sel.status == 0) "完成任务后自动领取" else "已领取 " + sel.doneDate, color = Color(0xFFCCCCCC), fontSize = 13.sp)
                    sel != null -> Text("已实现 " + sel.doneDate, color = Color(0xFFCCCCCC), fontSize = 13.sp)
                }
            }

            // 商品列表
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("货架", color = Color(0xFFCCCCCC), fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAdd = true }) { Text("＋ 上架新商品", color = GOLD) }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list, key = { it.id }) { w ->
                    val isSel = sel?.id == w.id
                    val tag = when {
                        w.kind == 1 -> if (w.status == 0) "待领取" else "已领取"
                        w.status == 1 -> "已实现"
                        bal >= w.cost -> "可兑换"
                        else -> "差 " + (w.cost - bal)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                            .background(if (isSel) Color(0x44E8B84A) else Color(0x33000000))
                            .combinedClickable(onClick = { selId = w.id }, onLongClick = { delWish = w })
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(w.name, Modifier.weight(1f), color = if (isSel) GOLD else Color(0xFFDDDDDD), maxLines = 1)
                        if (w.kind == 0) Text(coinIcon() + w.cost + "  ", fontSize = 12.sp, color = Color(0xFFBBBBBB))
                        Text(tag, color = if (tag == "可兑换" || tag == "待领取") GOLD else Color(0xFF999999), fontSize = 12.sp)
                    }
                }
                if (list.isEmpty()) item { Text("这里还没有东西。长按商品可以下架。", color = Color(0xFF888888), fontSize = 13.sp) }
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
        Modifier.fillMaxSize().background(Color(0xCC000000)).graphicsLayer { alpha = a.value }
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.graphicsLayer { scaleX = 0.9f + 0.1f * a.value; scaleY = 0.9f + 0.1f * a.value },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("兑换成功", color = GOLD, fontSize = 38.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Text("✦  ✦ ✦", color = GOLD, fontSize = 16.sp)
            Spacer(Modifier.height(18.dp))
            if (w.photo.isNotEmpty()) {
                Box(Modifier.size(210.dp).clip(CircleShape).border(2.dp, Color(0x88E8B84A), CircleShape)) {
                    AsyncImage(File(w.photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.height(12.dp))
                Text(w.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            } else {
                Text(
                    w.name, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 30.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.clip(RoundedCornerShape(2.dp)).background(Color(0xFF8A3A3F)).padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text("成交价：" + coinIcon() + " " + w.cost, color = Color.White, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(24.dp))
            Text("点任意处关闭", color = Color(0xFF888888), fontSize = 12.sp)
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
        title = { Text("上架新商品") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row {
                    RippleButton("📷 拍照", { cam.launch(null) })
                    Spacer(Modifier.width(8.dp))
                    RippleButton("🖼 相册", { pick.launch("image/*") })
                }
                if (photo.isNotEmpty()) Text("图片已选 ✓（只存在手机本地）", fontSize = 12.sp, color = C.gray)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(Wish(name = name.trim(), cost = cost.toIntOrNull() ?: 9, stars = stars, photo = photo))
            }) { Text("上架", color = C.red) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
