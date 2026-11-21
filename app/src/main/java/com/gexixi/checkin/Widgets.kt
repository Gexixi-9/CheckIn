package com.gexixi.checkin

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
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
            mgr.getAppWidgetIds(ComponentName(ctx, DayWidget::class.java)).let {
                if (it.isNotEmpty()) mgr.updateAppWidget(it, DayWidget.build(ctx))
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

    fun togglePi(ctx: Context, tagId: String): PendingIntent {
        val i = Intent(ctx, ToggleActivity::class.java).putExtra("tag", tagId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return PendingIntent.getActivity(ctx, ("tg" + tagId).hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun settlePi(ctx: Context, taskId: String): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).putExtra("settle", taskId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(ctx, ("settle" + taskId).hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
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
            val groupNames = d.groups.map { it.name }
            for (i in gids.indices) {
                val g = groupNames.getOrNull(i)
                if (g == null) { rv.setViewVisibility(gids[i], View.GONE); continue }
                rv.setViewVisibility(gids[i], View.VISIBLE)
                val sel = g == d.widgetGroup
                rv.setTextViewText(gids[i], g)
                rv.setTextColor(gids[i], if (sel) 0xFF1E1E1E.toInt() else 0xFF8E9093.toInt())
                rv.setTextViewTextSize(gids[i], TypedValue.COMPLEX_UNIT_SP, if (sel) 15f else 12f)
                rv.setOnClickPendingIntent(gids[i], Widgets.pi(ctx, TagWidget::class.java, ACTION_GROUP, "group" to g))
            }
            val tags = d.tags.filter { it.group == d.widgetGroup && !it.hidden() }.take(9)
            for (i in cells.indices) {
                val tag = tags.getOrNull(i)
                if (tag == null) { rv.setViewVisibility(cells[i], View.INVISIBLE); continue }
                val run = d.sessions.firstOrNull { it.tagId == tag.id && it.end == null && !it.auto }
                rv.setViewVisibility(cells[i], View.VISIBLE)
                rv.setImageViewBitmap(imgs[i], Widgets.circleBitmap(tag.emoji, pint(tag.color), run != null))
                rv.setTextViewText(names[i], tag.name)
                if (run != null) {
                    // 正向计时：从开始那一刻往上数。base 不能晚于现在，否则会显示成负数往 0 走（像倒数）
                    val elapsed = (System.currentTimeMillis() - run.start).coerceAtLeast(0L)
                    val base = SystemClock.elapsedRealtime() - elapsed
                    rv.setViewVisibility(chrs[i], View.VISIBLE)
                    rv.setChronometerCountDown(chrs[i], false)
                    rv.setChronometer(chrs[i], base, null, true)
                } else {
                    rv.setChronometer(chrs[i], SystemClock.elapsedRealtime(), null, false)
                    rv.setViewVisibility(chrs[i], View.GONE)
                }
                // 结束后要跳 Notion / repov 的标签，点击走一个透明中转页（广播接收器没法直接打开别的 App）
                val jump = notionKeyFor(tag.id).isNotEmpty() || tag.repov
                rv.setOnClickPendingIntent(
                    cells[i],
                    if (jump) Widgets.togglePi(ctx, tag.id) else Widgets.pi(ctx, TagWidget::class.java, ACTION_TOGGLE, "tag" to tag.id)
                )
            }
            return rv
        }
    }
}

/**
 * 每日日历小组件（替代原来的今日总结）：把当天的时间轴画成一张图，色块、所思所感、照片都在上面；
 * 同时发生的左右错开，和 App 里的日历是同一套算法。顶部可以往前后翻天，点日期回到今天，点图片打开 App。
 */
class DayWidget : AppWidgetProvider() {
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
            ACTION_TODAY -> Repo.resetSummary()
        }
    }

    companion object {
        const val ACTION_PREV = "com.gexixi.checkin.DAY_PREV"
        const val ACTION_NEXT = "com.gexixi.checkin.DAY_NEXT"
        const val ACTION_TODAY = "com.gexixi.checkin.DAY_TODAY"

        fun build(ctx: Context): RemoteViews {
            val d = Repo.data
            val date = LocalDate.now().plusDays(d.sumOffset.toLong())
            val rv = RemoteViews(ctx.packageName, R.layout.widget_day)
            val bmp = try {
                bitmap(d, date)
            } catch (e: Throwable) {
                errorBitmap("每日日历画不出来：" + (e.message ?: e.javaClass.simpleName))
            }
            rv.setImageViewBitmap(R.id.day_img, bmp)
            rv.setOnClickPendingIntent(R.id.day_img, Widgets.openApp(ctx))
            rv.setOnClickPendingIntent(R.id.day_prev, Widgets.pi(ctx, DayWidget::class.java, ACTION_PREV))
            rv.setOnClickPendingIntent(R.id.day_next, Widgets.pi(ctx, DayWidget::class.java, ACTION_NEXT))
            rv.setOnClickPendingIntent(R.id.day_title, Widgets.pi(ctx, DayWidget::class.java, ACTION_TODAY))
            return rv
        }

        private fun errorBitmap(msg: String): Bitmap {
            val b = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8E9093.toInt(); textSize = 28f }
            c.drawText(TextUtils.ellipsize(msg, p, 560f, TextUtils.TruncateAt.END).toString(), 20f, 150f, p)
            return b
        }

        fun bitmap(d: Data, date: LocalDate): Bitmap {
            val W = 900
            val H = 1100
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val ds = Repo.dayStart(date)
            val now = System.currentTimeMillis()
            val evs = dayEvents(d, date, now)
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E1E1E.toInt(); typeface = Typeface.DEFAULT_BOLD }
            val gray = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8E9093.toInt(); textSize = 22f }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE4E4DE.toInt(); strokeWidth = 2f }

            // 标题：日期 + 天气
            ink.textAlign = Paint.Align.CENTER
            ink.textSize = 36f
            val wx = d.weather[date.toString()] ?: ""
            c.drawText("" + date.monthValue + "月" + date.dayOfMonth + "日  周" + "一二三四五六日"[date.dayOfWeek.value - 1] + (if (wx.isEmpty()) "" else "  " + wx), W / 2f, 56f, ink)

            // 时间范围：有记录就贴着记录，至少 8 小时
            var startH = 6
            var endH = 22
            if (evs.isNotEmpty()) {
                startH = (((evs.minOf { it.st } - ds) / 3600000L).toInt() - 1).coerceIn(0, 16)
                endH = (((evs.maxOf { it.en } - ds) / 3600000L).toInt() + 2).coerceIn(startH + 8, 24)
                if (endH - startH < 8) startH = (endH - 8).coerceAtLeast(0)
            }
            val top = 84f
            val pxH = (H - top - 12f) / (endH - startH)
            evs.forEach { it.wantMs = (wantDp(it) * 2.6f / pxH * 3600000f).toLong() }   // 有文字/照片/录音的块撑高（dp→px 约 2.6 倍）
            assignLanes(evs, (56f / pxH * 3600000f).toLong())     // 块最矮 56px，上下相邻不压
            val left = 64f
            val w = W - left - 14f
            gray.textAlign = Paint.Align.LEFT
            for (h in startH..endH) {
                val y = top + (h - startH) * pxH
                c.drawLine(left, y, W - 8f, y, line)
                if (h < 24) c.drawText("%02d".format(h), 10f, y + 8f, gray)
            }
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E1E1E.toInt(); textSize = 26f; typeface = Typeface.DEFAULT_BOLD }
            val sp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x991E1E1E.toInt(); textSize = 21f }
            val np = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC1E1E1E.toInt(); textSize = 22f }
            val thumb = 52f
            evs.forEach { e ->
                val y0 = top + ((e.st - ds) / 3600000f - startH) * pxH + 3f
                val y1 = top + ((e.vEn - ds) / 3600000f - startH) * pxH - 3f
                val lw = w / e.lanes
                val x0 = left + e.lane * lw
                val x1 = x0 + lw - 12f                     // 块与块之间留缝，和 App 里一致
                val col = pint(e.tag.color)
                if (e.isTask) {
                    // 有具体时间的待办：白底圆角 + 空心圆
                    fill.style = Paint.Style.FILL
                    fill.color = 0xFFFFFFFF.toInt()
                    c.drawRoundRect(RectF(x0, y0, x1, y1), 14f, 14f, fill)
                    fill.style = Paint.Style.STROKE
                    fill.strokeWidth = 3f
                    fill.color = (col and 0x00FFFFFF) or (0x99 shl 24)
                    c.drawRoundRect(RectF(x0, y0, x1, y1), 14f, 14f, fill)
                    fill.strokeWidth = 4f
                    val cy = (y0 + y1) / 2f
                    c.drawCircle(x0 + 28f, cy, 12f, fill)
                    fill.style = Paint.Style.FILL
                    val tw0 = x1 - x0 - 60f
                    c.drawText(TextUtils.ellipsize(e.name, tp, tw0, TextUtils.TruncateAt.END).toString(), x0 + 52f, cy + 9f, tp)
                    return@forEach
                }
                // 时间段色块（参照参考图）：圆角浅色卡片 + 缩进的圆头竖条（不贴边）
                // 从上到下：照片 → 名称 → 小钟 + 时间范围 · 时长 → 录音条 → 文字
                fill.style = Paint.Style.FILL
                fill.color = (col and 0x00FFFFFF) or (0x33 shl 24)
                c.drawRoundRect(RectF(x0, y0, x1, y1), 16f, 16f, fill)
                fill.style = Paint.Style.STROKE
                fill.strokeWidth = 2f
                fill.color = (col and 0x00FFFFFF) or (0x38 shl 24)
                c.drawRoundRect(RectF(x0, y0, x1, y1), 16f, 16f, fill)
                fill.style = Paint.Style.FILL
                fill.color = col or (0xFF shl 24)
                val h = y1 - y0
                val padY = if (h < 80f) 8f else 14f
                c.drawRoundRect(RectF(x0 + 12f, y0 + padY, x0 + 18f, y1 - padY), 3f, 3f, fill)
                val tx = x0 + 32f
                val tw = x1 - tx - 12f
                var cy = y0 + padY
                val isMoment = e.tag.id == "x_note"
                val compact = h < 90f
                // 照片：统一小尺寸圆角方块，最上面
                if (e.s.photos.isNotEmpty() && cy + thumb + 6f <= y1 - padY) {
                    var px = tx
                    e.s.photos.take(3).forEach { p ->
                        val th = decodeThumb(p, 160)
                        if (th != null) {
                            val side = minOf(th.width, th.height)
                            val src = android.graphics.Rect((th.width - side) / 2, (th.height - side) / 2, (th.width + side) / 2, (th.height + side) / 2)
                            val dst = RectF(px, cy, px + thumb, cy + thumb)
                            val path = android.graphics.Path()
                            path.addRoundRect(dst, 12f, 12f, android.graphics.Path.Direction.CW)
                            c.save()
                            c.clipPath(path)
                            c.drawBitmap(th, src, dst, null)
                            c.restore()
                            px += thumb + 8f
                        }
                    }
                    cy += thumb + 10f
                }
                // 名称（块矮时把时长接在后面）；即时记录不显示名称
                if (!isMoment && cy + 26f <= y1) {
                    val head = e.emoji + " " + e.name + (if (e.s.audio.isNotBlank()) " 🎙" else "") + (if (compact) " · " + fmtMin(e.en - e.st) else "")
                    c.drawText(TextUtils.ellipsize(head, tp, tw, TextUtils.TruncateAt.END).toString(), tx, cy + 22f, tp)
                    cy += 34f
                }
                // 时间行：小钟 + 范围 · 时长
                if (!isMoment && !compact && cy + 24f <= y1) {
                    fill.style = Paint.Style.STROKE
                    fill.strokeWidth = 2.4f
                    fill.color = 0x881E1E1E.toInt()
                    c.drawCircle(tx + 8f, cy + 11f, 7f, fill)
                    c.drawLine(tx + 8f, cy + 11f, tx + 8f, cy + 6f, fill)
                    c.drawLine(tx + 8f, cy + 11f, tx + 12f, cy + 13f, fill)
                    fill.style = Paint.Style.FILL
                    val tr = hm(e.st) + " - " + (if (e.s.end == null) "进行中" else hm(e.en)) + " · " + fmtMin(e.en - e.st)
                    c.drawText(TextUtils.ellipsize(tr, sp, tw - 26f, TextUtils.TruncateAt.END).toString(), tx + 26f, cy + 18f, sp)
                    cy += 30f
                }
                // 录音：小圆 + 三角形 + 一排细竖条
                if (e.s.audio.isNotBlank() && cy + 44f <= y1) {
                    fill.style = Paint.Style.FILL
                    fill.color = 0xFF1E1E1E.toInt()
                    c.drawCircle(tx + 18f, cy + 22f, 18f, fill)
                    fill.color = 0xFFFFFFFF.toInt()
                    val tri = android.graphics.Path()
                    tri.moveTo(tx + 12f, cy + 12f); tri.lineTo(tx + 12f, cy + 32f); tri.lineTo(tx + 29f, cy + 22f); tri.close()
                    c.drawPath(tri, fill)
                    fill.color = 0x661E1E1E
                    val rnd = java.util.Random(e.s.audio.hashCode().toLong())
                    var bx = tx + 48f
                    while (bx < tx + tw - 6f) {
                        val bh2 = 8f + rnd.nextFloat() * 26f
                        c.drawRoundRect(RectF(bx, cy + 22f - bh2 / 2f, bx + 5f, cy + 22f + bh2 / 2f), 3f, 3f, fill)
                        bx += 9f
                    }
                    cy += 48f
                }
                // 文字
                if (e.s.note.isNotBlank()) {
                    var rest = e.s.note.replace("\n", " ")
                    while (rest.isNotEmpty() && cy + 26f <= y1 - padY + 4f) {
                        val cnt = np.breakText(rest, true, tw, null).coerceAtLeast(1)
                        c.drawText(rest.substring(0, cnt), tx, cy + 20f, np)
                        rest = rest.substring(cnt)
                        cy += 28f
                    }
                }
            }
            if (date == LocalDate.now()) {
                val y = top + ((now - ds) / 3600000f - startH) * pxH
                if (y in top..(H - 4f)) {
                    fill.style = Paint.Style.FILL
                    // 细细的半透明线 + 一个小点，不挡视线
                    fill.color = 0x551E1E1E
                    c.drawRect(left, y - 0.75f, W - 8f, y + 0.75f, fill)
                    fill.color = 0x881E1E1E.toInt()
                    c.drawCircle(left, y, 5f, fill)
                }
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
        // 完成要先对照达成条件、写感想，所以点圆圈改成打开 App 的结算窗（见 MainActivity）
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
                rv.setOnClickPendingIntent(circles[i], Widgets.settlePi(ctx, t.id))
            }
            return rv
        }
    }
}
