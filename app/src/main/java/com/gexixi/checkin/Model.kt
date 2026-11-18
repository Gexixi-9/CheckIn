package com.gexixi.checkin

import java.util.UUID

fun uid(): String = UUID.randomUUID().toString().take(8)

// 所有数据类的每个参数都带默认值：这样旧版本存下来的 JSON 缺字段时，Gson 会用默认值，不会变成 null 闪退。

// ---------- 生活记录（只记一天去哪了，不发金币，不进任务） ----------
data class Tag(
    val id: String = "", val name: String = "", val emoji: String = "⭐",
    val group: String = "生活",  // 分组
    val color: Int = 6,          // 色板序号 0..11
    val repov: Boolean = false   // 结束后显示「去 repov 记录」
)

data class Session(
    val id: String = "", val tagId: String = "", val start: Long = 0L, val end: Long? = null,
    val auto: Boolean = false,
    val note: String = "",                       // 当时的所思所感
    val photos: List<String> = emptyList(),      // 当时的照片（拍的或相册选的）
    val emoji: String = "",                      // 单条记录改过的图标（空 = 用标签的）
    val title: String = ""                       // 单条记录改过的名称（空 = 用标签的）
)

// ---------- 任务系统（与生活记录的标签/数据完全分开） ----------
data class TaskList(val id: String = "", val name: String = "", val emoji: String = "📌", val color: Int = 6)
data class Sub(val text: String = "", val done: Boolean = false, val stars: Int = 1)

data class Task(
    val id: String = uid(),
    val title: String = "",
    val note: String = "",
    val url: String = "",
    val listId: String = "l1",
    val goal: String = "",         // 状态目标：做完之后「我学会了什么 / 能做到什么」
    val condition: String = "",    // 达成条件（可检验）
    val subs: List<Sub> = emptyList(),
    val stars: Int = 1,
    val flowers: Int = 1,          // 预设金币数（字段名沿用旧版，避免丢数据）
    val date: String = "",         // 空 = 不限日期
    val time: String = "",         // 只用于排序/显示，不弹通知
    val repeat: Int = 0,           // 0不重复 1天 2周 3月 4年
    val every: Int = 1,
    val weekdays: List<Int> = emptyList(),
    val repeatEnd: String = "",
    val reward: String = "",       // 完成后的奖励（宝箱）
    val rewardWishId: String = "",
    val done: Boolean = false,
    val doneAt: Long = 0,
    val doneDates: List<String> = emptyList(),
    val timerStart: Long = 0,
    val timerSub: Int = -1,
    val workMs: Long = 0,
    val startState: Int = 0,       // 启动状态：0未设 1高压高能 2高压低能 3低压低能 4低压高能
    val doneNote: String = "",     // 完成时写的感想
    val settled: Int = -1,         // 结算后真正发放的金币（-1 = 还没结算）
    val settleComment: String = "" // AI 结算评语
)

// ---------- 游戏商城 ----------
data class Wish(
    val id: String = uid(),
    val name: String = "",
    val cost: Int = 0,
    val stars: Int = 3,
    val photo: String = "",
    val emoji: String = "",       // 没有图片时，展示柜圆圈里放的表情
    val kind: Int = 0,            // 0 自建商品（花金币兑换） 1 任务宝箱（完成任务直接领取）
    val status: Int = 0,          // 0 进行中/待领取  1 已实现/已领取
    val doneDate: String = "",
    val fromTask: String = ""
)

data class FlowerLog(val ts: Long = 0L, val n: Int = 0, val taskId: String = "")
data class AutoMap(val pkg: String = "", val tagId: String = "")
data class Diary(val date: String = "", val files: List<String> = emptyList(), val text: String = "")
data class NotionLink(val key: String = "", val name: String = "", val emoji: String = "", val url: String = "")

data class Prefs(
    val character: String = "random",
    val flower: String = "♥",             // 旧版字段，不再使用
    val coin: String = "🪙",
    val taskStarFlowers: List<Int> = listOf(1, 2, 3, 4, 5),
    val wishStarFlowers: List<Int> = listOf(3, 6, 9, 12, 15),
    val rewardCap: Int = 10,
    val dailyLimit: Int = 0,
    val dailyGoal: Int = 5,
    // AI 拆分（DeepSeek）
    val dsBase: String = "https://api.deepseek.com",
    val dsKey: String = "",
    val dsModel: String = "deepseek-chat",
    val dsVision: String = "deepseek-v4-flash-vision-exp",
    val settleAi: Boolean = true,         // 完成任务时让 AI 结算金币
    // 任务发放语音播报（硅基流动）——与 AI 设置完全分开
    val announceOn: Boolean = true,
    val ttsUrl: String = "https://api.siliconflow.cn/v1/audio/speech",
    val ttsKey: String = "",
    val ttsModel: String = "FunAudioLLM/CosyVoice2-0.5B",
    val ttsVoice: String = "FunAudioLLM/CosyVoice2-0.5B:alex",
    val ttsTemplate: String = "新任务：{任务}。目标：{目标}。第一步：{第一步}。",
    // 自动记录 App 使用
    val autoOn: Boolean = false,
    val minSec: Int = 10,                 // 旧版字段，不再使用
    val minMin: Int = 5,                  // 少于几分钟不记录
    val autoMap: List<AutoMap> = defaultAutoMap(),
    // Notion 联动
    val notion: List<NotionLink> = defaultNotion()
)

val GROUPS = listOf("生活", "护理", "工作", "社交", "娱乐", "考研", "论文")
val ALL_GROUPS = GROUPS + listOf("手机")

/** 分组的英文名、代表图标、代表色（标签组展开时用） */
fun groupEn(g: String): String = when (g) {
    "生活" -> "Life"; "护理" -> "Care"; "工作" -> "Work"; "社交" -> "Social"
    "娱乐" -> "Fun"; "考研" -> "Study"; "论文" -> "Thesis"; "手机" -> "Phone"; else -> g
}

fun groupEmoji(g: String): String = when (g) {
    "生活" -> "🏠"; "护理" -> "🧴"; "工作" -> "💼"; "社交" -> "👥"
    "娱乐" -> "🎮"; "考研" -> "📚"; "论文" -> "📝"; "手机" -> "📱"; else -> "⭐"
}

fun groupColor(g: String): Int = when (g) {
    "生活" -> 4; "护理" -> 10; "工作" -> 6; "社交" -> 8
    "娱乐" -> 7; "考研" -> 2; "论文" -> 5; "手机" -> 0; else -> 11
}

val STATE_NAMES = listOf("未设", "高压·高能", "高压·低能", "低压·低能", "低压·高能")
val STATE_HINTS = listOf(
    "",
    "想法很多，所有步骤都挤在脑子里。先别设计计划：把想的事情全倒出来，再挑一件最想干的。",
    "光想到这个任务就觉得好累、很抗拒。别再系统拆解，只做一个两分钟内能完成的小动作。",
    "没什么焦虑，就是提不起兴趣。找这件事里最好玩的一小块入手。",
    "不用研究怎么完美地开始，直接动手做。"
)
val STATE_EMOJI = listOf("◻️", "🌪️", "🪨", "🌙", "🚀")

fun defaultAutoMap() = listOf(
    AutoMap("com.ss.android.ugc.aweme", "a_douyin"),
    AutoMap("com.xingin.xhs", "a_xhs"),
    AutoMap("tv.danmaku.bili", "a_bili"),
    AutoMap("com.tencent.mm", "a_wechat"),
    AutoMap("com.netease.cloudmusic", "e_music"),
    AutoMap("com.tencent.weread", "e_book"),     // 微信读书 → 看书（阅读系统）
    AutoMap("com.dragon.read", "a_novel")        // 番茄小说 → 看小说（小说品鉴）
)

/** Notion 联动：只要这几项。链接可以在设置里改。 */
fun defaultNotion() = listOf(
    NotionLink("podcast", "播客", "🎧", "https://app.notion.com/p/320a419f8dd7809d951ef5587c2a9217"),
    NotionLink("shop", "购物", "🎁", "https://app.notion.com/p/2c1a419f8dd7808ea701faa2af40a990"),
    NotionLink("read", "阅读", "📖", "https://app.notion.com/p/68aa419f8dd7827a93de816477e9f57e"),
    NotionLink("novel", "小说", "💌", "https://app.notion.com/p/320a419f8dd78072914ed542d8013d24")
)

/** 哪些标签结束后提示「去 Notion 写感想」→ 对应哪个 Notion 页面 */
fun notionKeyFor(tagId: String): String = when (tagId) {
    "e_podcast" -> "podcast"
    "a_shop", "e_walk" -> "shop"
    "e_book", "e_bedread" -> "read"
    "a_novel" -> "novel"
    else -> ""
}

private fun t(id: String, name: String, emoji: String, group: String, color: Int, repov: Boolean = false) =
    Tag(id, name, emoji, group, color, repov)

fun defaultTags(): List<Tag> = listOf(
    // 生活
    t("l_breakfast", "早餐", "🍳", "生活", 5), t("l_lunch", "午餐", "🍜", "生活", 5),
    t("l_dinner", "晚餐", "🌮", "生活", 5), t("l_cook", "下厨", "🍲", "生活", 5),
    t("l_shop", "买菜", "🛒", "生活", 5), t("l_commute", "通勤", "🚃", "生活", 5),
    t("l_bath", "洗澡", "🛁", "生活", 5), t("l_chores", "做家务", "🧹", "生活", 5),
    t("l_ready", "出门准备", "💄", "生活", 5),
    // 护理
    t("c_skin", "护肤保养", "🧴", "护理", 10), t("c_face", "面部护理", "🧖", "护理", 10),
    t("c_nail", "美甲", "💅", "护理", 10), t("c_med", "冥想", "🧘", "护理", 10),
    t("c_sleep", "睡觉", "😴", "护理", 7),
    // 工作
    t("w_work", "工作", "💼", "工作", 6),
    // 社交
    t("s_colleague", "同事聚会", "👥", "社交", 8), t("s_relative", "亲友聚会", "👪", "社交", 8),
    t("s_friends", "与朋友聚餐", "👯", "社交", 8), t("s_video", "给家人打视频", "📲", "社交", 8),
    t("s_class", "同学聚会", "🧑‍🎓", "社交", 8), t("s_family", "家人", "🏠", "社交", 8),
    t("s_volunteer", "参加志愿者活动", "🙌", "社交", 8),
    // 娱乐
    t("e_game", "打游戏", "🎮", "娱乐", 9), t("e_drama", "看剧", "📺", "娱乐", 9, true),
    t("e_movie", "看电影", "🎬", "娱乐", 9, true), t("e_paint", "画画", "🎨", "娱乐", 9),
    t("e_walk", "逛街", "🛍️", "娱乐", 9), t("e_music", "听歌", "🎧", "娱乐", 9),
    t("e_podcast", "播客", "🎙️", "娱乐", 9), t("e_book", "看书", "📖", "娱乐", 4, true),
    t("e_bedread", "睡前阅读", "🌙", "娱乐", 4, true),
    // 考研 / 论文
    t("k_review", "复习考试", "📚", "考研", 2), t("k_homework", "做功课", "✍️", "考研", 2),
    t("k_skill", "学新技能", "🎓", "考研", 2),
    t("p_write", "写论文", "📝", "论文", 7),
    // 手机（自动记录用，也可以手动点）
    t("a_douyin", "刷抖音", "🎵", "手机", 0), t("a_xhs", "刷小红书", "📕", "手机", 0),
    t("a_bili", "刷B站", "📺", "手机", 0), t("a_wechat", "微信", "💬", "手机", 0),
    t("a_novel", "看小说", "📱", "手机", 4), t("a_shop", "逛购物平台", "🛒", "手机", 0)
)

/** 进度卡默认的 10 个最常做的事（不够 10 个常用的时候拿来补） */
val QUICK_DEFAULT = listOf(
    "l_breakfast", "l_lunch", "l_dinner", "l_commute", "l_bath",
    "c_skin", "w_work", "k_review", "e_book", "c_sleep"
)

fun defaultLists(): List<TaskList> = listOf(
    TaskList("l1", "学习", "📚", 6),
    TaskList("l2", "生活", "🏠", 5),
    TaskList("l3", "工作", "💼", 9),
    TaskList("l4", "论文", "📝", 7)
)

data class Data(
    val tags: List<Tag> = defaultTags(),
    val sessions: List<Session> = emptyList(),
    val lists: List<TaskList> = defaultLists(),
    val tasks: List<Task> = emptyList(),
    val wishes: List<Wish> = emptyList(),
    val earned: Int = 0,
    val spent: Int = 0,
    val flowerLog: List<FlowerLog> = emptyList(),
    val currentId: String = "",
    val diary: List<Diary> = emptyList(),
    val prefs: Prefs = Prefs(),
    val widgetGroup: String = "生活",
    val sumOffset: Int = 0,
    val weather: Map<String, String> = emptyMap(),   // 日期 → 天气 emoji（手动选）
    val shopOpen: Boolean = false,                   // 商城开关卡的状态
    val ver: Int = 0
)
