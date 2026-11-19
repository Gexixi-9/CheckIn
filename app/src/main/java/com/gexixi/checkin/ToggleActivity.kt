package com.gexixi.checkin

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * 桌面标签小组件点击的透明中转页：
 * 先开始/结束计时；如果这是「结束」，并且这个标签绑了 Notion（播客/购物/阅读/小说）或 repov，直接跳过去写感想。
 * （广播接收器在后台没法打开别的 App，所以要经过一个有界面的 Activity。）
 */
class ToggleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        val id = intent.getStringExtra("tag")
        val tag = id?.let { Repo.tag(it) }
        if (id != null && tag != null) {
            val ended = Repo.toggleTag(id)
            if (ended != null) {
                val text = tag.name + " " + fmtMin((ended.end ?: ended.start) - ended.start)
                val nk = notionKeyFor(id)
                val link = if (nk.isNotEmpty()) Notion.link(nk) else null
                if (link != null) Notion.open(this, link.url, text)
                else if (tag.repov) Notion.openRepov(this, text)
            }
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
