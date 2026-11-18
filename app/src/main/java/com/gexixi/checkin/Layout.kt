package com.gexixi.checkin

import java.time.LocalDate

/** 日历上的一个色块（已经裁到当天范围）。日历页和桌面小组件共用同一套错开算法。 */
class Ev(val s: Session, val tag: Tag, val st: Long, val en: Long) {
    var vEn: Long = en     // 视觉上占到的结束时间（太短的块也要占一小段高度）
    var lane = 0
    var lanes = 1
    val emoji: String get() = s.emoji.ifBlank { tag.emoji }
    val name: String get() = s.title.ifBlank { tag.name }
}

fun dayEvents(d: Data, date: LocalDate, now: Long = System.currentTimeMillis()): List<Ev> {
    val ds = Repo.dayStart(date)
    val de = ds + 86400000L
    return d.sessions.mapNotNull { s ->
        val tag = d.tags.firstOrNull { it.id == s.tagId } ?: return@mapNotNull null
        val st = maxOf(s.start, ds)
        val en = minOf(s.end ?: now, de)
        if (en > st) Ev(s, tag, st, en) else null
    }
}

/**
 * 相近或同时发生的事不重叠：按「视觉结束时间」分车道（左右错开），
 * 太短的块也会占一段最小高度，所以上下挨着的块不会压在一起。
 */
fun assignLanes(evs: List<Ev>, minVisMs: Long) {
    evs.forEach { it.vEn = maxOf(it.en, it.st + minVisMs) }
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
