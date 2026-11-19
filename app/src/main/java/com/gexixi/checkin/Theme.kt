package com.gexixi.checkin

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    // 12 色项目色板（按你给的彩色小方块便签图）
    val palette = listOf(
        0xFFE0143C, 0xFFF6F0DC, 0xFFC9C43A, 0xFFC8E23C, 0xFFA8E06A, 0xFF4FCB8C,
        0xFF2BA8E0, 0xFF9FB8E8, 0xFFC7AEEB, 0xFF7A3FC0, 0xFFEE7C95, 0xFF8E9093
    )
    val paper = Color(0xFFFAFAF7)
    val grid = Color(0xFFE4E4DE)
    val note = Color(0xFFF6F0DC)
    val ink = Color(0xFF1E1E1E)
    val star = Color(0xFFFFC400)
    val starOff = Color(0xFFCFCFCB)
    val red = Color(0xFFE0143C)          // 只用于删除这类危险操作，不再当强调色
    val accent = Color(0xFF1E1E1E)       // 强调色：黑
    val pink = Color(0xFFEE7C95)
    val gray = Color(0xFF8E9093)
    val gold = Color(0xFFE8B84A)
    val night = Color(0xFF15171A)
}

fun pcol(i: Int): Color = Color(C.palette[i.coerceIn(0, 11)])
fun pint(i: Int): Int = C.palette[i.coerceIn(0, 11)].toInt()

/** 色板色和白色混合，做便当格的浅色底 */
fun soft(i: Int, a: Float = 0.3f): Color {
    val c = pcol(i)
    return Color(1f - (1f - c.red) * a, 1f - (1f - c.green) * a, 1f - (1f - c.blue) * a)
}

/** 深一号的同色系（字体颜色用） */
fun darker(c: Color, k: Float = 0.5f): Color = Color(c.red * k, c.green * k, c.blue * k, c.alpha)

fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** 金币图标（设置里可换成别的字符/emoji） */
fun coinIcon(): String = Repo.data.prefs.coin.ifBlank { "🪙" }

/** 方格纸底 */
fun Modifier.paper(): Modifier = this.background(C.paper).drawBehind {
    val step = 24.dp.toPx()
    var x = 0f
    while (x < size.width) { drawLine(C.grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
    var y = 0f
    while (y < size.height) { drawLine(C.grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }
}

/** 行底部的淡淡虚线 */
fun Modifier.dashedBottom(): Modifier = this.drawBehind {
    val y = size.height - 1f
    drawLine(
        Color(0xFFB8B8B2), Offset(0f, y), Offset(size.width, y), 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    )
}

/** 苹果提醒事项风格的空心圆圈：点完填充成项目色，带弹一下的小动画 */
@Composable
fun CheckCircle(done: Boolean, color: Color, size: Dp = 28.dp, onClick: () -> Unit) {
    val s by animateFloatAsState(
        if (done) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "circle"
    )
    Box(
        Modifier.size(size).clip(CircleShape)
            .border(2.dp, if (done) color else Color(0xFF9A9A96), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().scale(s.coerceAtLeast(0f)).background(color, CircleShape))
    }
}

/** 黄色五角星难度；未填色是浅灰轮廓 */
@Composable
fun StarRow(n: Int, size: TextUnit = 14.sp, onPick: ((Int) -> Unit)? = null) {
    Row {
        (1..5).forEach { i ->
            Text(
                if (i <= n) "★" else "☆",
                color = if (i <= n) C.star else C.starOff,
                fontSize = size,
                modifier = if (onPick != null) Modifier.clickable { onPick(i) } else Modifier
            )
        }
    }
}
