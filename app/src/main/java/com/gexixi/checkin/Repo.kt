package com.gexixi.checkin

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 完成任务后弹窗要显示的内容 */
data class Cel(
    val gain: Int,
    val ch: String = "",       // 角色key，决定弹窗GIF
    val text: String = "",     // 这条鼓励音频的台词
    val reward: String = "",   // 任务奖励（直接领取，不扣小红花）
    val hint: String = "",     // 现在可兑换 / 再差N朵
    val todayN: Int = 0,
    val goal: Int = 5
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
        file = File(app.filesDir, "dangxia.json")
        data = try {
            Gson().fromJson(file.readText(), Data::class.java) ?: Data()
        } catch (e: Exception) {
            Data()
        }
        inited = true
    }

    /** 仅供 Android Studio 预览窗格塞示例数据 */
    fun preview(d: Data) { data = d }

    fun update(f: (Data) -> Data) {
        data = f(data)
        try { file.writeText(Gson().toJson(data)) } catch (_: Exception) {}
    }

    fun setPrefs(f: (Prefs) -> Prefs) {
        update { it.copy(prefs = f(it.prefs)) }
        Tracker.ensure(app)
    }

    private fun afterChange() {
        Tracker.ensure(app)
        Widgets.refreshAll(app)
    }

    fun today(): String = LocalDate.now().toString()
    fun balance(): Int = data.earned - data.spent
    fun dayStart(d: LocalDate = LocalDate.now()): Long = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun flowersFor(stars: Int): Int = data.prefs.taskStarFlowers.getOrElse(stars - 1) { stars }
    fun todayFlowers(): Int = data.flowerLog.filter { it.ts >= dayStart() }.sumOf { it.n }

    // ================= 生活记录 =================
    fun tag(id: String): Tag? = data.tags.firstOrNull { it.id == id }
    fun running(): List<Session> = data.sessions.filter { it.end == null }

    /** 点一下开始、再点一下结束；多个标签可同时进行。返回刚结束的那条（没有则 null）。 */
    fun toggleTag(id: String): Session? {
        val now = System.currentTimeMillis()
        var ended: Session? = null
        update { d ->
            val run = d.sessions.firstOrNull { it.tagId == id && it.end == null && !it.auto }
            if (run != null) {
                ended = run.copy(end = now)
                d.copy(sessions = d.sessions.map { if (it.id == run.id) run.copy(end = now) else it })
            } else d.copy(sessions = d.sessions + Session(uid(), id, now))
        }
        afterChange()
        return ended
    }

    fun stopAllManual() {
        val now = System.currentTimeMillis()
        update { d -> d.copy(sessions = d.sessions.map { if (it.end == null && !it.auto) it.copy(end = now) else it }) }
        afterChange()
    }

    fun addSession(s: Session) { update { it.copy(sessions = it.sessions + s) }; Widgets.refreshAll(app) }
    fun addTag(t: Tag) { update { it.copy(tags = it.tags + t) }; Widgets.refreshAll(app) }
    fun deleteTag(id: String) {
        update { d -> d.copy(tags = d.tags.filter { it.id != id }, sessions = d.sessions.filter { it.tagId != id }) }
        Widgets.refreshAll(app)
    }

    // ================= 任务 =================
    fun visibleTasks(): List<Task> {
        val td = today()
        return data.tasks.filter { !it.done && (it.date.isEmpty() || it.date <= td) }
            .sortedWith(compareBy<Task>({ it.date.ifEmpty { td } }, { it.time.ifEmpty { "99:99" } }))
    }

    fun currentTask(): Task? {
        val v = visibleTasks()
        return v.firstOrNull { it.id == data.currentId } ?: v.firstOrNull()
    }

    fun list(id: String): TaskList = data.lists.firstOrNull { it.id == id } ?: data.lists.first()

    private fun withReward(t: Task, wishes: List<Wish>): Pair<Task, List<Wish>> {
        if (t.reward.isBlank()) return t to wishes
        val own = wishes.firstOrNull { it.kind == 0 && it.status == 0 && it.name == t.reward }
        if (own != null) return t.copy(rewardWishId = own.id) to wishes
        val ex = wishes.firstOrNull { it.id == t.rewardWishId && it.kind == 1 }
        if (ex != null) return t to wishes.map { if (it.id == ex.id) it.copy(name = t.reward) else it }
        val w = Wish(name = t.reward, cost = 0, kind = 1, fromTask = t.id)
        return t.copy(rewardWishId = w.id) to (wishes + w)
    }

    fun addTask(t: Task, makeCurrent: Boolean = false) {
        val (nt, ws) = withReward(t, data.wishes)
        update { d -> d.copy(tasks = d.tasks + nt, wishes = ws, currentId = if (makeCurrent) nt.id else d.currentId) }
        Voice.warm(app, nt)
        Widgets.refreshAll(app)
    }

    fun updateTask(t: Task) {
        val (nt, ws) = withReward(t, data.wishes)
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == nt.id) nt else it }, wishes = ws) }
        Voice.warm(app, nt)
        Widgets.refreshAll(app)
    }

    fun deleteTask(id: String) {
        update { d ->
            d.copy(
                tasks = d.tasks.filter { it.id != id },
                wishes = d.wishes.filter { !(it.fromTask == id && it.status == 0) },
                currentId = if (d.currentId == id) "" else d.currentId
            )
        }
        Widgets.refreshAll(app)
    }

    fun setCurrent(id: String) { update { it.copy(currentId = id) }; Widgets.refreshAll(app) }

    /** 勾/取消一个子任务；返回「勾完之后是否全部子任务都完成了」 */
    fun toggleSub(taskId: String, idx: Int): Boolean {
        update { d ->
            d.copy(tasks = d.tasks.map { t ->
                if (t.id != taskId) t
                else t.copy(subs = t.subs.mapIndexed { i, s -> if (i == idx) s.copy(done = !s.done) else s })
            })
        }
        Widgets.refreshAll(app)
        val t = data.tasks.firstOrNull { it.id == taskId } ?: return false
        return t.subs.isNotEmpty() && t.subs.all { it.done }
    }

    /** 任务自己的计时（点子任务旁的 ▶）：与生活记录分开，只累计在任务上 */
    fun toggleTimer(taskId: String, sub: Int) {
        val now = System.currentTimeMillis()
        update { d ->
            d.copy(tasks = d.tasks.map { t ->
                if (t.id != taskId) t
                else if (t.timerStart == 0L) t.copy(timerStart = now, timerSub = sub)
                else t.copy(workMs = t.workMs + (now - t.timerStart), timerStart = 0, timerSub = -1)
            })
        }
    }

    fun nextDate(t: Task, from: LocalDate): LocalDate {
        val n = t.every.coerceAtLeast(1).toLong()
        return when (t.repeat) {
            1 -> from.plusDays(n)
            2 -> {
                if (t.weekdays.isEmpty()) from.plusWeeks(n)
                else {
                    var d = from.plusDays(1)
                    var guard = 0
                    var found: LocalDate? = null
                    while (guard < 800 && found == null) {
                        if (d.dayOfWeek.value in t.weekdays) {
                            val gap = ChronoUnit.WEEKS.between(from.with(DayOfWeek.MONDAY), d.with(DayOfWeek.MONDAY))
                            if (gap % n == 0L) found = d
                        }
                        d = d.plusDays(1); guard++
                    }
                    found ?: from.plusWeeks(n)
                }
            }
            3 -> from.plusMonths(n)
            else -> from.plusYears(n)
        }
    }

    private fun nextOccurrence(t: Task, td: String): Task {
        val parsed = runCatching { LocalDate.parse(t.date) }.getOrNull()
        val base = if (parsed == null || parsed.isBefore(LocalDate.now())) LocalDate.now() else parsed
        val next = nextDate(t, base)
        val end = runCatching { LocalDate.parse(t.repeatEnd) }.getOrNull()
        val reset = t.copy(date = next.toString(), subs = t.subs.map { it.copy(done = false) }, doneDates = t.doneDates + td)
        return if (end != null && next.isAfter(end)) reset.copy(done = true, doneAt = System.currentTimeMillis()) else reset
    }

    /** 完成任务：只有任务能产生小红花。重复任务自动在下一个日期重新出现、子任务重置。 */
    fun completeTask(id: String): Cel? {
        val t = data.tasks.firstOrNull { it.id == id } ?: return null
        val cap = data.prefs.rewardCap
        val gain = if (cap > 0) t.flowers.coerceAtMost(cap) else t.flowers
        val now = System.currentTimeMillis()
        val td = today()
        update { d ->
            val fin = t.copy(workMs = t.workMs + (if (t.timerStart > 0) now - t.timerStart else 0L), timerStart = 0, timerSub = -1)
            val tasks = d.tasks.map { x ->
                if (x.id != id) x
                else if (t.repeat == 0) fin.copy(done = true, doneAt = now, subs = fin.subs.map { it.copy(done = true) }, doneDates = fin.doneDates + td)
                else nextOccurrence(fin, td)
            }
            val wishes = d.wishes.map { w -> if (w.id == t.rewardWishId && w.status == 0) w.copy(status = 1, doneDate = td) else w }
            d.copy(
                tasks = tasks, wishes = wishes, earned = d.earned + gain,
                flowerLog = d.flowerLog + FlowerLog(now, gain, id), currentId = ""
            )
        }
        Widgets.refreshAll(app)
        val bal = balance()
        val ongoing = data.wishes.filter { it.kind == 0 && it.status == 0 }
        val can = ongoing.filter { it.cost <= bal }.maxByOrNull { it.cost }
        val hint = if (can != null) "现在可兑换：" + can.name
        else ongoing.minByOrNull { it.cost }?.let { "再 " + (it.cost - bal) + " 朵就能兑换" + it.name } ?: ""
        return Cel(gain, reward = if (t.reward.isNotBlank()) t.reward else "", hint = hint, todayN = todayFlowers(), goal = data.prefs.dailyGoal)
    }

    // ================= 心愿 =================
    fun addWish(w: Wish) { update { it.copy(wishes = it.wishes + w) } }
    fun deleteWish(id: String) { update { it.copy(wishes = it.wishes.filter { w -> w.id != id }) } }

    /** 兑换：扣小红花、移到已实现并记录日期。返回错误提示，成功返回 null。 */
    fun redeem(id: String): String? {
        val w = data.wishes.firstOrNull { it.id == id } ?: return "找不到这个心愿"
        if (w.kind != 0 || w.status != 0) return "这个心愿不能兑换"
        if (balance() < w.cost) return "小红花还差 " + (w.cost - balance()) + " 朵"
        val lim = data.prefs.dailyLimit
        val td = today()
        if (lim > 0 && data.wishes.count { it.kind == 0 && it.status == 1 && it.doneDate == td } >= lim) return "今天最多兑换 $lim 个"
        update { d ->
            d.copy(
                spent = d.spent + w.cost,
                wishes = d.wishes.map { if (it.id == id) it.copy(status = 1, doneDate = td) else it }
            )
        }
        return null
    }

    // ================= 日记 =================
    fun diaryOf(date: String): Diary = data.diary.firstOrNull { it.date == date } ?: Diary(date)
    private fun putDiary(d: Data, e: Diary): Data = d.copy(diary = d.diary.filter { it.date != e.date } + e)
    fun diaryAddFiles(date: String, paths: List<String>) = update { d -> val e = diaryOf(date); putDiary(d, e.copy(files = e.files + paths)) }
    fun diaryRemoveFile(date: String, path: String) {
        update { d -> val e = diaryOf(date); putDiary(d, e.copy(files = e.files.filter { it != path })) }
        try { File(path).delete() } catch (_: Exception) {}
    }
    fun diarySetText(date: String, text: String) = update { d -> putDiary(d, diaryOf(date).copy(text = text)) }

    // ================= 小组件状态 / 备份 =================
    fun setWidgetGroup(g: String) { update { it.copy(widgetGroup = g) }; Widgets.refreshAll(app) }
    fun shiftSummary(delta: Int) { update { it.copy(sumOffset = (it.sumOffset + delta).coerceAtMost(0)) }; Widgets.refreshAll(app) }

    /** 导出备份：数据 + 日记 + 心愿图片 打成一个压缩包 */
    fun exportBackup(ctx: Context, uri: Uri) {
        ctx.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(os).use { z ->
                fun add(f: File, name: String) {
                    z.putNextEntry(ZipEntry(name))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
                if (file.exists()) add(file, "dangxia.json")
                File(app.filesDir, "diary").walkTopDown().filter { it.isFile }.forEach {
                    add(it, "diary/" + it.relativeTo(File(app.filesDir, "diary")).path)
                }
                app.filesDir.listFiles()?.filter { it.isFile && it.name.startsWith("wish_") }?.forEach { add(it, "wishes/" + it.name) }
            }
        }
    }
}
