package com.gexixi.checkin

import android.content.Context
import android.media.MediaPlayer

object Media {
    // key 与 assets 里的文件名前缀 / popup gif 名一致
    val chars = listOf("ghost" to "Ghost", "keegan" to "Keegan", "krueger" to "Krueger", "koenig" to "König")
    private var mp: MediaPlayer? = null

    fun stop() {
        try { mp?.release() } catch (_: Exception) {}
        mp = null
    }

    fun playAsset(ctx: Context, path: String) {
        stop()
        try {
            val fd = ctx.assets.openFd(path)
            mp = MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                fd.close()
                prepare()
                start()
            }
        } catch (_: Exception) {}
    }

    fun playFile(path: String) {
        stop()
        try {
            mp = MediaPlayer().apply { setDataSource(path); prepare(); start() }
        } catch (_: Exception) {}
    }

    /** 完成任务时：选角色（指定或随机）→ 随机播放该角色一条鼓励音频。返回角色key（用来选弹窗GIF）。 */
    fun celebrate(ctx: Context, pref: String): String {
        val key = if (chars.any { it.first == pref }) pref else chars.random().first
        val files = ctx.assets.list("audio")?.filter { it.startsWith(key + "_") }.orEmpty()
        if (files.isNotEmpty()) playAsset(ctx, "audio/" + files.random())
        return key
    }
}
