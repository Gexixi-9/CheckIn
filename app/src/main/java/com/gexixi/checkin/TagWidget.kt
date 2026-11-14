package com.gexixi.checkin

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

/** 桌面小组件：点标签开始/结束计时（最多显示前8个标签）。 */
class TagWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        Repo.init(context)
        ids.forEach { mgr.updateAppWidget(it, build(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            Repo.init(context)
            intent.getStringExtra("tag")?.let { Repo.toggleTag(it) }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.gexixi.checkin.TOGGLE"
        private val cells = intArrayOf(R.id.cell0, R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4, R.id.cell5, R.id.cell6, R.id.cell7)
        private val names = intArrayOf(R.id.name0, R.id.name1, R.id.name2, R.id.name3, R.id.name4, R.id.name5, R.id.name6, R.id.name7)
        private val chrs = intArrayOf(R.id.chr0, R.id.chr1, R.id.chr2, R.id.chr3, R.id.chr4, R.id.chr5, R.id.chr6, R.id.chr7)

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TagWidget::class.java))
            if (ids.isNotEmpty()) mgr.updateAppWidget(ids, build(ctx))
        }

        private fun build(ctx: Context): RemoteViews {
            val d = Repo.data
            val rv = RemoteViews(ctx.packageName, R.layout.widget_tags)
            for (i in cells.indices) {
                val tag = d.tags.getOrNull(i)
                if (tag == null) {
                    rv.setViewVisibility(cells[i], View.INVISIBLE)
                    continue
                }
                val run = d.sessions.firstOrNull { it.tagId == tag.id && it.end == null }
                val alpha = if (run != null) 0xFF else 0x88
                val color = (tag.color.toInt() and 0x00FFFFFF) or (alpha shl 24)
                rv.setViewVisibility(cells[i], View.VISIBLE)
                rv.setInt(cells[i], "setBackgroundColor", color)
                rv.setTextViewText(names[i], tag.emoji + "\n" + tag.name)
                if (run != null) {
                    val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - run.start)
                    rv.setViewVisibility(chrs[i], View.VISIBLE)
                    rv.setChronometer(chrs[i], base, null, true)
                } else {
                    rv.setChronometer(chrs[i], SystemClock.elapsedRealtime(), null, false)
                    rv.setViewVisibility(chrs[i], View.GONE)
                }
                val intent = Intent(ctx, TagWidget::class.java).setAction(ACTION_TOGGLE).putExtra("tag", tag.id)
                val pi = PendingIntent.getBroadcast(
                    ctx, tag.id.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                rv.setOnClickPendingIntent(cells[i], pi)
            }
            return rv
        }
    }
}
