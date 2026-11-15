package com.gexixi.checkin

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process

object Tracker {
    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 有手动计时在进行，或开了自动记录，就保证后台服务在跑（它负责通知栏常驻和自动记录） */
    fun ensure(ctx: Context) {
        val need = Repo.data.prefs.autoOn || Repo.running().isNotEmpty()
        if (!need) return
        try {
            ctx.startForegroundService(Intent(ctx, TrackerService::class.java))
        } catch (_: Exception) {
            // 某些情况下系统不允许后台启动前台服务，下次打开 App 会再试
        }
    }
}

class TrackerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var fgPkg: String? = null       // 当前真正在前台的 App
    private var fgSince = 0L
    private var autoPkg: String? = null     // 正在自动记录的 App
    private var autoStart = 0L
    private var lastQuery = 0L
    private var lastWidget = 0L
    private var lastNotif = 0L
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Repo.init(this)
        if (intent?.action == ACTION_STOP_ALL) Repo.stopAllManual()
        createChannel()
        try { startForeground(NOTIF_ID, buildNotification()) } catch (_: Exception) {}
        if (!started) { started = true; handler.post(tick) }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        endAuto()
        super.onDestroy()
    }

    private val tick = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, 2000)
        }
    }

    private fun step() {
        val p = Repo.data.prefs
        val now = System.currentTimeMillis()
        if (p.autoOn && Tracker.hasUsageAccess(this)) poll(now) else if (autoPkg != null) endAuto()
        if (now - lastNotif > 10000) { lastNotif = now; updateNotification() }
        if (now - lastWidget > 60000) { lastWidget = now; Widgets.refreshAll(this) }
        if (!p.autoOn && Repo.running().isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** 每隔一两秒看一眼前台是哪个 App，在对应表里就自动计时，切走就结束 */
    @Suppress("DEPRECATION")
    private fun poll(now: Long) {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val from = if (lastQuery == 0L) now - 60000 else lastQuery
        lastQuery = now
        try {
            val events = usm.queryEvents(from, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    fgPkg = e.packageName
                    fgSince = e.timeStamp
                } else if (e.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND && e.packageName == fgPkg) {
                    fgPkg = null
                }
            }
        } catch (_: Exception) {}
        val mapped = Repo.data.prefs.autoMap.firstOrNull { it.pkg == fgPkg && fgPkg != packageName }
        if (mapped == null || mapped.pkg != autoPkg) {
            endAuto()
            if (mapped != null) { autoPkg = mapped.pkg; autoStart = if (fgSince > 0) fgSince else now }
        }
    }

    /** 结束自动记录；太短的切换（默认不到10秒）不记，避免一堆碎片。自动记录不发小红花、不播语音。 */
    private fun endAuto() {
        val pkg = autoPkg ?: return
        autoPkg = null
        val p = Repo.data.prefs
        val end = System.currentTimeMillis()
        if (end - autoStart < p.minSec * 1000L) return
        val tagId = p.autoMap.firstOrNull { it.pkg == pkg }?.tagId ?: return
        Repo.addSession(Session(uid(), tagId, autoStart, end, auto = true))
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "计时进行中", NotificationManager.IMPORTANCE_LOW))
    }

    private fun buildNotification(): Notification {
        val now = System.currentTimeMillis()
        val running = Repo.running().filter { !it.auto }
        val names = running.mapNotNull { s ->
            Repo.tag(s.tagId)?.let { it.emoji + it.name + " " + ((now - s.start) / 60000) + "分" }
        }.toMutableList()
        autoPkg?.let { pkg ->
            val t = Repo.data.prefs.autoMap.firstOrNull { it.pkg == pkg }?.let { Repo.tag(it.tagId) }
            if (t != null) names.add(t.emoji + t.name + "（自动）")
        }
        val title = if (names.isEmpty()) "当下" else "正在进行 " + names.size + " 项"
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TrackerService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(if (names.isEmpty()) "自动记录已开启" else names.joinToString("　"))
            .setContentIntent(open)
            .setOngoing(true)
        if (running.isNotEmpty()) b.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "全部结束", stop).build())
        return b.build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try { nm.notify(NOTIF_ID, buildNotification()) } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL = "tracking"
        const val NOTIF_ID = 7
        const val ACTION_STOP_ALL = "com.gexixi.checkin.STOP_ALL"
    }
}
