@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.gexixi.checkin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 日记页里的 AI 日记助手：
 * ① 引导我写：AI 随机问几个问题（有选项也有开放题），我回答后整理成一篇日记；
 * ② AI 每天点评我的日记或生活，语言风格参考「感恩日记」。
 */
@Composable
fun AiDiaryCard(date: LocalDate) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = date.toString()
    val e = Repo.diaryOf(key)
    var flow by remember { mutableIntStateOf(0) }             // 0 关  1 引导写  2 改现有的
    var reviewing by remember { mutableStateOf(false) }

    fun review() {
        reviewing = true
        scope.launch {
            try {
                val r = Ai.diaryReview(Repo.data.prefs, Repo.dayContext(date), Repo.diaryOf(key).text)
                if (r.isNotBlank()) Repo.diarySetReview(key, r)
            } catch (ex: Exception) {
                Toast.makeText(ctx, "点评失败：" + (ex.message ?: ""), Toast.LENGTH_LONG).show()
            }
            reviewing = false
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFF1EEF8)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("✨ AI 日记助手", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = C.ink)
            if (e.text.isBlank()) {
                Text("AI 会随机问你几个问题（有的能直接选），你回答完，它帮你整理成一篇日记。", fontSize = 13.sp, color = C.gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RippleButton("引导我写日记", { flow = 1 }, filled = true)
                    RippleButton(if (reviewing) "点评中…" else "只点评今天的生活", { if (!reviewing) review() }, enabled = !reviewing)
                }
            } else {
                Text(e.text, fontSize = 14.sp, color = C.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RippleButton("改一改", { flow = 2 })
                    RippleButton("重新引导", { flow = 1 })
                }
            }
        }
        if (e.review.isNotBlank() || e.text.isNotBlank()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFFAF3E3)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🌿 今天的小点评", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = C.ink)
                Text(
                    if (e.review.isBlank()) "还没有点评。" else e.review,
                    fontSize = 14.sp, color = if (e.review.isBlank()) C.gray else C.ink
                )
                RippleButton(if (reviewing) "点评中…" else if (e.review.isBlank()) "让 AI 点评" else "重新点评", { if (!reviewing) review() }, enabled = !reviewing)
            }
        }
    }
    if (flow != 0) AiDiaryFlow(date, startAtEdit = flow == 2, onClose = { flow = 0 }, onSaved = { review() })
}

@Composable
private fun AiDiaryFlow(date: LocalDate, startAtEdit: Boolean, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val key = date.toString()
    val dayCtx = remember(date) { Repo.dayContext(date) }
    // 0 取问题中  1 回答问题  2 整理中  3 看/改日记
    var stage by remember { mutableIntStateOf(if (startAtEdit) 3 else 0) }
    var qs by remember { mutableStateOf<List<Ai.DiaryQ>>(emptyList()) }
    val answers = remember { mutableStateListOf<String>() }
    var text by remember { mutableStateOf(if (startAtEdit) Repo.diaryOf(key).text else "") }
    var err by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        if (stage == 0) {
            try {
                val got = Ai.diaryQuestions(Repo.data.prefs, dayCtx)
                if (got.isEmpty()) throw Exception("AI 没有给出问题")
                qs = got
                answers.clear()
                got.forEach { answers.add("") }
                err = ""
                stage = 1
            } catch (ex: Exception) {
                err = ex.message ?: "失败"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (stage != 2) onClose() },
        title = { Text(if (stage == 3) "今天的日记" else "AI 引导写日记") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (stage) {
                    0 -> {
                        if (err.isEmpty()) {
                            CircularProgressIndicator()
                            Text("AI 正在想问题…", fontSize = 13.sp, color = C.gray)
                        } else {
                            Text(err, color = C.red, fontSize = 13.sp)
                            RippleButton("重试", { err = ""; tick++ })
                        }
                    }
                    1 -> {
                        qs.forEachIndexed { i, q ->
                            Text((i + 1).toString() + ". " + q.q, fontWeight = FontWeight.Medium, color = C.ink)
                            if (q.options.isNotEmpty()) {
                                ChipFlow {
                                    q.options.forEach { opt ->
                                        FilterChip(selected = answers[i] == opt, onClick = { answers[i] = opt }, label = { Text(opt) })
                                    }
                                }
                            }
                            OutlinedTextField(
                                answers[i], { answers[i] = it },
                                label = { Text(if (q.options.isNotEmpty()) "选一个，或者自己写" else "我的回答") },
                                modifier = Modifier.fillMaxWidth(), minLines = if (q.options.isNotEmpty()) 1 else 2
                            )
                        }
                        if (err.isNotEmpty()) Text(err, color = C.red, fontSize = 13.sp)
                    }
                    2 -> {
                        CircularProgressIndicator()
                        Text("正在帮你整理成日记…", fontSize = 13.sp, color = C.gray)
                    }
                    else -> {
                        OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 8)
                        Text("可以直接改。保存后 AI 会给今天写一段点评。", fontSize = 12.sp, color = C.gray)
                    }
                }
            }
        },
        confirmButton = {
            when (stage) {
                1 -> TextButton(onClick = {
                    stage = 2
                    scope.launch {
                        try {
                            text = Ai.diaryCompose(Repo.data.prefs, dayCtx, qs.mapIndexed { i, q -> q.q to answers[i] })
                            stage = 3
                        } catch (ex: Exception) {
                            err = ex.message ?: "失败"
                            stage = 1
                        }
                    }
                }) { Text("整理成日记", color = C.accent, fontWeight = FontWeight.Bold) }
                3 -> TextButton(onClick = {
                    Repo.diarySetText(key, text.trim())
                    onSaved()
                    onClose()
                }) { Text("保存并点评", color = C.accent, fontWeight = FontWeight.Bold) }
                else -> {}
            }
        },
        dismissButton = { if (stage != 2) TextButton(onClick = onClose) { Text("取消") } }
    )
}
