package com.gexixi.checkin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Notion 联动：只做「一键跳转」，需要的话先把「标签名 + 用时」复制好，进去直接粘贴写感想。 */
object Notion {
    fun link(key: String): NotionLink? = Repo.data.prefs.notion.firstOrNull { it.key == key }

    fun open(ctx: Context, url: String, clip: String = "") {
        if (url.isBlank()) { Toast.makeText(ctx, "还没填 Notion 链接，去设置里填", Toast.LENGTH_SHORT).show(); return }
        if (clip.isNotBlank()) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("dangxia", clip))
        }
        val uri = Uri.parse(url)
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("notion.id"))
        } catch (e: Exception) {
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            catch (e2: Exception) { Toast.makeText(ctx, "打不开 Notion", Toast.LENGTH_SHORT).show() }
        }
    }
}
