package com.gexixi.checkin

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import java.io.File
import java.time.LocalDate
import java.util.UUID

fun uid(): String = UUID.randomUUID().toString().take(8)

// ---------- 生活记录（标签计时）：与任务完全分开 ----------
data class Tag(val id: String, val name: String, val emoji: String, val color: Long)
data class Session(val id: String, val tagId: String, val start: Long, val end: Long? = null)

// ---------- 任务 ----------
data class Sub(val text: String, val done: Boolean = false)
data class Task(
    val id: String = uid(),
    val title: String,
    val goal: String = "",
    val condition: String = "",
    val subs: List<Sub> = emptyList(),
    val stars: Int = 1,        // 难度（黄色五角星）
    val flowers: Int = 1,      // 完成奖励小红花
    val date: String = "",     // yyyy-MM-dd，只用于排序/显示，不弹通知
    val time: String = "",     // HH:mm
    val repeat: Int = 0,       // 0不重复 1每天 2每周 3每月
    val repeatEnd: String = "",
    val done: Boolean = false,
    val doneAt: Long = 0
)

// ---------- 心愿 ----------
data class Wish(
    val id: String = uid(),
    val name: String,
    val cost: Int,
    val photo: String = "",
    val ai: Boolean = false,   // true = AI建任务时附带的奖励心愿，与自建心愿分开显示
    val redeemed: Boolean = false
)

data class Settings(
    val character: String = "random",
    // AI 拆分任务（DeepSeek）
    val dsKey: String = "",
    val dsModel: String = "deepseek-chat",
    // 任务语音播报（硅基流动）——与上面完全独立
    val ttsOn: Boolean = false,
    val ttsKey: String = "",
    val ttsModel: String = "FunAudioLLM/CosyVoice2-0.5B",
    val ttsVoice: String = "FunAudioLLM/CosyVoice2-0.5B:alex"
)

fun defaultTags() = listOf(
    Tag("t1", "学习", "📚", 0xFF4C8DFF),
    Tag("t2", "阅读", "📖", 0xFFB06BFF),
    Tag("t3", "播客", "🎧", 0xFFFF8A3D),
    Tag("t4", "运动", "🏃", 0xFF2FBF71),
    Tag("t5", "洗澡", "🛁", 0xFF35C4D6),
    Tag("t6", "手机", "📱", 0xFFE5484D),
    Tag("t7", "家务", "🧹", 0xFFE0B100),
    Tag("t8", "睡觉", "😴", 0xFF8E8E93)
)

data class Data(
    val tags: List<Tag> = defaultTags(),
    val sessions: List<Session> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val wishes: List<Wish> = emptyList(),
    val flowers: Int = 0,
    val currentId: String = "",
    val settings: Settings = Settings()
)

object Repo {
    lateinit var app: Context
    private lateinit var file: File
    private var inited = false
    var data by mutableStateOf(Data())
        private set

    fun init(ctx: Context) {
        if (inited) return
        app = ctx.applicationContext
        file = File(app.filesDir, "data.json")
        data = try {
            Gson().fromJson(file.readText(), Data::class.java) ?: Data()
        } catch (e: Exception) {
            Data()
        }
        inited = true
    }

    fun update(f: (Data) -> Data) {
        data = f(data)
        try { file.writeText(Gson().toJson(data)) } catch (_: Exception) {}
    }

    // ----- 标签计时（允许多个标签同时进行、时间重叠） -----
    fun toggleTag(id: String) {
        val now = System.currentTimeMillis()
        update { d ->
            val run = d.sessions.firstOrNull { it.tagId == id && it.end == null }
            if (run != null) d.copy(sessions = d.sessions.map { if (it.id == run.id) it.copy(end = now) else it })
            else d.copy(sessions = d.sessions + Session(uid(), id, now))
        }
        TagWidget.refresh(app)
    }

    fun addTag(t: Tag) { update { it.copy(tags = it.tags + t) }; TagWidget.refresh(app) }
    fun deleteTag(id: String) {
        update { d -> d.copy(tags = d.tags.filter { it.id != id }, sessions = d.sessions.filter { it.tagId != id }) }
        TagWidget.refresh(app)
    }

    // ----- 任务 -----
    fun addTask(t: Task) = update { it.copy(tasks = it.tasks + t) }
    fun deleteTask(id: String) = update { it.copy(tasks = it.tasks.filter { t -> t.id != id }) }
    fun setCurrent(id: String) = update { it.copy(currentId = id) }
    fun toggleSub(taskId: String, idx: Int) = update { d ->
        d.copy(tasks = d.tasks.map { t ->
            if (t.id != taskId) t
            else t.copy(subs = t.subs.mapIndexed { i, s -> if (i == idx) s.copy(done = !s.done) else s })
        })
    }

    fun addAi(list: List<AiTask>) = update { d ->
        d.copy(
            tasks = d.tasks + list.map { it.task },
            wishes = d.wishes + list.filter { it.wish.isNotBlank() }
                .map { Wish(name = it.wish, cost = it.task.flowers, ai = true) }
        )
    }

    /** 完成任务：只有任务能产生小红花。返回获得的小红花数。 */
    fun completeTask(id: String): Int {
        val t = data.tasks.firstOrNull { it.id == id } ?: return 0
        update { d ->
            var tasks = d.tasks.map {
                if (it.id == id) it.copy(done = true, doneAt = System.currentTimeMillis(), subs = it.subs.map { s -> s.copy(done = true) }) else it
            }
            nextRepeat(t)?.let { tasks = tasks + it }
            d.copy(tasks = tasks, flowers = d.flowers + t.flowers, currentId = "")
        }
        return t.flowers
    }

    private fun nextRepeat(t: Task): Task? {
        if (t.repeat == 0) return null
        val base = runCatching { LocalDate.parse(t.date) }.getOrElse { LocalDate.now() }
        val next = when (t.repeat) { 1 -> base.plusDays(1); 2 -> base.plusWeeks(1); else -> base.plusMonths(1) }
        val end = runCatching { LocalDate.parse(t.repeatEnd) }.getOrNull()
        if (end != null && next.isAfter(end)) return null
        return t.copy(id = uid(), date = next.toString(), subs = t.subs.map { it.copy(done = false) }, done = false, doneAt = 0)
    }

    // ----- 心愿 -----
    fun addWish(w: Wish) = update { it.copy(wishes = it.wishes + w) }
    fun deleteWish(id: String) = update { it.copy(wishes = it.wishes.filter { w -> w.id != id }) }
    fun redeem(id: String) = update { d ->
        val w = d.wishes.firstOrNull { it.id == id }
        if (w == null || w.redeemed || d.flowers < w.cost) d
        else d.copy(flowers = d.flowers - w.cost, wishes = d.wishes.map { if (it.id == id) it.copy(redeemed = true) else it })
    }
}
