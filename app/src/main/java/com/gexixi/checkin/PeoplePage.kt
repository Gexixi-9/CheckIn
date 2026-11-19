@file:OptIn(ExperimentalFoundationApi::class)

package com.gexixi.checkin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/**
 * 人物界面（鼓励音频里的四个人）：在规划页再往右滑出来，不占底栏。
 * 上下滑切换角色；点大卡片播一条这个人的鼓励语音；♥ 是播放次数；右上角可以换照片。
 */
@Composable
fun PeoplePage() {
    val ctx = LocalContext.current
    val d = Repo.data
    val chars = Media.chars
    val pager = rememberPagerState(pageCount = { chars.size })
    val loader = rememberGifLoader()
    var line by remember { mutableStateOf("") }
    val cur = chars[pager.currentPage]
    LaunchedEffect(pager.currentPage) { line = Media.sampleLine(ctx, cur.first) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) copyUri(ctx, uri, File(ctx.filesDir, "people"), "p_")?.let { Repo.setCharPhoto(cur.first, it) }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF2F2F5)).padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("TA", Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.ink)
            if (d.charPhotos.containsKey(cur.first)) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color.White).bounceClick { Repo.clearCharPhoto(cur.first) },
                    contentAlignment = Alignment.Center
                ) { Text("↺", fontSize = 20.sp, color = C.ink) }
                Spacer(Modifier.width(10.dp))
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(C.ink).bounceClick { pick.launch("image/*") },
                contentAlignment = Alignment.Center
            ) { Text("＋", fontSize = 22.sp, color = Color.White) }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            VerticalPager(
                pager, Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 104.dp), pageSpacing = 12.dp
            ) { i ->
                val key = chars[i].first
                val photo = d.charPhotos[key]
                val plays = d.charPlays[key] ?: 0
                val chosen = d.prefs.character == key
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(36.dp)).background(Color(0xFF1A1A1A))
                        .clickable { line = Media.cheer(ctx, key).text }
                ) {
                    AsyncImage(
                        model = if (photo != null) File(photo) else "file:///android_asset/popup/" + key + ".gif",
                        contentDescription = null, imageLoader = loader,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier.fillMaxWidth().height(220.dp).align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                        Text(chars[i].second, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        if (i == pager.currentPage && line.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(line, fontSize = 13.sp, color = Color(0xCCFFFFFF), maxLines = 3)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("♥", fontSize = 13.sp, color = C.ink)
                                Spacer(Modifier.width(6.dp))
                                Text(plays.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.ink)
                            }
                            Box(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(if (chosen) Color.White else Color(0x44FFFFFF))
                                    .bounceClick { Repo.setPrefs { it.copy(character = if (chosen) "random" else key) } }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    if (chosen) "✓ 我的鼓励角色" else "选 TA 来鼓励我", fontSize = 13.sp,
                                    color = if (chosen) C.ink else Color.White, fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            // 右侧小圆点：当前是第几个角色
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chars.indices.forEach { i ->
                    Box(Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape).background(if (i == pager.currentPage) C.ink else Color(0x55000000)))
                }
            }
        }
    }
}
