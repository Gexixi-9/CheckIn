package com.gexixi.checkin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

// 只给 Android Studio 的预览窗格用，不影响 App 运行。
// 打开本文件或 TodayScreen.kt，右上角切到 Split，就能一边改一边看。
// 想改预览里的示例内容，改下面的 sample()。

private fun sample(): Data {
    val now = System.currentTimeMillis()
    return Data(
        sessions = listOf(
            Session("s1", "k_review", now - 25 * 60000L),
            Session("s2", "e_podcast", now - 3 * 3600000L, now - 2 * 3600000L),
            Session("s3", "a_douyin", now - 150 * 60000L, now - 128 * 60000L, auto = true),
            Session("s4", "l_bath", now - 140 * 60000L, now - 120 * 60000L)
        ),
        tasks = listOf(
            Task(
                id = "a", title = "写论文第一章", listId = "l4", goal = "写完引言初稿", condition = "写满1500字",
                subs = listOf(Sub("打开文档，写下第一句", true, 1), Sub("列出三个小标题", false, 2), Sub("每段补一个例子", false, 2)),
                stars = 3, flowers = 3, date = "2026-11-15", time = "14:00", reward = "一块蛋糕"
            ),
            Task(id = "b", title = "背20个单词", listId = "l1", stars = 1, flowers = 1, date = "2026-11-15", time = "20:00",
                subs = listOf(Sub("打开单词本"))),
            Task(id = "c", title = "整理书桌", listId = "l2", stars = 2, flowers = 2)
        ),
        wishes = listOf(
            Wish(name = "一包薯片", cost = 3, stars = 1),
            Wish(name = "看一场电影", cost = 12, stars = 4),
            Wish(name = "一块蛋糕", cost = 0, kind = 1, fromTask = "a")
        ),
        earned = 9, spent = 0, currentId = "a"
    )
}

@Composable
private fun Wrap(content: @Composable () -> Unit) {
    remember { Repo.preview(sample()); 0 }
    MaterialTheme(colorScheme = lightColorScheme(primary = C.red)) {
        Surface(Modifier.fillMaxSize()) { content() }
    }
}

@Preview(name = "今天", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun PreviewToday() = Wrap { TodayScreen { } }

@Preview(name = "记录", showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun PreviewRecords() = Wrap { RecordsScreen() }

@Preview(name = "日历", showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun PreviewCalendar() = Wrap { CalendarScreen() }

@Preview(name = "心愿", showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun PreviewWishes() = Wrap { WishScreen() }

@Preview(name = "设置", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun PreviewSettings() = Wrap { SettingsScreen() }

@Preview(name = "完成弹窗", showBackground = true, widthDp = 360, heightDp = 700)
@Composable
private fun PreviewCelebrate() = Wrap {
    CelebrateDialog(Cel(3, "ghost", "Good. That's my girl.", "一块蛋糕", "现在可兑换：一包薯片", 4, 5)) { }
}
