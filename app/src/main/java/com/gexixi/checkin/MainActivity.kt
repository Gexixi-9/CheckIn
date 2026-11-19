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
import androidx.compose.ui.geometry.Offset
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
    val pager = rememberPagerState(initialPage = 1, pageCount = { 3 })      // 0 记录  1 规划（默认）  2 人物（在规划右边）
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
                        0 -> RecordPage(onSettings = { settings = true })
                        1 -> PlanPage(onSettings = { settings = true })
                        else -> PeoplePage()
                    }
                }
                if (settings) SettingsPage(onBack = { settings = false })
                // 悬浮的深色玻璃底栏：盖在内容上面，展开的卡片会再盖住它
                GlassBar(
                    pager.currentPage + pager.currentPageOffsetFraction, !settings,
                    { i ->
                        settings = false
                        scope.launch { pager.animateScrollToPage(i, 0f, spring(dampingRatio = 0.72f, stiffness = 260f)) }
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
 * 悬浮深色玻璃底栏：半透明深灰胶囊，里面两个入口（记录 / 规划），人物页在规划右边、不占底栏。
 * 选中的浅灰块用弹簧滑过去（带一点过冲和拉伸），也会跟着左右滑页面实时移动；图标和文字挨得很近。
 */
@Composable
fun GlassBar(pos: Float, show: Boolean, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val itemW = 92.dp
    val idx by animateFloatAsState(pos.coerceIn(0f, 1f), springF(380f, 20f), label = "barIdx")
    val vis by animateFloatAsState(if (show) (2f - pos).coerceIn(0f, 1f) else 0f, tween(200), label = "barVis")
    val mid = (4f * idx * (1f - idx)).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xD92E2E33), Color(0xBF1F1F23))))
            .border(1.dp, Color(0x40FFFFFF), shape)
            .padding(5.dp)
    ) {
        Box(
            Modifier
                .offset { IntOffset((idx * itemW.toPx()).roundToInt(), 0) }
                .size(itemW, 48.dp)
                .graphicsLayer { alpha = vis; scaleX = 1f + 0.22f * mid }
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0x40FFFFFF))
        )
        Row {
            listOf("记录", "规划").forEachIndexed { i, n ->
                Column(
                    Modifier.size(itemW, 48.dp).bounceClick { onPick(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BarIcon(i)
                    Spacer(Modifier.height(2.dp))
                    Text(n, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
        val dark = Color(0xFF2E2E33)
        if (kind == 0) {
            drawCircle(Color.White, w / 2f)
            drawLine(dark, Offset(w / 2f, w / 2f), Offset(w / 2f, w * 0.22f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
            drawLine(dark, Offset(w / 2f, w / 2f), Offset(w * 0.72f, w * 0.62f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
        } else {
            drawCircle(Color.White, w / 2f - w * 0.06f, style = Stroke(w * 0.12f))
            drawCircle(Color.White, w * 0.2f)
        }
    }
}
