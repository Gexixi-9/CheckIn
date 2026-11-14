package com.gexixi.checkin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class AiTask(val task: Task, val wish: String)

object Ai {
    private fun postBytes(url: String, key: String, json: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 90000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Authorization", "Bearer $key")
        c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        if (code >= 400) {
            val e = c.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
            throw Exception(("HTTP $code $e").take(300))
        }
        return c.inputStream.use { it.readBytes() }
    }

    private fun validDate(s: String) = runCatching { LocalDate.parse(s) }.isSuccess
    private fun validTime(s: String) = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(s)

    /** DeepSeek：把一段描述拆成任务（含目标、达成条件、子任务、难度星级、奖励小红花、可选奖励心愿）。 */
    suspend fun split(s: Settings, text: String): List<AiTask> = withContext(Dispatchers.IO) {
        if (s.dsKey.isBlank()) throw Exception("请先在「设置」里填 DeepSeek API Key")
        val sys = """你是任务拆分助手。把用户的描述（可能是一个大目标，也可能是一堆杂事）拆成可以直接去做的小任务，每个任务大约25到60分钟能完成。
只输出 json，格式如下：
{"tasks":[{"title":"简短任务名","goal":"本次目标，一句话","condition":"达成条件，要能检验","subtasks":["步骤1","步骤2"],"stars":3,"flowers":5,"reward_wish":"","date":"","time":""}]}
要求：subtasks 2到5个；stars 是1到5的整数难度；flowers 是1到10的整数奖励小红花，要和难度相称；
reward_wish 只有当用户明确提到完成后想要的奖励才填，否则空字符串；date 格式 YYYY-MM-DD、time 格式 HH:mm，用户没提就留空。
今天的日期是 ${LocalDate.now()}。"""
        val body = JSONObject()
            .put("model", s.dsModel)
            .put("temperature", 0.3)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", sys))
                .put(JSONObject().put("role", "user").put("content", text)))
        val resp = String(postBytes("https://api.deepseek.com/chat/completions", s.dsKey, body.toString()), Charsets.UTF_8)
        val content = JSONObject(resp).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        val arr = JSONObject(content).getJSONArray("tasks")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val sa = o.optJSONArray("subtasks")
            val subs = if (sa == null) emptyList() else (0 until sa.length()).map { Sub(sa.optString(it)) }
            val stars = o.optInt("stars", 1).coerceIn(1, 5)
            val date = o.optString("date")
            val time = o.optString("time")
            AiTask(
                Task(
                    title = o.optString("title"),
                    goal = o.optString("goal"),
                    condition = o.optString("condition"),
                    subs = subs,
                    stars = stars,
                    flowers = o.optInt("flowers", stars).coerceAtLeast(1),
                    date = if (validDate(date)) date else "",
                    time = if (validTime(time)) time else ""
                ),
                o.optString("reward_wish")
            )
        }.filter { it.task.title.isNotBlank() }
    }

    /** 硅基流动 TTS：只用于「任务发放」时的语音播报。 */
    suspend fun tts(ctx: Context, s: Settings, text: String): File = withContext(Dispatchers.IO) {
        if (s.ttsKey.isBlank()) throw Exception("请先在「设置」里填硅基流动 API Key")
        val body = JSONObject()
            .put("model", s.ttsModel)
            .put("input", text)
            .put("voice", s.ttsVoice)
            .put("response_format", "mp3")
        val bytes = postBytes("https://api.siliconflow.cn/v1/audio/speech", s.ttsKey, body.toString())
        File(ctx.cacheDir, "tts.mp3").also { it.writeBytes(bytes) }
    }
}
