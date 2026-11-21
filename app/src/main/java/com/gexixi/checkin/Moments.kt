package com.gexixi.checkin

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 朋友圈引擎：用户发动态 → 好友们（AI 扮演）点赞、评论；用户回复 → 对应好友回复；
 * 点一下按钮 → 随机一个好友发一条纯文字动态。收到点赞/回复会发手机通知（头像 + 名字 + 内容）。
 * 回应会错开几秒陆续到，像真的有人在刷。
 */
object Moments {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private const val CHANNEL = "moments"

    /** 页面顶部的小提示，比如「TA 们正在看…」 */
    var status by mutableStateOf("")

    private fun toast(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }

    fun name(id: String): String = if (id == "me") "我" else Repo.data.friends.firstOrNull { it.id == id }?.name ?: "（已删除的好友）"

    private fun preview(text: String): String = if (text.isBlank()) "[图片]" else if (text.length > 24) text.take(24) + "…" else text

    // ---------------- 发动态 ----------------
    fun postMine(ctx: Context, text: String, photos: List<String>) {
        val post = Post(author = "me", text = text.trim(), photos = photos, ts = System.currentTimeMillis())
        Repo.addPost(post)
        react(ctx, post, "用户本人")
    }

    /** 点一下，随机一个好友发一条动态（只有文字），其他好友再来点赞评论 */
    fun friendPostNow(ctx: Context) {
        val fs = Repo.data.friends
        if (fs.isEmpty()) { toast(ctx, "先去「角色」里添加几个好友"); return }
        val f = fs.random()
        status = f.name + " 正在想发什么…"
        scope.launch {
            try {
                val others = Repo.data.posts.sortedByDescending { it.ts }.take(5).joinToString("\n") { it.text }
                val text = Ai.friendPost(Repo.data.prefs, f, others)
                if (text.isNotBlank()) {
                    val post = Post(author = f.id, text = text, ts = System.currentTimeMillis())
                    Repo.addPost(post)
                    status = ""
                    react(ctx, post, f.name + "（用户的" + f.relation.ifBlank { "朋友" } + "）", fromFriend = f.id)
                } else status = ""
            } catch (e: Exception) {
                status = ""
                toast(ctx, "发动态失败：" + (e.message ?: ""))
            }
        }
    }

    // ---------------- 点赞评论 ----------------
    private fun react(ctx: Context, post: Post, authorDesc: String, fromFriend: String = "") {
        val others = Repo.data.friends.filter { it.id != fromFriend }
        if (others.isEmpty()) return
        status = "TA 们正在看…"
        scope.launch {
            try {
                val img = post.photos.firstOrNull()?.let { path ->
                    try { thumbToBase64(path) } catch (_: Exception) { null }
                }
                val rs = Ai.momentReactions(Repo.data.prefs, others, authorDesc, post.text, img)
                status = ""
                val mine = post.author == "me"
                for (r in rs.shuffled()) {
                    delay((2000L..9000L).random())
                    val f = Repo.data.friends.firstOrNull { it.id == r.friend } ?: continue
                    if (Repo.data.posts.none { it.id == post.id }) break      // 动态已经被删了
                    if (r.like) {
                        Repo.setLike(post.id, f.id, true)
                        if (mine) notify(ctx, f, "赞了你的朋友圈：" + preview(post.text))
                    }
                    if (r.comment.isNotBlank()) {
                        if (r.like) delay((1500L..4000L).random())
                        Repo.addComment(post.id, MComment(author = f.id, text = r.comment, ts = System.currentTimeMillis(), to = if (fromFriend.isNotEmpty()) fromFriend else ""))
                        if (mine) notify(ctx, f, "评论了你：" + r.comment)
                    }
                }
            } catch (e: Exception) {
                status = ""
                toast(ctx, "好友们没反应过来：" + (e.message ?: ""))
            }
        }
    }

    /** 用户在 post 下回复了好友 toFriend（评论或动态作者），这个好友用自己的口吻回一句 */
    fun reply(ctx: Context, postId: String, toFriend: String, text: String) {
        val post = Repo.data.posts.firstOrNull { it.id == postId } ?: return
        val f = Repo.data.friends.firstOrNull { it.id == toFriend } ?: return
        Repo.addComment(postId, MComment(author = "me", text = text.trim(), ts = System.currentTimeMillis(), to = toFriend))
        status = f.name + " 正在回复…"
        scope.launch {
            try {
                val thread = post.comments.takeLast(6).joinToString("\n") { name(it.author) + "：" + it.text }
                val reply = Ai.momentReply(Repo.data.prefs, f, post.text, thread, text)
                status = ""
                if (reply.isNotBlank()) {
                    delay((2500L..8000L).random())
                    Repo.addComment(postId, MComment(author = f.id, text = reply, ts = System.currentTimeMillis(), to = "me"))
                    notify(ctx, f, "回复了你：" + reply)
                }
            } catch (e: Exception) {
                status = ""
                toast(ctx, f.name + " 没回上：" + (e.message ?: ""))
            }
        }
    }

    // ---------------- 通知：头像 + 名字 + 具体内容 ----------------
    fun notify(ctx: Context, f: Friend, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "朋友圈消息", NotificationManager.IMPORTANCE_DEFAULT))
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(f.name)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setLargeIcon(avatarBitmap(f, 128))
            .setAutoCancel(true)
            .setContentIntent(Widgets.openApp(ctx))
        nm.notify((System.currentTimeMillis() % 100000).toInt() + 1000, b.build())
    }

    private fun thumbToBase64(path: String): String? {
        val bmp = decodeThumb(path, 900) ?: return null
        return bitmapToBase64(bmp)
    }
}

/** 头像：有照片用照片（圆形裁切），没有就用表情画一个浅灰圆 */
fun avatarBitmap(f: Friend, px: Int): Bitmap {
    val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val src = if (f.avatar.isNotBlank() && File(f.avatar).exists()) decodeThumb(f.avatar, px) else null
    if (src != null) {
        val side = minOf(src.width, src.height)
        val path = Path()
        path.addCircle(px / 2f, px / 2f, px / 2f, Path.Direction.CW)
        c.clipPath(path)
        c.drawBitmap(
            src, Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2),
            Rect(0, 0, px, px), null
        )
    } else {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFE9E9EC.toInt()
        c.drawCircle(px / 2f, px / 2f, px / 2f, p)
        p.color = 0xFF1E1E1E.toInt()
        p.textSize = px * 0.52f
        p.textAlign = Paint.Align.CENTER
        c.drawText(f.emoji.ifBlank { "🙂" }, px / 2f, px / 2f + px * 0.18f, p)
    }
    return out
}
