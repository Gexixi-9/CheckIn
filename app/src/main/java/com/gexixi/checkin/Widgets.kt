package com.gexixi.checkin

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate

object Widgets {
    fun refreshAll(ctx: Context) {
        try {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, TagWidget::class.java)).let {
                if (it.isNotEmpty()) mgr.updateAppWidget(it, TagWidget.build(ctx))
            }
            mgr.getAppWidgetIds(ComponentName(ctx, SummaryWidget::class.java)).let {
                if (it.isNotEmpty()) mgr.updateAppWidget(it, SummaryWidget.build(ctx))
            }
            mgr.getAppWidgetIds(ComponentName(ctx, TaskWidget::class.java)).let {
                if (it.isNotEmpty()) mgr.updateAppWidget(it, TaskWidget.build(ctx))
            }
        } catch (_: Exception) {}
    }

    fun pi(ctx: Context, cls: Class<*>, action: String, vararg extras: Pair<String, String>): PendingIntent {
        val i = Intent(ctx, cls).setAction(action)
        extras.forEach { i.putExtra(it.first, it.second) }
        val req = (action + extras.joinToString("|") { it.second }).hashCode()
        return PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, 99, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** 圆形按钮：浅色底 + emoji；进行中的用深色底加粗圈高亮 */
    fun circleBitmap(emoji: String, color: Int, active: Boolean, size: Int = 128): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = (color and 0x00FFFFFF) or ((if (active) 0xCC else 0x44) shl 24)
        c.drawCircle(r, r, r - 4, p)
        if (active) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 8f
            p.color = color or (0xFF shl 24)
            c.drawCircle(r, r, r - 6, p)
        }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.textSize = size * 0.5f
        tp.textAlign = Paint.Align.CENTER
        c.drawText(emoji, r, r - (tp.ascent() + tp.descent()) / 2f, tp)
        return bmp
    }

    /** 提醒事项风格的空心圆圈 */
    fun hollowCircle(color: Int, size: Int = 72): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = color or (0xFF shl 24)
        c.drawCircle(size / 2f, size / 2f, size / 2f - 6f, p)
        return bmp
    }
}

/** 点标签的小组件：左边分组切换，右边圆形按钮，点一下开始/结束 */
class TagWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        Repo.init(context)
        ids.forEach { mgr.updateAppWidget(it, build(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Repo.init(context)
        when (intent.action) {
            ACTION_TOGGLE -> intent.getStringExtra("tag")?.let { Repo.toggleTag(it) }
            ACTION_GROUP -> intent.getStringExtra("group")?.let { Repo.setWidgetGroup(it) }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.gexixi.checkin.TAG_TOGGLE"
        const val ACTION_GROUP = "com.gexixi.checkin.TAG_GROUP"
        private val gids = intArrayOf(R.id.g0, R.id.g1, R.id.g2, R.id.g3, R.id.g4, R.id.g5, R.id.g6)
        private val cells = intArrayOf(R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4, R.id.c5, R.id.c6, R.id.c7, R.id.c8)
        private val imgs = intArrayOf(R.id.i0, R.id.i1, R.id.i2, R.id.i3, R.id.i4, R.id.i5, R.id.i6, R.id.i7, R.id.i8)
        private val names = intArrayOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.n5, R.id.n6, R.id.n7, R.id.n8)
        private val chrs = intArrayOf(R.id.ch0, R.id.ch1, R.id.ch2, R.id.ch3, R.id.ch4, R.id.ch5, R.id.ch6, R.id.ch7, R.id.ch8)

        fun build(ctx: Context): RemoteViews {
            val d = Repo.data
            val rv = RemoteViews(ctx.packageName, R.layout.widget_tags)
            GROUPS.forEachIndexed { i, g ->
                val sel = g == d.widgetGroup
                rv.setTextViewText(gids[i], g)
                rv.setTextColor(gids[i], if (sel) 0xFF1E1E1E.toInt() else 0xFF8E9093.toInt())
                rv.setTextViewTextSize(gids[i], TypedValue.COMPLEX_UNIT_SP, if (sel) 15f else 12f)
                rv.setOnClickPendingIntent(gids[i], Widgets.pi(ctx, TagWidget::class.java, ACTION_GROUP, "group" to g))
            }
            val tags = d.tags.filter { it.group == d.widgetGroup }.take(9)
            for (i in cells.indices) {
                val tag = tags.getOrNull(i)
                if (tag == null) { rv.setViewVisibility(cells[i], View.INVISIBLE); continue }
                val run = d.sessions.firstOrNull { it.tagId == tag.id && it.end == null && !it.auto }
                rv.setViewVisibility(cells[i], View.VISIBLE)
                rv.setImageViewBitmap(imgs[i], Widgets.circleBitmap(tag.emoji, pint(tag.color), run != null))
                rv.setTextViewText(names[i], tag.name)
                if (run != null) {
                    val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - run.start)
                    rv.setViewVisibility(chrs[i], View.VISIBLE)
                    rv.setChronometer(chrs[i], base, null, true)
                } else {
                    rv.setChronometer(chrs[i], SystemClock.elapsedRealtime(), null, false)
                    rv.setViewVisibility(chrs[i], View.GONE)
                }
                rv.setOnClickPendingIntent(cells[i], Widgets.pi(ctx, TagWidget::class.java, ACTION_TOGGLE, "tag" to tag.id))
            }
            return rv
        }
    }
}

/** 总结小组件：左边按小时的色条（上午/下午），右边圆环 + 各项目时长，可往前翻天。全部本地计算，不联网。 */
class SummaryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        Repo.init(context)
        ids.forEach { mgr.updateAppWidget(it, build(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Repo.init(context)
        when (intent.action) {
            ACTION_PREV -> Repo.shiftSummary(-1)
            ACTION_NEXT -> Repo.shiftSummary(1)
        }
    }

    companion object {
        const val ACTION_PREV = "com.gexixi.checkin.SUM_PREV"
        const val ACTION_NEXT = "com.gexixi.checkin.SUM_NEXT"

        fun build(ctx: Context): RemoteViews {
            val d = Repo.data
            val date = LocalDate.now().plusDays(d.sumOffset.toLong())
            val rv = RemoteViews(ctx.packageName, R.layout.widget_summary)
            rv.setImageViewBitmap(R.id.sum_img, bitmap(d, date))
            rv.setOnClickPendingIntent(R.id.sum_prev, Widgets.pi(ctx, SummaryWidget::class.java, ACTION_PREV))
            rv.setOnClickPendingIntent(R.id.sum_next, Widgets.pi(ctx, SummaryWidget::class.java, ACTION_NEXT))
            return rv
        }

        private fun fmtDur(min: Long): String = if (min >= 60) "" + (min / 60) + "h " + (min % 60) + "m" else "" + min + "m"

        /** 把一组 [start,end] 合并后求总分钟 */
        private fun unionMin(list: List<Pair<Long, Long>>): Long {
            var total = 0L
            var curS = -1L
            var curE = -1L
            for ((s, e) in list.sortedBy { it.first }) {
                if (curS < 0) { curS = s; curE = e }
                else if (s <= curE) curE = maxOf(curE, e)
                else { total += curE - curS; curS = s; curE = e }
            }
            if (curS >= 0) total += curE - curS
            return total / 60000
        }

        fun bitmap(d: Data, date: LocalDate): Bitmap {
            val W = 880
            val H = 400
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val ds = Repo.dayStart(date)
            val de = ds + 86400000L
            val now = System.currentTimeMillis()
            // 当天每条记录（裁到当天范围）
            data class Seg(val tag: Tag, val s: Long, val e: Long)
            val segs = d.sessions.mapNotNull { s ->
                val tag = d.tags.firstOrNull { it.id == s.tagId } ?: return@mapNotNull null
                val st = maxOf(s.start, ds)
                val en = minOf(s.end ?: now, de)
                if (en > st) Seg(tag, st, en) else null
            }
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E1E1E.toInt(); textSize = 26f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
            val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE4E4E0.toInt() }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)

            // ---- 左：上午 / 下午 按小时的色条 ----
            c.drawText("上午", 110f, 38f, ink)
            c.drawText("下午", 330f, 38f, ink)
            for (r in 0 until 12) {
                val y = 56f + r * 24f
                ink.textSize = 20f
                c.drawText(if (r == 0) "0/12" else r.toString(), 220f, y + 15f, ink)
                for (half in 0..1) {
                    val hour = r + half * 12
                    val bx = if (half == 0) 30f else 250f
                    val bw = 160f
                    c.drawRoundRect(RectF(bx, y, bx + bw, y + 17f), 8f, 8f, soft)
                    val hs = ds + hour * 3600000L
                    val he = hs + 3600000L
                    segs.filter { it.e > hs && it.s < he }.forEach { sg ->
                        val a = (maxOf(sg.s, hs) - hs) / 3600000f
                        val b = (minOf(sg.e, he) - hs) / 3600000f
                        fill.color = pint(sg.tag.color)
                        c.drawRoundRect(RectF(bx + a * bw, y, maxOf(bx + b * bw, bx + a * bw + 8f), y + 17f), 8f, 8f, fill)
                    }
                }
            }
            // 空白时间
            val all = unionMin(segs.map { it.s to it.e })
            val blank = 1440L - all
            fill.color = 0xFFE4E4E0.toInt()
            c.drawCircle(40f, 372f, 9f, fill)
            ink.textAlign = Paint.Align.LEFT
            ink.textSize = 24f
            c.drawText("空白", 60f, 381f, ink)
            ink.textAlign = Paint.Align.RIGHT
            c.drawText((blank / 60).toString() + "h " + (blank % 60) + "m", 420f, 381f, ink)

            // ---- 分隔虚线 ----
            val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB8B8B2.toInt(); strokeWidth = 2f; pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f) }
            c.drawLine(440f, 16f, 440f, 384f, dash)

            // ---- 右：日期、圆环、项目时长 ----
            ink.textAlign = Paint.Align.CENTER
            ink.textSize = 34f
            c.drawText("" + date.monthValue + "月" + date.dayOfMonth + "日", 660f, 48f, ink)
            val ring = RectF(660f - 95f, 190f - 95f, 660f + 95f, 190f + 95f)
            val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 34f; color = 0xFFE4E4E0.toInt() }
            c.drawArc(ring, 0f, 360f, false, rp)
            // 按项目（分组）统计
            val byGroup = segs.groupBy { it.tag.group }.map { (g, list) ->
                Triple(g, pint(list.first().tag.color), unionMin(list.map { it.s to it.e }))
            }.sortedByDescending { it.third }
            var start = -90f
            byGroup.forEach { (_, col, min) ->
                val sweep = maxOf(min / 1440f * 360f, 2f)
                rp.color = col
                c.drawArc(ring, start, sweep, false, rp)
                start += sweep
            }
            ink.textSize = 36f
            c.drawText("" + (all * 100 / 1440) + "%", 660f, 203f, ink)
            // 图例
            byGroup.take(3).forEachIndexed { i, (g, col, min) ->
                val y = 322f + i * 32f
                fill.color = col
                c.drawCircle(470f, y - 7f, 9f, fill)
                ink.textAlign = Paint.Align.LEFT
                ink.textSize = 22f
                c.drawText(g, 492f, y, ink)
                ink.textAlign = Paint.Align.RIGHT
                c.drawText(fmtDur(min), 850f, y, ink)
            }
            return bmp
        }
    }
}

/** 任务清单小组件：提醒事项样式，直接点圆圈完成 */
class TaskWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        Repo.init(context)
        ids.forEach { mgr.updateAppWidget(it, build(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Repo.init(context)
        if (intent.action == ACTION_DONE) {
            val id = intent.getStringExtra("task") ?: return
            if (Repo.completeTask(id) != null) Media.cheer(context, Repo.data.prefs.character)
        }
    }

    companion object {
        const val ACTION_DONE = "com.gexixi.checkin.TASK_DONE"
        private val rows = intArrayOf(R.id.tr0, R.id.tr1, R.id.tr2, R.id.tr3, R.id.tr4, R.id.tr5)
        private val circles = intArrayOf(R.id.tc0, R.id.tc1, R.id.tc2, R.id.tc3, R.id.tc4, R.id.tc5)
        private val titles = intArrayOf(R.id.tt0, R.id.tt1, R.id.tt2, R.id.tt3, R.id.tt4, R.id.tt5)
        private val counts = intArrayOf(R.id.tn0, R.id.tn1, R.id.tn2, R.id.tn3, R.id.tn4, R.id.tn5)

        fun build(ctx: Context): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_tasks)
            val cur = Repo.currentTask()
            val list = Repo.visibleTasks().let { v -> if (cur == null) v else listOf(cur) + v.filter { it.id != cur.id } }
            val tl = cur?.let { Repo.list(it.listId) }
            val col = tl?.let { pint(it.color) } ?: pint(6)
            rv.setTextViewText(R.id.tw_count, list.size.toString())
            rv.setTextViewText(R.id.tw_list, tl?.name ?: "今天")
            rv.setTextColor(R.id.tw_list, col or (0xFF shl 24))
            rv.setOnClickPendingIntent(R.id.tw_count, Widgets.openApp(ctx))
            rv.setOnClickPendingIntent(R.id.tw_list, Widgets.openApp(ctx))
            for (i in rows.indices) {
                val t = list.getOrNull(i)
                if (t == null) { rv.setViewVisibility(rows[i], View.GONE); continue }
                rv.setViewVisibility(rows[i], View.VISIBLE)
                rv.setImageViewBitmap(circles[i], Widgets.hollowCircle(pint(Repo.list(t.listId).color)))
                rv.setTextViewText(titles[i], t.title)
                rv.setTextViewText(counts[i], if (t.subs.isEmpty()) "" else t.subs.size.toString())
                rv.setOnClickPendingIntent(circles[i], Widgets.pi(ctx, TaskWidget::class.java, ACTION_DONE, "task" to t.id))
            }
            return rv
        }
    }
}
