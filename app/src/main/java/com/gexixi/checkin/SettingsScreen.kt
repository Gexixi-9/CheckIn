@file:OptIn(ExperimentalMaterial3Api::class)

package com.gexixi.checkin

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(10.dp))
    Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = C.ink)
}

@Composable
private fun TextRow(label: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
    )
}

@Composable
private fun NumRow(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value.toString(), { v -> onChange(v.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0) },
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ElasticSwitch(checked, onChange)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MapRow(title: String, values: List<Int>, onChange: (List<Int>) -> Unit) {
    Text(title, fontSize = 13.sp, color = C.gray)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEachIndexed { i, v ->
            OutlinedTextField(
                v.toString(), { s -> onChange(values.mapIndexed { k, old -> if (k == i) (s.filter { it.isDigit() }.take(3).toIntOrNull() ?: 0) else old }) },
                label = { Text((i + 1).toString() + "★") }, singleLine = true, modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Body(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
        Spacer(Modifier.height(40.dp))
    }
}

/** 设置页：同样是便当格，每个格子点开是这一项的详细设置 */
@Composable
fun SettingsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = Repo.data.prefs
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            try { Repo.exportBackup(ctx, uri); Toast.makeText(ctx, "已导出备份", Toast.LENGTH_SHORT).show() }
            catch (e: Exception) { Toast.makeText(ctx, "导出失败：" + e.message, Toast.LENGTH_LONG).show() }
        }
    }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val importPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }
    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入这个备份？") },
            text = { Text("会用备份里的内容覆盖现在手机上的全部数据和设置（包括 API 密钥）。建议先导出一份现在的。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    try {
                        val n = Repo.importBackup(ctx, uri)
                        Toast.makeText(ctx, "已恢复（" + n + " 个文件）", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "导入失败：" + (e.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }) { Text("覆盖导入", color = C.red) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("取消") } }
        )
    }
    BentoPage(header = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White).bounceClick(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("‹", fontSize = 28.sp, color = C.ink)
            }
            Spacer(Modifier.width(12.dp))
            Text("设置", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.ink)
        }
    }) { cell ->
        BentoRow {
            Bento(Modifier.size(cell), color = soft(8, 0.3f), title = "鼓励音频", detail = { AudioSettings() }) {
                SettingCardText("🎧", "鼓励音频", if (p.character == "random") "四个角色随机" else Media.chars.firstOrNull { it.first == p.character }?.second ?: "")
            }
            Bento(Modifier.size(cell), color = soft(1, 1f), title = "金币", detail = { CoinSettings() }) {
                SettingCardText(p.coin.ifBlank { "🪙" }, "金币", "难度 → 金币、上限")
            }
        }
        Bento(Modifier.fillMaxWidth().height(cell), color = soft(9, 0.3f), title = "AI 拆分 / 结算", detail = { AiSettings() }) {
            SettingCardText("✨", "AI 拆分 / 结算（DeepSeek）", if (p.dsKey.isBlank()) "还没填 API Key" else "已填密钥 · 结算" + (if (p.settleAi) "开" else "关"))
        }
        BentoRow {
            Bento(Modifier.size(cell), color = soft(6, 0.25f), title = "语音播报", detail = { VoiceSettings() }) {
                SettingCardText("🔊", "语音播报", if (p.ttsKey.isBlank()) "用手机自带朗读" else "硅基流动已填")
            }
            Bento(Modifier.size(cell), color = soft(4, 0.35f), title = "自动记录", detail = { AutoSettings() }) {
                SettingCardText("📲", "自动记录", (if (p.autoOn) "已开" else "已关") + " · 少于" + p.minMin + "分钟不记")
            }
        }
        BentoRow {
            Bento(Modifier.size(cell), color = Color.White, title = "Notion 联动", detail = { NotionSettings() }) {
                SettingCardText("🗒️", "Notion", "播客 / 购物 / 阅读 / 小说")
            }
            Bento(Modifier.size(cell), color = soft(10, 0.3f), title = "防止被杀后台", detail = { KeepAliveSettings() }) {
                SettingCardText("🛡️", "后台保活", "小米 / OPPO / vivo / 华为")
            }
        }
        BentoRow {
            Bento(Modifier.size(cell), color = soft(2, 0.3f), title = "备份", detail = {
                Body {
                    Text("打成一个压缩包，包含：全部数据、日记、记录里的照片和录音、商品图片、人物照片，还有设置里填的所有东西——AI 和语音的 API 密钥、自动记录的 App 对应表、Notion 链接、金币设置。", fontSize = 13.sp, color = C.ink)
                    Text("注意：压缩包里有 API 密钥，别发给别人。", fontSize = 12.sp, color = C.gray)
                    RippleButton("导出备份", { export.launch("dangxia-backup.zip") }, filled = true)
                    RippleButton("导入备份（换手机 / 重装后用）", { importPick.launch(arrayOf("application/zip", "application/octet-stream")) })
                }
            }) { SettingCardText("💾", "备份", "导出压缩包") }
            Spacer(Modifier.size(cell))
        }
    }
}

@Composable
private fun BoxScope.SettingCardText(icon: String, title: String, sub: String) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(icon, fontSize = 30.sp)
        Spacer(Modifier.weight(1f))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = C.ink)
        Text(sub, fontSize = 11.sp, color = C.gray, maxLines = 2)
    }
}

@Composable
private fun AudioSettings() {
    val ctx = LocalContext.current
    val p = Repo.data.prefs
    Body {
        Section("完成任务时的鼓励音频")
        ChipFlow {
            (listOf("random" to "随机") + Media.chars).forEach { (k, n) ->
                FilterChip(selected = p.character == k, onClick = { Repo.setPrefs { it.copy(character = k) } }, label = { Text(n) })
            }
        }
        RippleButton("▶ 试听", { Media.cheer(ctx, p.character) })
        Text("只播放你从原 App 下载的这批音频，不用语音生成。", fontSize = 12.sp, color = C.gray)
    }
}

@Composable
private fun CoinSettings() {
    val p = Repo.data.prefs
    Body {
        Section("金币")
        TextRow("金币图标（可换成任何字符或 emoji）", p.coin) { v -> Repo.setPrefs { it.copy(coin = v.take(4)) } }
        MapRow("任务难度 → 默认金币数", p.taskStarFlowers) { v -> Repo.setPrefs { it.copy(taskStarFlowers = v) } }
        MapRow("商品想要程度 → 默认标价", p.wishStarFlowers) { v -> Repo.setPrefs { it.copy(wishStarFlowers = v) } }
        NumRow("单任务奖励上限（AI 给的偏高就按上限算，0=不限）", p.rewardCap) { v -> Repo.setPrefs { it.copy(rewardCap = v) } }
        NumRow("每天兑换商品上限（0=不限）", p.dailyLimit) { v -> Repo.setPrefs { it.copy(dailyLimit = v) } }
        NumRow("每天金币目标（进度条用）", p.dailyGoal) { v -> Repo.setPrefs { it.copy(dailyGoal = v.coerceAtLeast(1)) } }
    }
}

@Composable
private fun AiSettings() {
    val p = Repo.data.prefs
    Body {
        Section("AI 拆分任务（DeepSeek）")
        Text("密钥只存在手机本地，不会写进代码、不会上传 GitHub。", fontSize = 12.sp, color = C.gray)
        TextRow("接口地址", p.dsBase) { v -> Repo.setPrefs { it.copy(dsBase = v.trim()) } }
        TextRow("API Key", p.dsKey, true) { v -> Repo.setPrefs { it.copy(dsKey = v.trim()) } }
        TextRow("文字模型", p.dsModel) { v -> Repo.setPrefs { it.copy(dsModel = v.trim()) } }
        TextRow("拍照用的视觉模型（实验性，可能会改名，所以可以自己改）", p.dsVision) { v -> Repo.setPrefs { it.copy(dsVision = v.trim()) } }
        SwitchRow("完成任务时让 AI 结算金币（对照达成条件和你写的感想；关掉就按预设发）", p.settleAi) { v -> Repo.setPrefs { it.copy(settleAi = v) } }
    }
}

@Composable
private fun VoiceSettings() {
    val ctx = LocalContext.current
    val p = Repo.data.prefs
    Body {
        Section("任务发放语音播报（硅基流动）")
        SwitchRow("任务成为当前任务时播报（没填密钥/没网就用手机自带朗读）", p.announceOn) { v -> Repo.setPrefs { it.copy(announceOn = v) } }
        TextRow("接口地址", p.ttsUrl) { v -> Repo.setPrefs { it.copy(ttsUrl = v.trim()) } }
        TextRow("API Key", p.ttsKey, true) { v -> Repo.setPrefs { it.copy(ttsKey = v.trim()) } }
        TextRow("模型", p.ttsModel) { v -> Repo.setPrefs { it.copy(ttsModel = v.trim()) } }
        TextRow("音色 ID", p.ttsVoice) { v -> Repo.setPrefs { it.copy(ttsVoice = v.trim()) } }
        TextRow("播报模板（{任务} {目标} {第一步}）", p.ttsTemplate) { v -> Repo.setPrefs { it.copy(ttsTemplate = v) } }
        RippleButton("🔊 试听", {
            Voice.announce(ctx, Task(title = "背单词", goal = "我能认出二十个新单词", subs = listOf(Sub("打开单词本"))))
        })
        Text("完成任务时只播放你下载的鼓励音频，和这个语音接口没有关系。", fontSize = 12.sp, color = C.gray)
    }
}

@Composable
private fun AutoSettings() {
    val ctx = LocalContext.current
    val p = Repo.data.prefs
    var showMap by remember { mutableStateOf(false) }
    val hasUsage = Tracker.hasUsageAccess(ctx)
    Body {
        Section("自动记录 App 使用")
        SwitchRow("打开对应 App 自动计时，退出自动结束（不发金币、不播语音）", p.autoOn) { v -> Repo.setPrefs { it.copy(autoOn = v) } }
        Text(if (hasUsage) "使用情况访问权限：已开启 ✓" else "使用情况访问权限：还没开，需要手动开一次", fontSize = 13.sp, color = if (hasUsage) C.gray else C.accent)
        RippleButton("去开「使用情况访问权限」", { ctx.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        RippleButton("对应表：哪个 App 记到哪个标签（已设 " + p.autoMap.size + " 个）", { showMap = true })
        NumRow("少于几分钟的不记录", p.minMin) { v -> Repo.setPrefs { it.copy(minMin = v.coerceAtLeast(1)) } }
        Text("切走一分钟内又回来，算同一段，不拆成碎片。", fontSize = 12.sp, color = C.gray)
        RippleButton("清理已记下的、不到 " + p.minMin + " 分钟的自动记录", {
            val n = Repo.purgeShortAuto(p.minMin)
            Toast.makeText(ctx, "清理了 $n 条", Toast.LENGTH_SHORT).show()
        })
    }
    if (showMap) AppMapDialog { showMap = false }
}

@Composable
private fun NotionSettings() {
    val p = Repo.data.prefs
    Body {
        Section("Notion 联动")
        Text("结束播客 / 购物 / 看书 / 看小说类标签后，记录页会出现「去 Notion 写感想」，点一下跳过去（标签名和用时已复制，进去直接粘贴）。链接可以改。", fontSize = 12.sp, color = C.gray)
        p.notion.forEach { n ->
            TextRow(n.emoji + " " + n.name + " 页面链接", n.url) { v ->
                Repo.setPrefs { pp -> pp.copy(notion = pp.notion.map { if (it.key == n.key) it.copy(url = v.trim()) else it }) }
            }
        }
        Text("对应：播客 → Audio dramas；逛购物平台/逛街 → 购物清单；看书/睡前阅读（微信读书）→ 阅读系统；看小说（番茄小说、Reeden）→ 小说品鉴。", fontSize = 12.sp, color = C.gray)
    }
}

@Composable
private fun KeepAliveSettings() {
    val ctx = LocalContext.current
    Body {
        Section("防止被系统杀后台（小米/OPPO/vivo/华为等）")
        Text(
            "这些系统常会杀掉后台，导致自动记录和通知栏计时中断。请给「当下」开：自启动、后台运行、电池「无限制」。不同品牌入口不一样，下面两个按钮能带你到最近的页面。",
            fontSize = 13.sp, color = C.ink
        )
        RippleButton("电池优化", {
            try { ctx.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
        })
        RippleButton("应用详情（自启动）", {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
        })
    }
}

@Composable
private fun AppMapDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val d = Repo.data
    val apps by produceState<List<Pair<String, String>>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
                .distinctBy { it.second }.sortedBy { it.first }
        }
    }
    var open by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自动记录对应表") },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(apps) { (label, pkg) ->
                    val tag = d.prefs.autoMap.firstOrNull { it.pkg == pkg }?.let { m -> d.tags.firstOrNull { it.id == m.tagId } }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), fontSize = 14.sp)
                        Box {
                            TextButton(onClick = { open = pkg }) { Text(if (tag != null) tag.emoji + tag.name else "不记录", color = if (tag != null) C.accent else C.gray) }
                            DropdownMenu(expanded = open == pkg, onDismissRequest = { open = "" }) {
                                DropdownMenuItem(text = { Text("不记录") }, onClick = {
                                    Repo.setPrefs { p -> p.copy(autoMap = p.autoMap.filter { it.pkg != pkg }) }; open = ""
                                })
                                d.tags.forEach { t ->
                                    DropdownMenuItem(text = { Text(t.emoji + t.name + "（" + t.group + "）") }, onClick = {
                                        Repo.setPrefs { p -> p.copy(autoMap = p.autoMap.filter { it.pkg != pkg } + AutoMap(pkg, t.id)) }; open = ""
                                    })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
