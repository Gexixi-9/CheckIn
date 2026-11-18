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
import androidx.compose.foundation.background
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
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (screen) {
                            0 -> RecordPage(onSettings = { lastTab = 0; screen = 2 })
                            1 -> PlanPage(onSettings = { lastTab = 1; screen = 2 })
                            else -> SettingsPage(onBack = { screen = lastTab })
                        }
                    }
                    BottomBar(screen) { screen = it }
                }
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

/** 底栏只有两个：记录 / 规划。按下缩小并减弱阴影，松手弹簧回弹 */
@Composable
fun BottomBar(screen: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf("⏱️" to "记录", "🎯" to "规划").forEachIndexed { i, (e, n) ->
            val sel = screen == i
            Column(
                Modifier
                    .bounceClick(elevation = 6.dp, shape = RoundedCornerShape(20.dp)) { onPick(i) }
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (sel) Color(0xFFE6E0F5) else Color.Transparent)
                    .padding(horizontal = 40.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(e, fontSize = 22.sp)
                Text(n, fontSize = 12.sp, color = if (sel) C.ink else C.gray)
            }
        }
    }
}
