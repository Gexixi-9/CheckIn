package com.gexixi.checkin

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.util.Locale

data class Cheer(val ch: String, val text: String)

object Media {
    // key 与 assets 里的文件名前缀、popup GIF 名一致
    val chars = listOf("ghost" to "Ghost", "keegan" to "Keegan", "krueger" to "Krueger", "koenig" to "König")
    private var mp: MediaPlayer? = null
    private var texts: Map<String, String>? = null

    private fun loadTexts(ctx: Context): Map<String, String> {
        texts?.let { return it }
        val m: Map<String, String> = try {
            ctx.assets.open("audio_text.json").bufferedReader().use {
                val type = object : TypeToken<Map<String, String>>() {}.type
                Gson().fromJson<Map<String, String>>(it, type)
            }
        } catch (e: Exception) {
            emptyMap()
        }
        texts = m
        return m
    }

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

    /** 人物界面用：随机挑这个角色的一句台词（只取文字，不播放） */
    fun sampleLine(ctx: Context, key: String): String {
        val f = ctx.assets.list("audio")?.filter { it.startsWith(key + "_") }.orEmpty().randomOrNull() ?: return ""
        return loadTexts(ctx)[f].orEmpty()
    }

    /** 选角色（指定或随机）→ 随机播一条该角色的鼓励音频，返回角色和台词（台词给弹窗的气泡用）。 */
    fun cheer(ctx: Context, pref: String): Cheer {
        val key = if (chars.any { it.first == pref }) pref else chars.random().first
        val files = ctx.assets.list("audio")?.filter { it.startsWith(key + "_") }.orEmpty()
        if (files.isEmpty()) return Cheer(key, "")
        val f = files.random()
        playAsset(ctx, "audio/" + f)
        Repo.bumpPlay(key)
        return Cheer(key, loadTexts(ctx)[f].orEmpty())
    }
}

/** 任务发放时的语音播报：硅基流动提前生成并缓存，没网/没密钥退回手机自带朗读 */
object Voice {
    private var sys: TextToSpeech? = null
    private var sysReady = false

    fun textFor(t: Task, p: Prefs): String {
        val first = t.subs.firstOrNull { !it.done }?.text ?: "开始"
        return p.ttsTemplate
            .replace("{任务}", t.title)
            .replace("{目标}", t.goal.ifBlank { t.title })
            .replace("{第一步}", first)
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun cacheFile(ctx: Context, text: String, p: Prefs): File =
        File(File(ctx.filesDir, "tts"), md5(text + "|" + p.ttsModel + "|" + p.ttsVoice) + ".mp3")

    fun speakSystem(ctx: Context, text: String) {
        val cur = sys
        if (cur == null) {
            sys = TextToSpeech(ctx.applicationContext) { st ->
                sysReady = st == TextToSpeech.SUCCESS
                if (sysReady) {
                    sys?.language = Locale.CHINESE
                    sys?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dx")
                }
            }
        } else if (sysReady) {
            cur.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dx")
        }
    }

    /** 保存/修改任务时提前生成；内容没变就不重复花钱 */
    fun warm(ctx: Context, t: Task) {
        val p = Repo.data.prefs
        if (!p.announceOn || p.ttsKey.isBlank()) return
        val text = textFor(t, p)
        val f = cacheFile(ctx, text, p)
        if (f.exists()) return
        CoroutineScope(Dispatchers.IO).launch {
            try { Ai.tts(p, text, f) } catch (_: Exception) {}
        }
    }

    /** 一个任务成为「当前任务」时播报 */
    fun announce(ctx: Context, t: Task) {
        val p = Repo.data.prefs
        if (!p.announceOn) return
        val text = textFor(t, p)
        val f = cacheFile(ctx, text, p)
        CoroutineScope(Dispatchers.Main).launch {
            if (f.exists()) { Media.playFile(f.path); return@launch }
            if (p.ttsKey.isNotBlank()) {
                val ok = try { Ai.tts(p, text, f); true } catch (e: Exception) { false }
                if (ok && f.exists()) { Media.playFile(f.path); return@launch }
            }
            speakSystem(ctx, text)
        }
    }
}
