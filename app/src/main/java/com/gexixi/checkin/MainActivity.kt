package com.gexixi.checkin

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        handle(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** 桌面任务小组件点圆圈：打开 App 并弹出这个任务的结算窗 */
    private fun handle(i: Intent?) {
        val id = i?.getStringExtra("settle") ?: return
        i.removeExtra("settle")
        if (id.isNotEmpty()) Repo.requestSettle(id)
    }

    override fun onResume() {
        super.onResume()
        Widgets.refreshAll(this)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val pager = rememberPagerState(initialPage = 1, pageCount = { 3 })      // 0 人物（在规划右滑出来）  1 规划（默认）  2 记录（在规划左滑出来）
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(false) }
    var cel by remember { mutableStateOf<Cel?>(null) }
    val host = remember { BentoHost() }
    val pointer = remember { mutableStateOf<Offset?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        Tracker.ensure(ctx)
    }
    BackHandler(enabled = settings) { settings = false }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.accent, onPrimary = Color.White, background = C.paper, surface = C.paper,
            secondaryContainer = Color(0xFFE6E6E8), onSecondaryContainer = C.ink
        )
    ) {
        CompositionLocalProvider(LocalBento provides host, LocalPointer provides pointer) {
            Box(Modifier.fillMaxSize().background(C.paper).trackPointer(pointer)) {
                HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !settings) { page ->
                    when (page) {
                        0 -> PeoplePage()
                        1 -> PlanPage(onSettings = { settings = true })
                        else -> RecordPage(onSettings = { settings = true })
                    }
                }
                if (settings) SettingsPage(onBack = { settings = false })
                // 悬浮的深色玻璃底栏：盖在内容上面，展开的卡片会再盖住它
                GlassBar(
                    pager.currentPage + pager.currentPageOffsetFraction - 1f, !settings,
                    { i ->
                        settings = false
                        scope.launch { pager.animateScrollToPage(i + 1, 0f, spring(dampingRatio = 0.72f, stiffness = 260f)) }
                    },
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp)
                )
                BentoOverlay(host)
            }
            val sid = Repo.settleId
            if (sid.isNotEmpty()) {
                SettleDialog(sid) { c ->
                    Repo.settleId = ""
                    if (c != null) cel = c
                }
            }
            cel?.let { c -> CelebrateDialog(c) { cel = null } }
        }
    }
}

/**
 * 悬浮白色「液态玻璃」底栏（仿苹果）：半透明浅白 + 顶部高光 + 一圈亮边 + 极淡的外轮廓和柔影。
 * 底栏两个入口：规划 / 记录；人物页在规划右滑出来，不占底栏。
 * 选中的浅灰块用弹簧滑过去（带一点过冲和拉伸），也会跟着左右滑页实时移动；图标和文字挨得很近。
 * pos：0 = 规划，1 = 记录，负数 = 在人物页（浅灰块淡出）。
 */
@Composable
fun GlassBar(pos: Float, show: Boolean, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val itemW = 88.dp
    val idx by animateFloatAsState(pos.coerceIn(0f, 1f), springF(380f, 20f), label = "barIdx")
    val vis by animateFloatAsState(if (show) (pos + 1f).coerceIn(0f, 1f) else 0f, tween(200), label = "barVis")
    val mid = (4f * idx * (1f - idx)).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier.drawBehind {
            // 柔和的外阴影：几层越来越大、越来越淡的圆角矩形
            val cr = CornerRadius(32.dp.toPx())
            listOf(2.dp to 0.05f, 5.dp to 0.035f, 9.dp to 0.02f).forEach { (g, a) ->
                val gp = g.toPx()
                drawRoundRect(
                    Color.Black.copy(alpha = a), Offset(-gp, -gp + 3.dp.toPx()),
                    Size(this.size.width + gp * 2, this.size.height + gp * 2), CornerRadius(cr.x + gp)
                )
            }
        }
    ) {
        Box(
            Modifier
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xE6FFFFFF), Color(0xB3EEF2F8))))
                .border(1.dp, Color(0x14000000), shape)
                .padding(1.dp)
                .border(1.dp, Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0x66FFFFFF), Color(0xE6FFFFFF))), RoundedCornerShape(31.dp))
                .padding(4.dp)
        ) {
            // 顶部高光
            Box(
                Modifier.align(Alignment.TopCenter).padding(horizontal = 14.dp).fillMaxWidth().height(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color.Transparent)))
            )
            Box(
                Modifier
                    .offset { IntOffset((idx * itemW.toPx()).roundToInt(), 0) }
                    .size(itemW, 46.dp)
                    .graphicsLayer { alpha = vis; scaleX = 1f + 0.22f * mid }
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0x14000000))
                    .border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(26.dp))
            )
            Row {
                listOf("规划", "记录").forEachIndexed { i, n ->
                    Column(
                        Modifier.size(itemW, 46.dp).bounceClick { onPick(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BarIcon(if (i == 0) 1 else 0)
                        Text(n, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    }
                }
            }
        }
    }
}

/** 实心线条小图标：0 = 钟（记录），1 = 靶心（规划） */
@Composable
private fun BarIcon(kind: Int) {
    Canvas(Modifier.size(20.dp)) {
        val w = this.size.width
        if (kind == 0) {
            drawCircle(C.ink, w / 2f)
            drawLine(Color.White, Offset(w / 2f, w / 2f), Offset(w / 2f, w * 0.22f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(w / 2f, w / 2f), Offset(w * 0.72f, w * 0.62f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
        } else {
            drawCircle(C.ink, w / 2f - w * 0.06f, style = Stroke(w * 0.12f))
            drawCircle(C.ink, w * 0.2f)
        }
    }
}
