package com.gexixi.checkin

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

fun hm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

fun fmt(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

fun fmtMin(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "" + (m / 60) + "小时" + (m % 60) + "分钟" else "" + m + "分钟"
}

fun pickDate(ctx: Context, init: String, onPick: (String) -> Unit) {
    val d = runCatching { LocalDate.parse(init) }.getOrElse { LocalDate.now() }
    DatePickerDialog(ctx, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}

fun pickTime(ctx: Context, init: String, onPick: (String) -> Unit) {
    val h = init.take(2).toIntOrNull() ?: 9
    val m = init.drop(3).take(2).toIntOrNull() ?: 0
    TimePickerDialog(ctx, { _, hh, mm -> onPick("%02d:%02d".format(hh, mm)) }, h, m, true).show()
}

fun bitmapToBase64(bmp: Bitmap): String {
    val sc = 1024f / maxOf(bmp.width, bmp.height)
    val b2 = if (sc < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt(), (bmp.height * sc).toInt(), true) else bmp
    val bos = ByteArrayOutputStream()
    b2.compress(Bitmap.CompressFormat.JPEG, 80, bos)
    return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
}

fun imageToBase64(ctx: Context, uri: Uri): String? {
    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    return bitmapToBase64(bmp)
}

/** 把选中的文件复制进 App 自己的存储，返回新路径 */
fun copyUri(ctx: Context, uri: Uri, dir: File, prefix: String): String? {
    return try {
        dir.mkdirs()
        val mime = ctx.contentResolver.getType(uri)
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
        val f = File(dir, prefix + uid() + "." + ext)
        ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } ?: return null
        f.path
    } catch (e: Exception) {
        null
    }
}

fun saveBitmap(ctx: Context, bmp: Bitmap, prefix: String): String? {
    return try {
        val f = File(ctx.filesDir, prefix + uid() + ".jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        f.path
    } catch (e: Exception) {
        null
    }
}

@Composable
fun rememberGifLoader(): ImageLoader {
    val ctx = LocalContext.current
    return remember {
        ImageLoader.Builder(ctx).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }.build()
    }
}

@Composable
fun Bar(p: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x22000000))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0f, 1f)).background(color))
    }
}

/** 缩略图（小组件和日历里放照片用），按最大边解码，省内存 */
fun decodeThumb(path: String, maxPx: Int): Bitmap? = try {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, o)
    var s = 1
    while (o.outWidth / s > maxPx * 2 || o.outHeight / s > maxPx * 2) s *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
} catch (e: Exception) {
    null
}

fun fmtDur(min: Long): String = if (min >= 60) "" + (min / 60) + "h " + (min % 60) + "m" else "" + min + "m"
