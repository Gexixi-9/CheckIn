package com.gexixi.checkin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

object Ai {
    private fun post(url: String, key: String, json: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 120000
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

    /**
     * DeepSeek 拆任务：返回目标、达成条件、子任务（每个有难度）、整体难度和小红花、可选奖励愿望。
     * imageB64 不为空时走视觉模型（模型名在设置里可改）。结果只是草稿，确认前可以改。
     */
    suspend fun split(p: Prefs, text: String, imageB64: String?): List<Task> = withContext(Dispatchers.IO) {
        if (p.dsKey.isBlank()) throw Exception("请先在「设置」里填 DeepSeek API Key")
        val d = Repo.data
        val listNames = d.lists.joinToString("、") { it.name }
        val wishNames = d.wishes.filter { it.kind == 0 && it.status == 0 }.joinToString("、") { it.name }
        val cap = if (p.rewardCap > 0) p.rewardCap else 10
        val sys = """你是任务拆分助手，帮一个容易拖延的人把事情变成能立刻开始的小任务。只输出 json，不要任何解释。
格式：{"tasks":[{"title":"任务名","list":"所属清单名","goal":"本次目标，一句话","condition":"达成条件，要能检验","subtasks":[{"text":"子任务","stars":1}],"stars":3,"flowers":3,"reward_wish":"","date":"","time":""}]}
规则：
1. 默认只返回一个任务（tasks 里一个元素）；只有用户明确说了几件互不相关的事才返回多个。
2. subtasks 2到5个。难的任务把子任务分成两类：探索类（从目标倒推，先弄清楚要什么）和事务类（照着做就行）。
3. 每个子任务的第一步要足够小，能立刻开始，比如「打开文档，写下第一句」，不要写「完成某某」这种大词。
4. stars 是整个任务的难度，1到5的整数；每个子任务也有自己的 stars。
5. flowers 是完成整个任务奖励的小红花数，1到${cap}的整数，要和难度相称，不要偏高。
6. list 只能从这些清单里选：${listNames}。
7. reward_wish：只有用户提到完成后想要的奖励才填。优先从已有心愿里选名字完全一致的（已有心愿：${wishNames.ifBlank { "无" }}），没有合适的再写新的；用户没提就留空字符串。
8. date 格式 YYYY-MM-DD，time 格式 HH:mm，用户没提就留空。今天是 ${LocalDate.now()}。"""
        val userContent: Any = if (imageB64 != null) {
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", text.ifBlank { "请根据这张图片里的内容拆分任务" }))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64," + imageB64)))
        } else text
        val body = JSONObject()
            .put("model", if (imageB64 != null) p.dsVision else p.dsModel)
            .put("temperature", 0.3)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", sys))
                .put(JSONObject().put("role", "user").put("content", userContent)))
        if (imageB64 == null) body.put("response_format", JSONObject().put("type", "json_object"))
        val resp = String(post(p.dsBase.trimEnd('/') + "/chat/completions", p.dsKey, body.toString()), Charsets.UTF_8)
        var content = JSONObject(resp).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        val a = content.indexOf('{')
        val b = content.lastIndexOf('}')
        if (a >= 0 && b > a) content = content.substring(a, b + 1)
        val arr = JSONObject(content).getJSONArray("tasks")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val sa = o.optJSONArray("subtasks")
            val subs = if (sa == null) emptyList() else (0 until sa.length()).mapNotNull { k ->
                val so = sa.optJSONObject(k)
                if (so != null) Sub(so.optString("text"), false, so.optInt("stars", 1).coerceIn(1, 5))
                else sa.optString(k).takeIf { it.isNotBlank() }?.let { Sub(it) }
            }.filter { it.text.isNotBlank() }
            val stars = o.optInt("stars", 1).coerceIn(1, 5)
            val rawFlowers = o.optInt("flowers", Repo.flowersFor(stars)).coerceAtLeast(1)
            val listName = o.optString("list")
            val date = o.optString("date")
            val time = o.optString("time")
            Task(
                title = o.optString("title"),
                goal = o.optString("goal"),
                condition = o.optString("condition"),
                subs = subs,
                stars = stars,
                flowers = rawFlowers.coerceAtMost(cap),   // 单任务奖励上限：AI 给的偏高就按上限算
                listId = d.lists.firstOrNull { it.name == listName }?.id ?: d.lists.first().id,
                reward = o.optString("reward_wish"),
                date = if (validDate(date)) date else "",
                time = if (validTime(time)) time else ""
            )
        }.filter { it.title.isNotBlank() }
    }

    /** 硅基流动 TTS：只用于任务发放播报。生成后写进缓存文件。 */
    suspend fun tts(p: Prefs, text: String, out: File) = withContext(Dispatchers.IO) {
        if (p.ttsKey.isBlank()) throw Exception("请先在「设置」里填硅基流动 API Key")
        val body = JSONObject()
            .put("model", p.ttsModel)
            .put("input", text)
            .put("voice", p.ttsVoice)
            .put("response_format", "mp3")
        val bytes = post(p.ttsUrl, p.ttsKey, body.toString())
        out.parentFile?.mkdirs()
        out.writeBytes(bytes)
    }
}
