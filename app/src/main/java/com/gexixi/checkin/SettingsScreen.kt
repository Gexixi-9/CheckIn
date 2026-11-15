@file:OptIn(ExperimentalMaterial3Api::class)

package com.gexixi.checkin

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(14.dp))
    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = C.ink)
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
fun SettingsScreen() {
    val ctx = LocalContext.current
    val p = Repo.data.prefs
    var showMap by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            try { Repo.exportBackup(ctx, uri); Toast.makeText(ctx, "已导出备份", Toast.LENGTH_SHORT).show() }
            catch (e: Exception) { Toast.makeText(ctx, "导出失败：" + e.message, Toast.LENGTH_LONG).show() }
        }
    }
    val hasUsage = Tracker.hasUsageAccess(ctx)

    Column(
        Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("设置", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)

        Section("完成任务时的鼓励音频")
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            (listOf("random" to "随机") + Media.chars).forEach { (k, n) ->
                FilterChip(selected = p.character == k, onClick = { Repo.setPrefs { it.copy(character = k) } }, label = { Text(n) })
                Spacer(Modifier.width(8.dp))
            }
        }
        OutlinedButton(onClick = { Media.cheer(ctx, p.character) }) { Text("▶ 试听") }

        Section("小红花")
        TextRow("小红花图标（可换成任何字符或 emoji）", p.flower) { v -> Repo.setPrefs { it.copy(flower = v.take(4)) } }
        MapRow("任务难度 → 默认小红花数", p.taskStarFlowers) { v -> Repo.setPrefs { it.copy(taskStarFlowers = v) } }
        MapRow("心愿想要程度 → 默认小红花数", p.wishStarFlowers) { v -> Repo.setPrefs { it.copy(wishStarFlowers = v) } }
        NumRow("单任务奖励上限（AI 给的偏高就按上限算，0=不限）", p.rewardCap) { v -> Repo.setPrefs { it.copy(rewardCap = v) } }
        NumRow("每天兑换心愿上限（0=不限）", p.dailyLimit) { v -> Repo.setPrefs { it.copy(dailyLimit = v) } }
        NumRow("每天小红花目标（进度条用）", p.dailyGoal) { v -> Repo.setPrefs { it.copy(dailyGoal = v.coerceAtLeast(1)) } }

        Section("AI 拆分任务（DeepSeek）")
        Text("密钥只存在手机本地，不会写进代码、不会上传 GitHub。", fontSize = 12.sp, color = C.gray)
        TextRow("接口地址", p.dsBase) { v -> Repo.setPrefs { it.copy(dsBase = v.trim()) } }
        TextRow("API Key", p.dsKey, true) { v -> Repo.setPrefs { it.copy(dsKey = v.trim()) } }
        TextRow("文字模型", p.dsModel) { v -> Repo.setPrefs { it.copy(dsModel = v.trim()) } }
        TextRow("拍照用的视觉模型（实验性，可能会改名，所以可以自己改）", p.dsVision) { v -> Repo.setPrefs { it.copy(dsVision = v.trim()) } }

        Section("任务发放语音播报（硅基流动）")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = p.announceOn, onCheckedChange = { v -> Repo.setPrefs { it.copy(announceOn = v) } })
            Spacer(Modifier.width(8.dp))
            Text("任务成为当前任务时播报（没填密钥/没网就用手机自带朗读）", fontSize = 13.sp)
        }
        TextRow("接口地址", p.ttsUrl) { v -> Repo.setPrefs { it.copy(ttsUrl = v.trim()) } }
        TextRow("API Key", p.ttsKey, true) { v -> Repo.setPrefs { it.copy(ttsKey = v.trim()) } }
        TextRow("模型", p.ttsModel) { v -> Repo.setPrefs { it.copy(ttsModel = v.trim()) } }
        TextRow("音色 ID", p.ttsVoice) { v -> Repo.setPrefs { it.copy(ttsVoice = v.trim()) } }
        TextRow("播报模板（{任务} {目标} {第一步}）", p.ttsTemplate) { v -> Repo.setPrefs { it.copy(ttsTemplate = v) } }
        OutlinedButton(onClick = {
            Voice.announce(ctx, Task(title = "背单词", goal = "背完二十个", subs = listOf(Sub("打开单词本"))))
        }) { Text("🔊 试听") }
        Text("完成任务时只播放你下载的鼓励音频，和这个语音接口没有关系。", fontSize = 12.sp, color = C.gray)

        Section("自动记录 App 使用")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = p.autoOn, onCheckedChange = { v -> Repo.setPrefs { it.copy(autoOn = v) } })
            Spacer(Modifier.width(8.dp))
            Text("打开对应 App 自动计时，退出自动结束（不发小红花、不播语音）", fontSize = 13.sp)
        }
        Text(if (hasUsage) "使用情况访问权限：已开启 ✓" else "使用情况访问权限：还没开，需要手动开一次", fontSize = 13.sp, color = if (hasUsage) C.gray else C.red)
        OutlinedButton(onClick = { ctx.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("去开「使用情况访问权限」") }
        OutlinedButton(onClick = { showMap = true }) { Text("对应表：哪个 App 记到哪个标签（已设 " + p.autoMap.size + " 个）") }
        NumRow("不到几秒的切换不记", p.minSec) { v -> Repo.setPrefs { it.copy(minSec = v.coerceAtLeast(0)) } }

        Section("防止被系统杀后台（小米/OPPO/vivo/华为等）")
        Text(
            "这些系统常会杀掉后台，导致自动记录和通知栏计时中断。请给「当下」开：自启动、后台运行、电池「无限制」。不同品牌入口不一样，下面两个按钮能带你到最近的页面。",
            fontSize = 13.sp, color = C.ink
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                try { ctx.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
            }) { Text("电池优化") }
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
            }) { Text("应用详情（自启动）") }
        }

        Section("备份")
        OutlinedButton(onClick = { export.launch("dangxia-backup.zip") }) { Text("导出备份（数据 + 日记 + 心愿图片）") }
        Spacer(Modifier.height(40.dp))
    }
    if (showMap) AppMapDialog { showMap = false }
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
                            TextButton(onClick = { open = pkg }) { Text(if (tag != null) tag.emoji + tag.name else "不记录", color = if (tag != null) C.red else C.gray) }
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
