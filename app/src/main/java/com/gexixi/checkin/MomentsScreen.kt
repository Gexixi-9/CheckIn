@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.gexixi.checkin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

/** 头像：照片优先，没有就是表情；f 为空表示「我」 */
@Composable
fun Avatar(f: Friend?, size: Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (f == null) C.ink else Color(0xFFE9E9EC)),
        contentAlignment = Alignment.Center
    ) {
        if (f == null) Text("我", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        else if (f.avatar.isNotBlank() && File(f.avatar).exists()) {
            AsyncImage(File(f.avatar), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else Text(f.emoji.ifBlank { "🙂" }, fontSize = (size.value * 0.55f).sp)
    }
}

private fun timeAgo(ts: Long): String {
    val m = (System.currentTimeMillis() - ts) / 60000
    return when {
        m < 1 -> "刚刚"
        m < 60 -> "" + m + " 分钟前"
        m < 60 * 24 -> "" + (m / 60) + " 小时前"
        else -> java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }
}

// ============================ 朋友圈 ============================
@Composable
fun MomentsScreen() {
    val ctx = LocalContext.current
    val d = Repo.data
    var composing by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<Pair<String, String>?>(null) }     // 动态 id → 要回复的好友 id
    var delPost by remember { mutableStateOf<String?>(null) }
    val posts = d.posts.sortedByDescending { it.ts }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            ChipFlow {
                RippleButton("＋ 发动态", { composing = true }, filled = true)
                RippleButton("🎲 让 TA 们发一条", { Moments.friendPostNow(ctx) })
                RippleButton("👥 角色（" + d.friends.size + "）", { managing = true })
            }
            if (Moments.status.isNotEmpty()) Text(Moments.status, fontSize = 12.sp, color = C.gray, modifier = Modifier.padding(top = 6.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            if (d.friends.isEmpty()) {
                item {
                    Text(
                        "还没有角色。点「角色」添加你的朋友（头像、名字、关系、人设），他们就会来给你的动态点赞、评论，也会自己发动态。",
                        fontSize = 13.sp, color = C.gray, modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
            items(posts, key = { it.id }) { p ->
                PostItem(p, onReply = { fid -> replyTo = p.id to fid }, onDelete = { delPost = p.id })
            }
            if (posts.isEmpty() && d.friends.isNotEmpty()) item { Text("还没有动态，发一条试试吧。", fontSize = 13.sp, color = C.gray, modifier = Modifier.padding(vertical = 16.dp)) }
        }
    }
    if (composing) ComposePostDialog { composing = false }
    if (managing) FriendsDialog { managing = false }
    replyTo?.let { (pid, fid) ->
        var text by remember(pid, fid) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { replyTo = null },
            title = { Text("回复 " + Moments.name(fid)) },
            text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2) },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) { Moments.reply(ctx, pid, fid, text); replyTo = null }
                }) { Text("发送", color = C.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { replyTo = null }) { Text("取消") } }
        )
    }
    delPost?.let { id ->
        AlertDialog(
            onDismissRequest = { delPost = null },
            title = { Text("删除这条动态？") },
            confirmButton = { TextButton(onClick = { Repo.deletePost(id); delPost = null }) { Text("删除", color = C.red) } },
            dismissButton = { TextButton(onClick = { delPost = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun PostItem(p: Post, onReply: (String) -> Unit, onDelete: () -> Unit) {
    val d = Repo.data
    val author = if (p.author == "me") null else d.friends.firstOrNull { it.id == p.author }
    val liked = "me" in p.likes
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Avatar(author, 42.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(Moments.name(p.author), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = C.ink)
            if (p.text.isNotBlank()) Text(p.text, fontSize = 15.sp, color = C.ink, modifier = Modifier.padding(top = 2.dp))
            if (p.photos.isNotEmpty()) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.photos.take(3).forEach { ph ->
                        AsyncImage(File(ph), null, Modifier.size(100.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    }
                }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(timeAgo(p.ts), fontSize = 12.sp, color = C.gray)
                Text("　删除", fontSize = 12.sp, color = C.gray, modifier = Modifier.clickable(onClick = onDelete))
                Spacer(Modifier.weight(1f))
                Text(if (liked) "♥" else "♡", fontSize = 20.sp, color = C.ink,
                    modifier = Modifier.clickable { Repo.setLike(p.id, "me", !liked) }.padding(horizontal = 10.dp))
                if (author != null) {
                    Text("💬", fontSize = 18.sp, modifier = Modifier.clickable { onReply(author.id) }.padding(horizontal = 6.dp))
                }
            }
            if (p.likes.isNotEmpty() || p.comments.isNotEmpty()) {
                Column(
                    Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF2F2F0)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (p.likes.isNotEmpty()) Text("♥ " + p.likes.joinToString("、") { Moments.name(it) }, fontSize = 13.sp, color = C.ink)
                    p.comments.forEach { c ->
                        val head = Moments.name(c.author) + (if (c.to.isNotEmpty() && c.to != p.author) " 回复 " + Moments.name(c.to) else "")
                        Text(
                            head + "：" + c.text, fontSize = 13.sp, color = C.ink,
                            modifier = if (c.author != "me") Modifier.clickable { onReply(c.author) } else Modifier
                        )
                    }
                    if (p.comments.any { it.author != "me" }) Text("点一条评论可以回复 TA", fontSize = 10.sp, color = C.gray)
                }
            }
        }
    }
}

/** 发动态：文字或图片都行；发出去后好友们会陆续点赞、评论 */
@Composable
private fun ComposePostDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf<List<String>>(emptyList()) }
    var camFile by remember { mutableStateOf<File?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) copyUri(ctx, uri, File(ctx.filesDir, "moments"), "m_")?.let { photos = (photos + it).take(3) }
    }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = camFile
        if (ok && f != null) photos = (photos + f.path).take(3) else f?.delete()
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("发动态") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("这一刻的想法…") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                if (photos.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        photos.forEach { ph ->
                            Box(Modifier.size(70.dp).clip(RoundedCornerShape(10.dp)).clickable { photos = photos - ph }) {
                                AsyncImage(File(ph), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                    Text("点图片可以移除（最多 3 张）", fontSize = 11.sp, color = C.gray)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RippleButton("📷 拍照", {
                        val dir = File(ctx.filesDir, "moments")
                        dir.mkdirs()
                        val f = File(dir, "m_" + uid() + ".jpg")
                        camFile = f
                        cam.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".fp", f))
                    })
                    RippleButton("🖼 相册", { pick.launch("image/*") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank() || photos.isNotEmpty()) { Moments.postMine(ctx, text, photos); onClose() }
            }) { Text("发表", color = C.accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } }
    )
}

/** 角色列表：添加 / 修改 / 删除好友 */
@Composable
private fun FriendsDialog(onClose: () -> Unit) {
    val d = Repo.data
    var editing by remember { mutableStateOf<Friend?>(null) }
    var adding by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("朋友圈里的角色") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.friends.isEmpty()) Text("还没有角色，点下面添加一个。", fontSize = 13.sp, color = C.gray)
                d.friends.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF4F4F2)).clickable { editing = f }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(f, 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontWeight = FontWeight.Bold, color = C.ink)
                            Text(f.relation.ifBlank { "朋友" }, fontSize = 12.sp, color = C.gray)
                        }
                        Text("编辑", fontSize = 12.sp, color = C.gray)
                    }
                }
                RippleButton("＋ 添加角色", { adding = true }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("完成", color = C.accent) } }
    )
    if (adding) FriendDialog(null) { adding = false }
    editing?.let { f -> FriendDialog(f) { editing = null } }
}

@Composable
private fun FriendDialog(initial: Friend?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🙂") }
    var avatar by remember { mutableStateOf(initial?.avatar ?: "") }
    var relation by remember { mutableStateOf(initial?.relation ?: "") }
    var persona by remember { mutableStateOf(initial?.persona ?: "") }
    var confirmDel by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) copyUri(ctx, uri, File(ctx.filesDir, "friends"), "f_")?.let { avatar = it }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (initial == null) "添加角色" else "编辑角色") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(Friend(name = name, emoji = emoji, avatar = avatar), 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RippleButton("选头像照片", { pick.launch("image/*") })
                        if (avatar.isNotBlank()) RippleButton("不用照片", { avatar = "" })
                    }
                }
                OutlinedTextField(name, { name = it.take(12) }, label = { ReqLabel("名字") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("没有照片时用的表情头像") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(relation, { relation = it }, label = { Text("和你的关系（比如：大学室友、妈妈、同事）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    persona, { persona = it }, label = { Text("人设（性格、说话方式、爱好，写得越具体越像）") },
                    minLines = 3, modifier = Modifier.fillMaxWidth()
                )
                if (initial != null) TextButton(onClick = { confirmDel = true }) { Text("删除这个角色（他的动态和评论也会删）", color = C.red) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val f = (initial ?: Friend()).copy(name = name.trim(), emoji = emoji.ifBlank { "🙂" }, avatar = avatar, relation = relation.trim(), persona = persona.trim())
                    if (initial == null) Repo.addFriend(f) else Repo.updateFriend(f)
                    onClose()
                }
            }) { Text("保存", color = C.accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } }
    )
    if (confirmDel && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDel = false },
            title = { Text("删除「" + initial.name + "」？") },
            confirmButton = { TextButton(onClick = { Repo.deleteFriend(initial.id); confirmDel = false; onClose() }) { Text("删除", color = C.red) } },
            dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("取消") } }
        )
    }
}
