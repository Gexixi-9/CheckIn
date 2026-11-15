package com.gexixi.checkin

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        Widgets.refreshAll(this)
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var cel by remember { mutableStateOf<Cel?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        Tracker.ensure(ctx)
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = C.red, background = C.paper, surface = C.paper)) {
        Scaffold(
            containerColor = C.paper,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf("🎯" to "今天", "⏱️" to "记录", "🗓️" to "日历", "🎁" to "心愿", "⚙️" to "设置").forEachIndexed { i, (e, n) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(e) }, label = { Text(n) })
                    }
                }
            }
        ) { pad ->
            androidx.compose.foundation.layout.Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> TodayScreen { cel = it }
                    1 -> RecordsScreen()
                    2 -> CalendarScreen()
                    3 -> WishScreen()
                    else -> SettingsScreen()
                }
            }
        }
        cel?.let { c -> CelebrateDialog(c) { cel = null } }
    }
}
