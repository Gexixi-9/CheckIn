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
import androidx.compose.foundation.layout.Box
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

@Composable
fun App() {
    val ctx = LocalContext.current
    var screen by remember { mutableIntStateOf(1) }      // 0 记录  1 规划（默认）  2 设置
    var lastTab by remember { mutableIntStateOf(1) }
    var cel by remember { mutableStateOf<Cel?>(null) }
    val host = remember { BentoHost() }
    val pointer = remember { mutableStateOf<Offset?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        Tracker.ensure(ctx)
    }
    BackHandler(enabled = screen == 2) { screen = lastTab }
    MaterialTheme(colorScheme = lightColorScheme(primary = C.red, background = C.paper, surface = C.paper)) {
        CompositionLocalProvider(LocalBento provides host, LocalPointer provides pointer) {
            Box(Modifier.fillMaxSize().background(C.paper).trackPointer(pointer)) {
                Box(Modifier.fillMaxSize()) {
                    when (screen) {
                        0 -> RecordPage(onSettings = { lastTab = 0; screen = 2 })
                        1 -> PlanPage(onSettings = { lastTab = 1; screen = 2 })
                        else -> SettingsPage(onBack = { screen = lastTab })
                    }
                }
                // 悬浮的玻璃底栏：盖在内容上面，展开的卡片会再盖住它
                GlassBar(
                    screen, { screen = it },
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
 * 悬浮玻璃底栏（参照小米文件管理器）：半透明圆角胶囊，里面两个入口，
 * 选中的那块浅灰底会用弹簧滑过去（带一点过冲和拉伸），按下时图标整体回弹。
 */
@Composable
fun GlassBar(screen: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val itemW = 96.dp
    val idx by animateFloatAsState(if (screen in 0..1) screen.toFloat() else 0f, springF(380f, 20f), label = "barIdx")
    val show by animateFloatAsState(if (screen in 0..1) 1f else 0f, tween(220), label = "barShow")
    val mid = (4f * idx * (1f - idx)).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(34.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xEBFFFFFF), Color(0xB8FFFFFF))))
            .border(1.dp, Color(0xCCFFFFFF), shape)
            .padding(5.dp)
    ) {
        Box(
            Modifier
                .offset { IntOffset((idx * itemW.toPx()).roundToInt(), 0) }
                .size(itemW, 54.dp)
                .graphicsLayer { alpha = show; scaleX = 1f + 0.22f * mid }
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0x1F000000))
        )
        Row {
            listOf("记录", "规划").forEachIndexed { i, n ->
                Column(
                    Modifier.size(itemW, 54.dp).bounceClick { onPick(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BarIcon(i)
                    Spacer(Modifier.height(3.dp))
                    Text(n, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.ink)
                }
            }
        }
    }
}

/** 实心线条小图标：0 = 钟（记录），1 = 靶心（规划） */
@Composable
private fun BarIcon(kind: Int) {
    Canvas(Modifier.size(22.dp)) {
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
