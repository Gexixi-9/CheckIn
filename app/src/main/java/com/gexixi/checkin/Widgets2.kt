@file:OptIn(ExperimentalLayoutApi::class)

package com.gexixi.checkin

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 选标签用的自动换行排列：不再横着一直往左滑，所有选项一眼都能看到 */
@Composable
fun ChipFlow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

/** 安卓系统那个标志性的小绿人头（AI 拆分图标） */
@Composable
fun AndroidHeadIcon(dp: Dp = 36.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(dp)) {
        val w = this.size.width
        val h = this.size.height
        val green = Color(0xFF3DDC84)
        val cx = w / 2f
        val r = w * 0.36f
        val baseY = h * 0.66f
        // 天线
        drawLine(green, Offset(cx - r * 0.55f, baseY - r * 0.75f), Offset(cx - r * 0.85f, baseY - r * 1.2f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
        drawLine(green, Offset(cx + r * 0.55f, baseY - r * 0.75f), Offset(cx + r * 0.85f, baseY - r * 1.2f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
        // 半圆脑袋
        drawArc(green, 180f, 180f, true, Offset(cx - r, baseY - r), Size(r * 2f, r * 2f))
        // 眼睛
        drawCircle(Color.White, w * 0.045f, Offset(cx - r * 0.45f, baseY - r * 0.4f))
        drawCircle(Color.White, w * 0.045f, Offset(cx + r * 0.45f, baseY - r * 0.4f))
    }
}

/**
 * 录音条：左边三角形播放按钮（播放中变成暂停），右边是音频波段，播放时从左往右染色。
 * 波段是按文件名生成的固定形状（只是示意，不是真实波形）。
 */
@Composable
fun AudioWave(path: String, tint: Color, modifier: Modifier = Modifier) {
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }
    var progress by remember(path) { mutableFloatStateOf(0f) }
    val bars = remember(path) {
        val r = java.util.Random(path.hashCode().toLong())
        List(28) { 0.25f + r.nextFloat() * 0.75f }
    }
    DisposableEffect(path) { onDispose { try { player?.release() } catch (_: Exception) {} } }
    LaunchedEffect(player) {
        val mp = player
        while (mp != null) {
            progress = try { mp.currentPosition.toFloat() / mp.duration.coerceAtLeast(1) } catch (e: Exception) { 0f }
            delay(120)
        }
        progress = 0f
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier.size(30.dp).clip(CircleShape).background(tint).clickable {
                val cur = player
                if (cur != null) {
                    try { cur.release() } catch (_: Exception) {}
                    player = null
                } else {
                    try {
                        val mp = MediaPlayer()
                        mp.setDataSource(path)
                        mp.prepare()
                        mp.setOnCompletionListener { try { it.release() } catch (_: Exception) {}; player = null }
                        mp.start()
                        player = mp
                    } catch (_: Exception) {}
                }
            }
        ) {
            val s = this.size.width
            if (player == null) {
                val p = Path()
                p.moveTo(s * 0.40f, s * 0.28f)
                p.lineTo(s * 0.40f, s * 0.72f)
                p.lineTo(s * 0.74f, s * 0.50f)
                p.close()
                drawPath(p, Color.White)
            } else {
                drawRoundRect(Color.White, Offset(s * 0.32f, s * 0.30f), Size(s * 0.12f, s * 0.40f), CornerRadius(2f))
                drawRoundRect(Color.White, Offset(s * 0.56f, s * 0.30f), Size(s * 0.12f, s * 0.40f), CornerRadius(2f))
            }
        }
        Spacer(Modifier.width(8.dp))
        Canvas(Modifier.weight(1f).height(24.dp)) {
            val n = bars.size
            val step = this.size.width / n
            val bw = step * 0.55f
            bars.forEachIndexed { i, v ->
                val bh = this.size.height * v
                val on = (i + 1).toFloat() / n <= progress
                drawRoundRect(
                    if (on) tint else tint.copy(alpha = 0.35f),
                    Offset(i * step + (step - bw) / 2f, (this.size.height - bh) / 2f),
                    Size(bw, bh), CornerRadius(bw / 2f)
                )
            }
        }
    }
}

/** 小钟表轮廓图标（时间行前面那个小钟） */
@Composable
fun ClockGlyph(dp: Dp, tint: Color) {
    Canvas(Modifier.size(dp)) {
        val s = this.size.width
        drawCircle(tint, s / 2f - s * 0.07f, style = Stroke(s * 0.12f))
        drawLine(tint, Offset(s / 2f, s / 2f), Offset(s / 2f, s * 0.28f), strokeWidth = s * 0.11f, cap = StrokeCap.Round)
        drawLine(tint, Offset(s / 2f, s / 2f), Offset(s * 0.66f, s * 0.58f), strokeWidth = s * 0.11f, cap = StrokeCap.Round)
    }
}
