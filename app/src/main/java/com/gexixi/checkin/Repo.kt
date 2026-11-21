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
    val ch: String = "",        // 角色key，决定弹窗GIF
    val text: String = "",      // 这条鼓励音频的台词
    val reward: String = "",    // 任务奖励（宝箱，直接领取，不扣金币）
    val hint: String = "",      // 现在可兑换 / 再差N枚
    val todayN: Int = 0,
    val goal: Int = 5,
    val comment: String = "",   // AI 结算评语
    val planned: Int = -1       // 建任务时预设的金币数（和实际不同时显示）
)

object Repo {
    lateinit var app: Context
    private lateinit var file: File
    private var inited = false
    var data by mutableStateOf(Data())
        private set

    /** 等待结算的任务 id：非空时，App 最外层会弹出「完成前先自检」的结算窗 */
    var settleId by mutableStateOf("")

    fun init(ctx: Context) {
        if (inited) return
        app = ctx.applicationContext
        file = File(app.filesDir, "dangxia.json")
        val loaded = try {
            Gson().fromJson(file.readText(), Data::class.java) ?: Data()
        } catch (e: Exception) {
            Data()
        }
        data = normalize(loaded)
        inited = true
    }

    /** 旧版数据升级：补上新增的自动记录对应、金币图标、5 分钟门槛 */
    private fun normalize(d0: Data): Data {
        var d = d0
        if (d.ver < 2) {
            val p = d.prefs
            val have = p.autoMap.map { it.pkg }.toSet()
            val extra = defaultAutoMap().filter { m -> m.pkg !in have && d.tags.any { it.id == m.tagId } }
            d = d.copy(prefs = p.copy(coin = "🪙", minMin = 5, autoMap = p.autoMap + extra), ver = 2)
        }
        if (d.ver < 3) {
            val sys = defaultTags().filter { it.hidden() && d.tags.none { x -> x.id == it.id } }
            d = d.copy(tags = d.tags + sys, groups = if (d.groups.isEmpty()) defaultGroups() else d.groups, ver = 3)
        }
        return d
    }

    /** 仅供 Android Studio 预览窗格塞示例数据 */
    fun preview(d: Data) { data = d }

    @Synchronized
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

    fun updateTag(t: Tag) {
        update { d -> d.copy(tags = d.tags.map { if (it.id == t.id) t else it }) }
        Widgets.refreshAll(app)
    }

    fun deleteTag(id: String) {
        update { d -> d.copy(tags = d.tags.filter { it.id != id }, sessions = d.sessions.filter { it.tagId != id }) }
        Widgets.refreshAll(app)
    }

    fun updateSession(id: String, f: (Session) -> Session) {
        update { d -> d.copy(sessions = d.sessions.map { if (it.id == id) f(it) else it }) }
        Widgets.refreshAll(app)
    }

    fun deleteSession(id: String) {
        val s = data.sessions.firstOrNull { it.id == id }
        update { d -> d.copy(sessions = d.sessions.filter { it.id != id }) }
        s?.photos?.forEach { try { File(it).delete() } catch (_: Exception) {} }
        Widgets.refreshAll(app)
    }

    fun setWeather(date: String, emoji: String) {
        update { d -> d.copy(weather = if (emoji.isEmpty()) d.weather - date else d.weather + (date to emoji)) }
        Widgets.refreshAll(app)
    }

    /** 清掉已经记下的、不到 minMin 分钟的自动记录（带了文字或照片的保留） */
    fun purgeShortAuto(minMin: Int): Int {
        val lim = minMin * 60000L
        val bad = data.sessions.filter { it.auto && it.end != null && it.end - it.start < lim && it.note.isBlank() && it.photos.isEmpty() }
        if (bad.isEmpty()) return 0
        val ids = bad.map { it.id }.toSet()
        update { d -> d.copy(sessions = d.sessions.filter { it.id !in ids }) }
        Widgets.refreshAll(app)
        return bad.size
    }

    /** 记录页进度卡里的 10 个标签：最近两周手动记得最多的，不够 10 个用默认常用补 */
    fun quickTags(): List<Tag> {
        val since = System.currentTimeMillis() - 14 * 86400000L
        val counts = data.sessions.filter { !it.auto && it.start >= since }.groupingBy { it.tagId }.eachCount()
        val top = counts.entries.sortedByDescending { it.value }.mapNotNull { e -> tag(e.key) }.take(10)
        val out = top.toMutableList()
        for (id in QUICK_DEFAULT) {
            if (out.size >= 10) break
            val t = tag(id)
            if (t != null && out.none { it.id == t.id }) out.add(t)
        }
        for (t in data.tags) {
            if (out.size >= 10) break
            if (out.none { it.id == t.id }) out.add(t)
        }
        return out.take(10)
    }

    // ---- 标签分组（可增减、改名、改颜色） ----
    fun addGroup(g: GroupDef) { update { it.copy(groups = it.groups + g) } }

    fun updateGroup(old: String, g: GroupDef) {
        update { d ->
            d.copy(
                groups = d.groups.map { if (it.name == old) g else it },
                tags = d.tags.map { if (it.group == old) it.copy(group = g.name) else it },
                widgetGroup = if (d.widgetGroup == old) g.name else d.widgetGroup
            )
        }
        Widgets.refreshAll(app)
    }

    /** 删掉分组：里面的标签挪到第一个剩下的分组，不会丢记录 */
    fun deleteGroup(name: String) {
        update { d ->
            val rest = d.groups.filter { it.name != name }
            if (rest.isEmpty()) d
            else d.copy(
                groups = rest,
                tags = d.tags.map { if (it.group == name) it.copy(group = rest.first().name) else it },
                widgetGroup = if (d.widgetGroup == name) rest.first().name else d.widgetGroup
            )
        }
        Widgets.refreshAll(app)
    }

    // ---- 番茄钟（专注计时）：点一下开始，再点一下结束并填「这是干什么的」，写进日历 ----
    fun startStopwatch() { update { it.copy(stopwatchStart = System.currentTimeMillis()) } }
    fun cancelStopwatch() { update { it.copy(stopwatchStart = 0L) } }

    fun finishStopwatch(tagId: String, title: String, note: String) {
        val st = data.stopwatchStart
        if (st == 0L) return
        val en = System.currentTimeMillis()
        val tg = if (tag(tagId) != null) tagId else "x_focus"
        update { d ->
            d.copy(
                sessions = d.sessions + Session(uid(), tg, st, en, false, note.trim(), emptyList(), "", "", title.trim()).copy(audio = ""),
                stopwatchStart = 0L
            )
        }
        Widgets.refreshAll(app)
    }

    /** 日历底栏「文字 / 录音」：在当下这个时间写入日历一条即时记录 */
    fun addMoment(note: String, audio: String, start: Long = System.currentTimeMillis(), end: Long = start + 60000L): String {
        val s = Session(uid(), "x_note", start, end, false, note, emptyList(), audio)
        update { it.copy(sessions = it.sessions + s) }
        Widgets.refreshAll(app)
        return s.id
    }

    // ---- 人物界面 ----
    fun bumpPlay(ch: String) {
        if (ch.isBlank()) return
        update { d -> d.copy(charPlays = d.charPlays + (ch to ((d.charPlays[ch] ?: 0) + 1))) }
    }

    fun setCharPhoto(ch: String, path: String) { update { d -> d.copy(charPhotos = d.charPhotos + (ch to path)) } }
    fun clearCharPhoto(ch: String) { update { d -> d.copy(charPhotos = d.charPhotos - ch) } }

    fun setShopOpen(b: Boolean) { update { it.copy(shopOpen = b) } }

    // ================= 任务 =================
    fun visibleTasks(): List<Task> {
        val td = today()
        return data.tasks.filter { !it.done && (it.date.isEmpty() || it.date <= td) }
            .sortedWith(compareBy<Task>({ it.date.ifEmpty { td } }, { it.time.ifEmpty { "99:99" } }))
    }

    /** 所有没完成的任务（包含日期还没到的），待办列表用 */
    fun pendingTasks(): List<Task> =
        data.tasks.filter { !it.done }.sortedWith(compareBy<Task>({ it.date.ifEmpty { today() } }, { it.time.ifEmpty { "99:99" } }))

    fun currentTask(): Task? {
        val v = visibleTasks()
        return v.firstOrNull { it.id == data.currentId } ?: v.firstOrNull()
    }

    fun list(id: String): TaskList = data.lists.firstOrNull { it.id == id } ?: data.lists.first()
    fun addList(l: TaskList) { update { it.copy(lists = it.lists + l) } }

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
        val reset = t.copy(
            date = next.toString(), subs = t.subs.map { it.copy(done = false) }, doneDates = t.doneDates + td,
            doneNote = "", settled = -1, settleComment = ""
        )
        return if (end != null && next.isAfter(end)) reset.copy(done = true, doneAt = System.currentTimeMillis()) else reset
    }

    /** 点「完成」先走结算窗（自检达成条件 + 写感想），结算完才真正完成 */
    fun requestSettle(id: String) { settleId = id }

    /**
     * 完成任务：只有任务能产生金币。
     * coins 为空 = 按预设；不为空 = 结算后的实际金币（可能和预设不同）。重复任务自动在下一个日期重新出现、子任务重置。
     */
    fun completeTask(id: String, coins: Int? = null, note: String = "", comment: String = ""): Cel? {
        val t = data.tasks.firstOrNull { it.id == id } ?: return null
        val cap = data.prefs.rewardCap
        val planned = if (cap > 0) t.flowers.coerceAtMost(cap) else t.flowers
        val gain = (coins ?: planned).coerceAtLeast(0)
        val now = System.currentTimeMillis()
        val td = today()
        update { d ->
            val fin = t.copy(
                workMs = t.workMs + (if (t.timerStart > 0) now - t.timerStart else 0L), timerStart = 0, timerSub = -1,
                doneNote = note, settled = gain, settleComment = comment
            )
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
        else ongoing.minByOrNull { it.cost }?.let { "再 " + (it.cost - bal) + " 枚就能兑换" + it.name } ?: ""
        return Cel(
            gain, reward = t.reward, hint = hint, todayN = todayFlowers(), goal = data.prefs.dailyGoal,
            comment = comment, planned = planned
        )
    }

    // ================= 游戏商城 =================
    fun addWish(w: Wish) { update { it.copy(wishes = it.wishes + w) } }
    fun deleteWish(id: String) { update { it.copy(wishes = it.wishes.filter { w -> w.id != id }) } }

    /** 兑换：扣金币、移到已实现并记录日期。返回错误提示，成功返回 null。 */
    fun redeem(id: String): String? {
        val w = data.wishes.firstOrNull { it.id == id } ?: return "找不到这件商品"
        if (w.kind != 0 || w.status != 0) return "这件商品不能兑换"
        if (balance() < w.cost) return "金币还差 " + (w.cost - balance()) + " 枚"
        val lim = data.prefs.dailyLimit
        val td = today()
        if (lim > 0 && data.wishes.count { it.kind == 0 && it.status == 1 && it.doneDate == td } >= lim) return "今天最多兑换 $lim 件"
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
    fun diarySetReview(date: String, review: String) = update { d -> putDiary(d, diaryOf(date).copy(review = review)) }

    /** 给 AI 看的「这一天发生了什么」：生活记录（含感想）+ 完成的任务 + 天气 */
    fun dayContext(date: LocalDate): String {
        val zone = ZoneId.systemDefault()
        val ds = dayStart(date)
        val de = ds + 86400000L
        val sb = StringBuilder()
        sb.append("日期：").append(date.toString()).append("\n")
        data.weather[date.toString()]?.let { sb.append("天气：").append(it).append("\n") }
        val ss = data.sessions.filter { it.start in ds until de }.sortedBy { it.start }
        if (ss.isNotEmpty()) {
            sb.append("生活记录：\n")
            ss.take(20).forEach { s ->
                val t = tag(s.tagId)
                if (t != null && s.tagId != "x_note") {
                    val mins = ((s.end ?: s.start) - s.start) / 60000
                    sb.append("- ").append(s.title.ifBlank { t.name }).append("，").append(mins).append("分钟")
                    if (s.note.isNotBlank()) sb.append("，当时的感想：").append(s.note)
                    sb.append("\n")
                } else if (s.note.isNotBlank()) sb.append("- 随手记：").append(s.note).append("\n")
            }
        }
        val done = data.tasks.filter { it.doneAt in ds until de }
        if (done.isNotEmpty()) {
            sb.append("完成的任务：\n")
            done.take(10).forEach { t ->
                sb.append("- ").append(t.title)
                if (t.doneNote.isNotBlank()) sb.append("（感想：").append(t.doneNote).append("）")
                sb.append("\n")
            }
        }
        if (ss.isEmpty() && done.isEmpty()) sb.append("（今天几乎没有记录）\n")
        return sb.toString()
    }

    // ================= 朋友圈 =================
    fun addFriend(f: Friend) { update { it.copy(friends = it.friends + f) } }
    fun updateFriend(f: Friend) { update { d -> d.copy(friends = d.friends.map { if (it.id == f.id) f else it }) } }

    fun deleteFriend(id: String) {
        update { d ->
            d.copy(
                friends = d.friends.filter { it.id != id },
                posts = d.posts.filter { it.author != id }.map { p ->
                    p.copy(likes = p.likes - id, comments = p.comments.filter { it.author != id })
                }
            )
        }
    }

    fun addPost(p: Post) { update { it.copy(posts = it.posts + p) } }

    fun deletePost(id: String) {
        val p = data.posts.firstOrNull { it.id == id }
        update { d -> d.copy(posts = d.posts.filter { it.id != id }) }
        p?.photos?.forEach { try { File(it).delete() } catch (_: Exception) {} }
    }

    fun setLike(postId: String, who: String, on: Boolean) {
        update { d ->
            d.copy(posts = d.posts.map { p ->
                if (p.id != postId) p
                else p.copy(likes = if (on) (if (who in p.likes) p.likes else p.likes + who) else p.likes - who)
            })
        }
    }

    fun addComment(postId: String, c: MComment) {
        update { d -> d.copy(posts = d.posts.map { p -> if (p.id == postId) p.copy(comments = p.comments + c) else p }) }
    }

    fun diarySetText(date: String, text: String) = update { d -> putDiary(d, diaryOf(date).copy(text = text)) }

    // ================= 小组件状态 / 备份 =================
    fun setWidgetGroup(g: String) { update { it.copy(widgetGroup = g) }; Widgets.refreshAll(app) }
    fun shiftSummary(delta: Int) { update { it.copy(sumOffset = (it.sumOffset + delta).coerceAtMost(0)) }; Widgets.refreshAll(app) }
    fun resetSummary() { update { it.copy(sumOffset = 0) }; Widgets.refreshAll(app) }

    /** 导出备份：数据 + 日记 + 记录里的照片 + 商品图片 打成一个压缩包 */
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
                File(app.filesDir, "notes").walkTopDown().filter { it.isFile }.forEach {
                    add(it, "notes/" + it.relativeTo(File(app.filesDir, "notes")).path)
                }
                File(app.filesDir, "wishes").walkTopDown().filter { it.isFile }.forEach {
                    add(it, "wishes/" + it.relativeTo(File(app.filesDir, "wishes")).path)
                }
                File(app.filesDir, "friends").walkTopDown().filter { it.isFile }.forEach {
                    add(it, "friends/" + it.relativeTo(File(app.filesDir, "friends")).path)
                }
                File(app.filesDir, "moments").walkTopDown().filter { it.isFile }.forEach {
                    add(it, "moments/" + it.relativeTo(File(app.filesDir, "moments")).path)
                }
                File(app.filesDir, "people").walkTopDown().filter { it.isFile }.forEach {
                    add(it, "people/" + it.relativeTo(File(app.filesDir, "people")).path)
                }
                app.filesDir.listFiles()?.filter { it.isFile && it.name.startsWith("wish_") }?.forEach { add(it, "wishes/" + it.name) }
            }
        }
    }

    /**
     * 导入备份（导出的压缩包）：恢复数据、日记、照片、录音，设置（含 API 密钥、自动记录对应表）一起恢复。
     * 图片路径是旧手机的绝对路径，这里按文件名重新指到新位置。返回恢复了多少个文件。
     */
    fun importBackup(ctx: Context, uri: Uri): Int {
        val root = app.filesDir
        val byName = HashMap<String, File>()
        var json: String? = null
        var n = 0
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            java.util.zip.ZipInputStream(ins).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        if (e.name == "dangxia.json") {
                            json = z.readBytes().toString(Charsets.UTF_8)
                        } else {
                            val dest = File(root, e.name)
                            if (dest.canonicalPath.startsWith(root.canonicalPath)) {
                                dest.parentFile?.mkdirs()
                                dest.outputStream().use { z.copyTo(it) }
                                byName[dest.name] = dest
                                n++
                            }
                        }
                    }
                    e = z.nextEntry
                }
            }
        }
        val text = json ?: throw Exception("这个压缩包里没有 dangxia.json，不是备份文件")
        val loaded = Gson().fromJson(text, Data::class.java) ?: throw Exception("备份文件读不出来")
        fun fix(p: String): String {
            if (p.isBlank()) return p
            val rel = p.substringAfter("/files/", "")
            if (rel.isNotBlank() && File(root, rel).exists()) return File(root, rel).path
            return byName[File(p).name]?.path ?: p
        }
        val fixed = loaded.copy(
            wishes = loaded.wishes.map { it.copy(photo = fix(it.photo)) },
            sessions = loaded.sessions.map { it.copy(photos = it.photos.map { p -> fix(p) }, audio = fix(it.audio)) },
            diary = loaded.diary.map { it.copy(files = it.files.map { p -> fix(p) }) },
            charPhotos = loaded.charPhotos.mapValues { fix(it.value) },
            friends = loaded.friends.map { it.copy(avatar = fix(it.avatar)) },
            posts = loaded.posts.map { it.copy(photos = it.photos.map { p -> fix(p) }) }
        )
        data = normalize(fixed)
        try { file.writeText(Gson().toJson(data)) } catch (_: Exception) {}
        afterChange()
        return n
    }
}
