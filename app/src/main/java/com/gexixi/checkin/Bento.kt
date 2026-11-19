package com.gexixi.checkin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 一次「卡片展开」的请求：从哪个位置、什么颜色、里面放什么 */
class ExpandReq(val rect: Rect, val color: Color, val endColor: Color, val title: String, val content: @Composable () -> Unit)

/** 展开状态放在最外层。点卡片 → open；返回/点暗处 → close */
class BentoHost {
    var req by mutableStateOf<ExpandReq?>(null)
    var closing by mutableStateOf(false)
    fun open(r: ExpandReq) { closing = false; req = r }
    fun close() { if (req != null) closing = true }
}

val LocalBento = compositionLocalOf { BentoHost() }

/** 展开页可以往顶部那一行（返回键右边）放内容，比如日历把日期放在 Today 旁边 */
val LocalHeaderSlot = compositionLocalOf { mutableStateOf<(@Composable () -> Unit)?>(null) }

/** 便当格卡片：24dp 圆角，按下回弹；有 detail 就在点击时从原位置展开成全屏 */
@Composable
fun Bento(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    title: String = "",
    detailColor: Color = Color.White,
    detail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val host = LocalBento.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val clickable = enabled && (detail != null || onClick != null)
    Box(
        modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .bounceClick(enabled = clickable) {
                if (detail != null) host.open(ExpandReq(bounds, color, detailColor, title, detail)) else onClick?.invoke()
            }
            .clip(RoundedCornerShape(24.dp))
            .background(color),
        content = content
    )
}

/** 展开层：卡片约 400ms 从原位置放大到全屏，详情在卡内淡入；其余卡片留在原位只变暗 */
@Composable
fun BentoOverlay(host: BentoHost) {
    val p = remember { Animatable(0f) }
    val r = host.req
    LaunchedEffect(r, host.closing) {
        if (r != null) {
            if (host.closing) {
                p.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
                host.req = null
                host.closing = false
            } else {
                p.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
            }
        }
    }
    BackHandler(enabled = r != null && !host.closing) { host.close() }
    if (r == null) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = p.value }
                .background(Color(0x77000000))
                .pointerInput(Unit) { detectTapGestures { host.close() } }
        )
        val dark = r.endColor.luminance() < 0.35f
        val slot = remember(r) { mutableStateOf<(@Composable () -> Unit)?>(null) }
        Box(
            Modifier
                .layout { m, c ->
                    val t = p.value
                    val fw = lerpF(r.rect.width, w.toFloat(), t).roundToInt().coerceAtLeast(1)
                    val fh = lerpF(r.rect.height, h.toFloat(), t).roundToInt().coerceAtLeast(1)
                    val fx = lerpF(r.rect.left, 0f, t).roundToInt()
                    val fy = lerpF(r.rect.top, 0f, t).roundToInt()
                    val pl = m.measure(Constraints.fixed(fw, fh))
                    layout(c.maxWidth, c.maxHeight) { pl.place(fx, fy) }
                }
                .graphicsLayer {
                    shape = RoundedCornerShape((24f * (1f - p.value)).dp)
                    clip = true
                }
                .drawBehind { drawRect(lerp(r.color, r.endColor, p.value)) }
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            // 内容按全屏尺寸固定排版，再反向平移抵消外框移动：外框放大时像「揭开」，不会反复重排
            Box(
                Modifier
                    .fillMaxSize()
                    .layout { m, c ->
                        val t = p.value
                        val fx = lerpF(r.rect.left, 0f, t).roundToInt()
                        val fy = lerpF(r.rect.top, 0f, t).roundToInt()
                        val pl = m.measure(Constraints.fixed(w, h))
                        layout(c.maxWidth, c.maxHeight) { pl.place(-fx, -fy) }
                    }
                    .graphicsLayer { alpha = ((p.value - 0.35f) / 0.65f).coerceIn(0f, 1f) }
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).bounceClick { host.close() },
                            contentAlignment = Alignment.Center
                        ) { Text("‹", fontSize = 30.sp, color = if (dark) Color.White else C.ink) }
                        Text(
                            r.title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = if (dark) Color.White else C.ink
                        )
                        Spacer(Modifier.weight(1f))
                        slot.value?.invoke()
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CompositionLocalProvider(LocalHeaderSlot provides slot) { r.content() }
                    }
                }
            }
        }
    }
}

/** 便当格页面容器：两列、12dp 缝隙；cell 是 1×1 卡片的边长 */
@Composable
fun BentoPage(header: @Composable () -> Unit, content: @Composable (cell: Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().paper()) {
        val cell = (maxWidth - 32.dp - 12.dp) / 2
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            header()
            content(cell)
            Spacer(Modifier.height(104.dp))   // 给悬浮的底栏留位置
        }
    }
}

@Composable
fun BentoRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

/** 页面左上角的设置齿轮 */
@Composable
fun GearButton(onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.White).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text("⚙️", fontSize = 20.sp) }
}
