package com.gexixi.checkin

import java.time.LocalDate

/** 日历上的一个色块（已经裁到当天范围）。日历页和桌面小组件共用同一套错开算法。 */
class Ev(val s: Session, val tag: Tag, val st: Long, val en: Long) {
    var wantMs: Long = 0L  // 块里有文字/照片/录音时，需要额外撑高的时间长度（让内容放得下）
    var vEn: Long = en     // 视觉上占到的结束时间（太短的块也要占一小段高度）
    var lane = 0
    var lanes = 1
    val emoji: String get() = s.emoji.ifBlank { tag.emoji }
    val name: String get() = s.title.ifBlank { tag.name }
    /** 日历里「有具体时间的待办」也画成一个块（id 以 task_ 开头），点圆圈就是完成 */
    val isTask: Boolean get() = s.id.startsWith("task_")
    val taskId: String get() = s.id.removePrefix("task_")
}

fun dayEvents(d: Data, date: LocalDate, now: Long = System.currentTimeMillis(), withTasks: Boolean = true): List<Ev> {
    val ds = Repo.dayStart(date)
    val de = ds + 86400000L
    val out = d.sessions.mapNotNull { s ->
        val tag = d.tags.firstOrNull { it.id == s.tagId } ?: return@mapNotNull null
        val st = maxOf(s.start, ds)
        val en = minOf(s.end ?: now, de)
        if (en > st) Ev(s, tag, st, en) else null
    }.toMutableList()
    if (withTasks) {
        val key = date.toString()
        d.tasks.filter { !it.done && it.date == key && it.time.length >= 5 }.forEach { t ->
            val h = t.time.take(2).toIntOrNull()
            val m = t.time.drop(3).take(2).toIntOrNull()
            if (h != null && m != null) {
                val st = ds + h * 3600000L + m * 60000L
                val color = d.lists.firstOrNull { it.id == t.listId }?.color ?: 6
                out.add(Ev(Session("task_" + t.id, "task", st, st + 30 * 60000L), Tag("task", t.title, "◻️", "", color), st, st + 30 * 60000L))
            }
        }
    }
    return out
}

/**
 * 相近或同时发生的事不重叠：按「视觉结束时间」分车道（左右错开），
 * 太短的块也会占一段最小高度，所以上下挨着的块不会压在一起。
 */
fun assignLanes(evs: List<Ev>, minVisMs: Long) {
    evs.forEach { it.vEn = maxOf(it.en, it.st + maxOf(minVisMs, it.wantMs)) }
    val sorted = evs.sortedWith(compareBy({ it.st }, { it.vEn }))
    val cluster = mutableListOf<Ev>()
    val laneEnds = mutableListOf<Long>()
    var clusterEnd = 0L
    fun flush() {
        val n = laneEnds.size.coerceAtLeast(1)
        cluster.forEach { it.lanes = n }
        cluster.clear()
        laneEnds.clear()
    }
    for (e in sorted) {
        if (cluster.isNotEmpty() && e.st >= clusterEnd) flush()
        var idx = laneEnds.indexOfFirst { it <= e.st }
        if (idx < 0) { laneEnds.add(e.vEn); idx = laneEnds.size - 1 } else laneEnds[idx] = e.vEn
        e.lane = idx
        cluster.add(e)
        clusterEnd = maxOf(clusterEnd, e.vEn)
    }
    flush()
}

/** 把一组 [start,end] 合并后求总毫秒（重叠的只算一次） */
fun unionMs(list: List<Pair<Long, Long>>): Long {
    var total = 0L
    var curS = -1L
    var curE = -1L
    for ((s, e) in list.sortedBy { it.first }) {
        if (curS < 0) { curS = s; curE = e }
        else if (s <= curE) curE = maxOf(curE, e)
        else { total += curE - curS; curS = s; curE = e }
    }
    if (curS >= 0) total += curE - curS
    return total
}

/** 块里有文字 / 照片 / 录音时，至少要多高（dp）才放得下；什么都没有就返回 0 */
fun wantDp(e: Ev): Float {
    if (e.isTask) return 0f
    val s = e.s
    if (s.note.isBlank() && s.photos.isEmpty() && s.audio.isBlank()) return 0f
    var h = 12f
    if (s.photos.isNotEmpty()) h += 42f                      // 照片在最上面
    if (e.tag.id != "x_note") h += 18f + 16f                 // 名称行 + 时间行
    if (s.audio.isNotBlank()) h += 38f
    if (s.note.isNotBlank()) h += 15f * (s.note.length / 18 + 1).coerceAtMost(4)
    return maxOf(h, 30f)
}
