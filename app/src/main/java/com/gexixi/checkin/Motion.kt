package com.gexixi.checkin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 把「刚度 k、阻尼 c」换成 Compose 的 spring（质量按 1 算） */
fun springF(k: Float, c: Float) = spring<Float>(dampingRatio = c / (2f * sqrt(k)), stiffness = k)

// ---------------- 底栏 / 卡片按压回弹：按下缩到 0.94 并减弱阴影，松手弹簧回 1（刚度260 阻尼12，略有过冲） ----------------
@Composable
fun Modifier.bounceClick(
    enabled: Boolean = true,
    elevation: Dp = 0.dp,
    shape: Shape = RectangleShape,
    onClick: () -> Unit
): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, springF(260f, 12f), label = "press")
    val e by animateFloatAsState(if (pressed) 0.35f else 1f, tween(120), label = "shadow")
    return this
        .graphicsLayer {
            scaleX = s
            scaleY = s
            if (elevation > 0.dp) {
                shadowElevation = elevation.toPx() * e
                this.shape = shape
                clip = false
            }
        }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

// ---------------- 弹性开关：滑块移动过程中横向拉伸到 1.35 倍，到位后弹簧收回（刚度200 阻尼14），底色 280ms 渐变 ----------------
@Composable
fun ElasticSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, onColor: Color = C.accent) {
    val pos by animateFloatAsState(if (checked) 1f else 0f, springF(200f, 14f), label = "knob")
    val track by animateColorAsState(if (checked) onColor else Color(0xFFCFCFCB), tween(280), label = "track")
    val mid = (4f * pos * (1f - pos)).coerceIn(0f, 1f)
    val stretch = 1f + 0.35f * mid
    Box(
        modifier.size(52.dp, 30.dp).clip(RoundedCornerShape(15.dp)).background(track)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }
    ) {
        val x = lerpF(3f, 25f, pos) - (24f * stretch - 24f) / 2f
        Box(Modifier.offset(x = x.dp, y = 3.dp).size(24.dp * stretch, 24.dp).background(Color.White, RoundedCornerShape(12.dp)))
    }
}

// ---------------- 设置里的按钮：跟手波纹，以点击处为圆心扩散，450ms 放大到按钮宽度的 2.4 倍，初始透明度 0.28 逐渐淡出 ----------------
@Composable
fun RippleButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    filled: Boolean = false, enabled: Boolean = true
) {
    val prog = remember { Animatable(0f) }
    var center by remember { mutableStateOf(Offset.Zero) }
    var wPx by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val fg = if (filled) Color.White else C.ink
    val bg = if (filled) C.ink else Color.White
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) bg else Color(0xFFE6E6E2))
            .border(1.dp, if (filled) C.ink else Color(0xFFBDBDB8), RoundedCornerShape(12.dp))
            .onSizeChanged { wPx = it.width.toFloat() }
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = { o ->
                        center = o
                        scope.launch { prog.snapTo(0f); prog.animateTo(1f, tween(450)) }
                    },
                    onTap = { if (enabled) onClick() }
                )
            }
            .drawWithContent {
                drawContent()
                val p = prog.value
                if (p > 0f && p < 1f) {
                    drawCircle(
                        color = (if (filled) Color.White else C.ink).copy(alpha = 0.28f * (1f - p)),
                        radius = wPx * 1.2f * p,
                        center = center
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) fg else C.gray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------- 磁吸按钮：指针进入 120px 范围时，按钮按 0.35 的强度向指针偏移，离开后弹簧回位（刚度150 阻尼15） ----------------
val LocalPointer = compositionLocalOf<MutableState<Offset?>> { mutableStateOf(null) }

/** 放在根布局上，只观察手指/鼠标位置，不拦截任何事件 */
fun Modifier.trackPointer(state: MutableState<Offset?>): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val t = ev.type
            val ch = ev.changes.firstOrNull()
            state.value = if (t == PointerEventType.Release || t == PointerEventType.Exit || ch == null) null else ch.position
        }
    }
}

@Composable
fun Modifier.magnetic(): Modifier {
    val ptr = LocalPointer.current.value
    var center by remember { mutableStateOf(Offset.Zero) }
    var tx = 0f
    var ty = 0f
    if (ptr != null) {
        val dx = ptr.x - center.x
        val dy = ptr.y - center.y
        if (dx * dx + dy * dy < 120f * 120f) { tx = dx * 0.35f; ty = dy * 0.35f }
    }
    val ax by animateFloatAsState(tx, springF(150f, 15f), label = "mx")
    val ay by animateFloatAsState(ty, springF(150f, 15f), label = "my")
    return this
        .onGloballyPositioned { center = it.boundsInRoot().center }
        .graphicsLayer { translationX = ax; translationY = ay }
}

// ---------------- 兑换按钮：点击缩放到 1.3 再弹回（刚度300 阻尼10），周围散开 8 个小圆点，+1 向上滚动 ----------------
@Composable
fun RedeemButton(
    label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    accent: Color = Color.White
) {
    val scale = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val b = burst.value
            if (b > 0f && b < 1f) {
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                val dist = this.size.minDimension * 0.5f + 26.dp.toPx() * b
                for (i in 0 until 8) {
                    val ang = (i * 45f) * (Math.PI.toFloat() / 180f)
                    drawCircle(
                        accent.copy(alpha = 1f - b),
                        radius = 5.dp.toPx() * (1f - b * 0.4f),
                        center = Offset(cx + cos(ang) * dist, cy + sin(ang) * dist)
                    )
                }
            }
        }
        Box(
            Modifier
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .clip(RoundedCornerShape(50))
                .background(if (enabled) accent else Color(0x33FFFFFF))
                .clickable(enabled = enabled) {
                    onClick()
                    scope.launch { scale.animateTo(1.3f, tween(90)); scale.animateTo(1f, springF(300f, 10f)) }
                    scope.launch { burst.snapTo(0f); burst.animateTo(1f, tween(650)) }
                }
                .padding(horizontal = 26.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = if (enabled) Color(0xFF111111) else Color(0x88FFFFFF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "+1", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer {
                translationY = -64.dp.toPx() * burst.value
                alpha = if (burst.value > 0f && burst.value < 1f) 1f - burst.value else 0f
            }
        )
    }
}

// ---------------- 游戏宝箱图标（任务奖励用；画出来的，不依赖 emoji） ----------------
@Composable
fun ChestIcon(dp: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(dp)) {
        val w = this.size.width
        val h = this.size.height
        val wood = Color(0xFFA9622F)
        val deep = Color(0xFF6E3B17)
        val gold = Color(0xFFF2B632)
        drawRoundRect(wood, Offset(w * 0.08f, h * 0.42f), Size(w * 0.84f, h * 0.48f), CornerRadius(w * 0.07f))
        drawRoundRect(deep, Offset(w * 0.06f, h * 0.12f), Size(w * 0.88f, h * 0.38f), CornerRadius(w * 0.2f))
        drawRect(gold, Offset(w * 0.16f, h * 0.14f), Size(w * 0.09f, h * 0.76f))
        drawRect(gold, Offset(w * 0.75f, h * 0.14f), Size(w * 0.09f, h * 0.76f))
        drawRect(gold, Offset(w * 0.08f, h * 0.42f), Size(w * 0.84f, h * 0.05f))
        drawRoundRect(gold, Offset(w * 0.42f, h * 0.36f), Size(w * 0.16f, h * 0.2f), CornerRadius(w * 0.03f))
        drawCircle(deep, w * 0.025f, Offset(w * 0.5f, h * 0.44f))
    }
}
