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

    // ============ 通用：让 AI 返回一个 json 对象 ============
    private suspend fun chatJson(p: Prefs, sys: String, user: String, img: String? = null, temp: Double = 0.7): JSONObject =
        withContext(Dispatchers.IO) {
            if (p.dsKey.isBlank()) throw Exception("请先在「设置」里填 DeepSeek API Key")
            val userContent: Any = if (img != null) {
                JSONArray()
                    .put(JSONObject().put("type", "text").put("text", user))
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64," + img)))
            } else user
            val body = JSONObject()
                .put("model", if (img != null) p.dsVision else p.dsModel)
                .put("temperature", temp)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", sys))
                    .put(JSONObject().put("role", "user").put("content", userContent)))
            if (img == null) body.put("response_format", JSONObject().put("type", "json_object"))
            val resp = String(post(p.dsBase.trimEnd('/') + "/chat/completions", p.dsKey, body.toString()), Charsets.UTF_8)
            var content = JSONObject(resp).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            val a = content.indexOf('{')
            val b = content.lastIndexOf('}')
            if (a >= 0 && b > a) content = content.substring(a, b + 1)
            JSONObject(content)
        }

    // ============ AI 日记：引导提问 → 整理成日记 → 感恩风格点评 ============
    data class DiaryQ(val q: String, val options: List<String>)

    private const val GRATITUDE = "语言风格参考「感恩日记」：温柔、具体、不说教、不喊口号，多用「感谢」「幸好」「很珍惜」「真好」这类表达，把今天的小事写成值得感恩的瞬间。"

    suspend fun diaryQuestions(p: Prefs, dayContext: String): List<DiaryQ> {
        val sys = """你是一位温柔的日记引导者，帮用户回忆今天、写日记。只输出 json：{"questions":[{"q":"问题","options":["选项1","选项2"]}]}
规则：
1. 随机提 4 个问题，每次的角度要有变化（随机种子 ${System.nanoTime() % 9973}）：可以问今天的小确幸、让情绪好一点的瞬间、想感谢的人或事、辛苦的地方、想对自己说的话、明天想怎么对待自己。
2. 要结合下面的「今天的记录」，问得具体，不要空泛；记录很少就问生活里的小事。
3. 其中 2 个问题给 3 到 4 个选项（选项是第一人称的短句，比如「今天吃得很满足」，方便点选），另外 2 个是开放问答（options 写空数组）。
4. 问题用中文，语气像朋友聊天，一句话以内。
$GRATITUDE"""
        val o = chatJson(p, sys, "今天的记录：\n" + dayContext, temp = 1.0)
        val arr = o.getJSONArray("questions")
        return (0 until arr.length()).mapNotNull { i ->
            val q = arr.getJSONObject(i)
            val ops = q.optJSONArray("options")
            val list = if (ops == null) emptyList() else (0 until ops.length()).map { ops.optString(it) }.filter { it.isNotBlank() }
            if (q.optString("q").isBlank()) null else DiaryQ(q.optString("q"), list)
        }
    }

    suspend fun diaryCompose(p: Prefs, dayContext: String, qa: List<Pair<String, String>>): String {
        val sys = """你帮用户把今天的回答整理成一篇日记。只输出 json：{"diary":"日记正文"}
规则：第一人称「我」；只用用户给的事实和感受，不编造没发生的事；150 到 300 字，分两三段，自然流畅；最后单独一行写「今天的三件小确幸：」后面列三条（从回答和记录里找）。
$GRATITUDE"""
        val user = "今天的记录：\n" + dayContext + "\n\n用户的回答：\n" + qa.joinToString("\n") { "问：" + it.first + "\n答：" + it.second.ifBlank { "（没回答）" } }
        return chatJson(p, sys, user, temp = 0.8).optString("diary").trim()
    }

    suspend fun diaryReview(p: Prefs, dayContext: String, diary: String): String {
        val sys = """你每天给用户的日记或生活写一段点评，像一位温柔的陪伴者。只输出 json：{"review":"点评"}
规则：先具体地肯定和感谢今天做到的小事（引用记录或日记里的细节）；再轻轻提一个值得留意的地方（比如休息、情绪、节奏），没有就不提；最后用一句暖暖的话收尾。4 到 6 句，中文，不说教。
$GRATITUDE"""
        val user = "今天的记录：\n" + dayContext + (if (diary.isNotBlank()) "\n\n用户的日记：\n" + diary else "\n\n（用户今天没写日记，请只点评生活记录）")
        return chatJson(p, sys, user, temp = 0.8).optString("review").trim()
    }

    // ============ 朋友圈 ============
    data class Reaction(val friend: String, val like: Boolean, val comment: String)

    private fun friendLines(fs: List<Friend>): String =
        fs.joinToString("\n") { "- id=" + it.id + "；名字：" + it.name + "；和用户的关系：" + it.relation.ifBlank { "朋友" } + "；人设：" + it.persona.ifBlank { "普通朋友" } }

    /** 一条动态发出去后，每个好友按自己的人设决定点不点赞、评论什么 */
    suspend fun momentReactions(p: Prefs, friends: List<Friend>, authorDesc: String, text: String, img: String?): List<Reaction> {
        val sys = """你在扮演用户朋友圈里的一群好友，他们刷到了一条动态。只输出 json：{"reactions":[{"friend":"好友id","like":true,"comment":""}]}
好友们：
${friendLines(friends)}
规则：
1. 每个好友各自按人设和与用户的关系决定：点不点赞、要不要评论、评论什么。像真实的朋友圈，不是每个人都评论，至少要有一个人点赞。
2. 评论要短（5 到 30 字），口语，符合各自说话方式；不同人的评论不要雷同；可以用表情；不要说教。
3. 没话说就 comment 留空字符串。friend 必须是上面给出的 id。"""
        val user = "动态作者：" + authorDesc + "\n动态内容：" + text.ifBlank { "（只有图片）" } + (if (img != null) "\n（动态里带了一张图片，见附图）" else "")
        val o = chatJson(p, sys, user, img, 0.9)
        val arr = o.optJSONArray("reactions") ?: return emptyList()
        val ids = friends.map { it.id }.toSet()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.getJSONObject(i)
            val id = r.optString("friend")
            if (id in ids) Reaction(id, r.optBoolean("like", false), r.optString("comment").trim()) else null
        }
    }

    /** 用户回复了好友的评论 / 动态，好友用自己的口吻回一句 */
    suspend fun momentReply(p: Prefs, f: Friend, postText: String, thread: String, userText: String): String {
        val sys = """你在扮演「${f.name}」，和用户的关系：${f.relation.ifBlank { "朋友" }}；人设：${f.persona.ifBlank { "普通朋友" }}。
用户在朋友圈里回复了你，请用你的口吻回一句。5 到 40 字，口语、自然，不说教，可以带表情。只输出 json：{"reply":"回复"}"""
        val user = "那条动态：" + postText + "\n之前的对话：\n" + thread.ifBlank { "（无）" } + "\n用户刚刚说：" + userText
        return chatJson(p, sys, user, temp = 0.9).optString("reply").trim()
    }

    /** 好友自己发一条朋友圈（只有文字） */
    suspend fun friendPost(p: Prefs, f: Friend, others: String): String {
        val hour = java.time.LocalTime.now().hour
        val sys = """你在扮演「${f.name}」，和用户的关系：${f.relation.ifBlank { "朋友" }}；人设：${f.persona.ifBlank { "普通朋友" }}。
现在是 ${hour} 点左右。请发一条朋友圈动态：只有文字，1 到 3 句，像真实的朋友圈（日常、心情、吐槽、分享都行），符合你的人设和说话方式，不要提图片或照片，不要写「发图」。每次的话题要不一样（随机种子 ${System.nanoTime() % 9973}）。只输出 json：{"text":"动态内容"}"""
        return chatJson(p, sys, "最近朋友圈里别人发过的（避免重复）：\n" + others.ifBlank { "（无）" }, temp = 1.0).optString("text").trim()
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
