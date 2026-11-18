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
     * DeepSeek 拆任务（状态目标法）：返回目标、达成条件、子任务（每个有难度）、整体难度和金币、可选奖励。
     * imageB64 不为空时走视觉模型（模型名在设置里可改）。结果只是草稿，确认前可以改。
     * startState：用户当下的启动状态（0 未设），会换不同的拆法。
     */
    suspend fun split(p: Prefs, text: String, imageB64: String?, startState: Int = 0): List<Task> = withContext(Dispatchers.IO) {
        if (p.dsKey.isBlank()) throw Exception("请先在「设置」里填 DeepSeek API Key")
        val d = Repo.data
        val listNames = d.lists.joinToString("、") { it.name }
        val wishNames = d.wishes.filter { it.kind == 0 && it.status == 0 }.joinToString("、") { it.name }
        val cap = if (p.rewardCap > 0) p.rewardCap else 10
        val stateRule = if (startState in 1..4) "\n当前启动状态：" + STATE_NAMES[startState] + "。" + STATE_HINTS[startState] + "拆分要顺着这个状态来。" else ""
        val sys = """你是任务拆分助手，帮一个容易拖延、容易卡在开头的人把事情变成能立刻开始的小任务。只输出 json，不要任何解释。
格式：{"tasks":[{"title":"任务名","list":"所属清单名","goal":"本次目标","condition":"达成条件","subtasks":[{"text":"子任务","stars":1}],"stars":3,"flowers":3,"reward_wish":"","date":"","time":""}]}
规则：
1. 默认只返回一个任务（tasks 里一个元素）；只有用户明确说了几件互不相关的事才返回多个。
2. goal 写成「状态目标」：描述做完之后「我学会了什么 / 我能做到什么」，用「我……」开头，不要写「完成某某」。condition 是能检验的证据：做到什么、产出什么，才算这个状态达成了。
3. 从目标倒推：先想清楚达成状态需要什么，再往前推到现在能做的第一步。subtasks 2到6个，难的任务分成探索类（先弄清楚要什么）和事务类（照着做就行）。
4. 拆到最小一步：每个子任务都小到两分钟内能开始，写成具体动作，比如「打开文档，写下第一句」「把课本放到桌上」，不要写「完成」「准备」这种大词。第一个子任务必须是最容易启动的动作。
5. stars 是整个任务的难度，1到5的整数；每个子任务也有自己的 stars。
6. flowers 是完成整个任务奖励的金币数，1到${cap}的整数，要和难度相称，不要偏高。
7. list 只能从这些清单里选：${listNames}。
8. reward_wish：用户提到完成后想要的奖励才填；优先从已有商品里选名字完全一致的（已有商品：${wishNames.ifBlank { "无" }}），没有合适的再写新的；用户没提就留空字符串。
9. date 格式 YYYY-MM-DD，time 格式 HH:mm，用户没提就留空。今天是 ${LocalDate.now()}。$stateRule"""
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
                flowers = rawFlowers.coerceAtMost(cap),
                listId = d.lists.firstOrNull { it.name == listName }?.id ?: d.lists.first().id,
                reward = o.optString("reward_wish"),
                date = if (validDate(date)) date else "",
                time = if (validTime(time)) time else "",
                startState = startState
            )
        }.filter { it.title.isNotBlank() }
    }

    /** 手动建任务时：把名称/备注交给 AI，自动补上目标、达成条件、子任务、难度、金币和奖励 */
    suspend fun fill(p: Prefs, t: Task): Task {
        val listName = Repo.list(t.listId).name
        val desc = "任务名：" + t.title + (if (t.note.isNotBlank()) "\n备注：" + t.note else "") + "\n所属清单：" + listName
        val r = split(p, desc, null, t.startState).firstOrNull() ?: return t
        return t.copy(
            goal = r.goal, condition = r.condition, subs = r.subs, stars = r.stars, flowers = r.flowers,
            reward = if (t.reward.isBlank()) r.reward else t.reward
        )
    }

    /**
     * 结算：对照「达成条件」和用户自己写的感想，算这次真正该发多少金币。
     * achieved：0 还没达到 1 部分达成 2 完全达成。返回（金币，评语）。
     */
    suspend fun settle(p: Prefs, t: Task, achieved: Int, note: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        if (p.dsKey.isBlank()) throw Exception("没填 DeepSeek API Key")
        val cap = if (p.rewardCap > 0) p.rewardCap else 10
        val planned = t.flowers.coerceAtMost(cap)
        val sys = """你是任务结算官，像游戏系统一样给出公平的金币结算。只输出 json：{"coins":整数,"comment":"评语"}。
规则：
1. 预设金币是 $planned。实际金币在 0 到 $cap 之间，可以和预设不同。
2. 对照「达成条件」和用户写的感想判断：完全达成且感想具体 → 接近或略高于预设；部分达成 → 约预设的一半到七成；没达到 → 很少，但只要认真做过就至少给 1。
3. 感想空泛（只写「完成了」）不扣分太多，但不给额外奖励。
4. comment 用中文，最多两句，温和、具体，指出这次和达成条件之间还差什么，或者哪里做得好。不要说教。"""
        val user = "任务：" + t.title + "\n本次目标：" + t.goal + "\n达成条件：" + t.condition +
            "\n用户自评：" + listOf("还没达到", "部分达成", "完全达成")[achieved.coerceIn(0, 2)] + "\n用户感想：" + note.ifBlank { "（没写）" }
        val body = JSONObject()
            .put("model", p.dsModel)
            .put("temperature", 0.2)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", sys))
                .put(JSONObject().put("role", "user").put("content", user)))
        val resp = String(post(p.dsBase.trimEnd('/') + "/chat/completions", p.dsKey, body.toString()), Charsets.UTF_8)
        var content = JSONObject(resp).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        val a = content.indexOf('{')
        val b = content.lastIndexOf('}')
        if (a >= 0 && b > a) content = content.substring(a, b + 1)
        val o = JSONObject(content)
        o.optInt("coins", planned).coerceIn(0, cap) to o.optString("comment")
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
