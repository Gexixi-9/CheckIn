package com.gexixi.checkin

import java.util.UUID

fun uid(): String = UUID.randomUUID().toString().take(8)

// ---------- 生活记录（只记一天去哪了，不发小红花，不进任务） ----------
data class Tag(
    val id: String, val name: String, val emoji: String,
    val group: String,          // 小组件左边的分组
    val color: Int,             // 色板序号 0..11
    val repov: Boolean = false  // 结束后显示「去 repov 记录」
)
data class Session(val id: String, val tagId: String, val start: Long, val end: Long? = null, val auto: Boolean = false)

// ---------- 任务系统（与生活记录的标签/数据完全分开） ----------
data class TaskList(val id: String, val name: String, val emoji: String, val color: Int)
data class Sub(val text: String, val done: Boolean = false, val stars: Int = 1)
data class Task(
    val id: String = uid(),
    val title: String,
    val note: String = "",
    val url: String = "",
    val listId: String = "l1",
    val goal: String = "",
    val condition: String = "",
    val subs: List<Sub> = emptyList(),
    val stars: Int = 1,
    val flowers: Int = 1,
    val date: String = "",         // 空 = 不限日期
    val time: String = "",         // 空 = 当天任意时间；只用于排序/显示，不弹通知
    val repeat: Int = 0,           // 0不重复 1天 2周 3月 4年
    val every: Int = 1,            // 每隔 N 个单位
    val weekdays: List<Int> = emptyList(), // 每周时选周几，1=周一..7=周日
    val repeatEnd: String = "",    // 空 = 一直重复
    val reward: String = "",       // 完成后的奖励愿望名
    val rewardWishId: String = "",
    val done: Boolean = false,
    val doneAt: Long = 0,
    val doneDates: List<String> = emptyList(),
    val timerStart: Long = 0,      // 任务自己的计时（与生活记录分开）
    val timerSub: Int = -1,
    val workMs: Long = 0
)

// ---------- 心愿超市 ----------
data class Wish(
    val id: String = uid(),
    val name: String,
    val cost: Int,
    val stars: Int = 3,
    val photo: String = "",
    val kind: Int = 0,            // 0 自建心愿（花小红花兑换） 1 任务奖励（完成任务直接领取）
    val status: Int = 0,          // 0 进行中/待领取  1 已实现/已领取
    val doneDate: String = "",
    val fromTask: String = ""
)
data class FlowerLog(val ts: Long, val n: Int, val taskId: String)
data class AutoMap(val pkg: String, val tagId: String)
data class Diary(val date: String, val files: List<String> = emptyList(), val text: String = "")

data class Prefs(
    val character: String = "random",
    val flower: String = "♥",
    val taskStarFlowers: List<Int> = listOf(1, 2, 3, 4, 5),
    val wishStarFlowers: List<Int> = listOf(3, 6, 9, 12, 15),
    val rewardCap: Int = 10,
    val dailyLimit: Int = 0,      // 每日最多兑换几个心愿，0 = 不限
    val dailyGoal: Int = 5,       // 每天小红花目标（进度条用）
    // AI 拆分（DeepSeek）
    val dsBase: String = "https://api.deepseek.com",
    val dsKey: String = "",
    val dsModel: String = "deepseek-chat",
    val dsVision: String = "deepseek-v4-flash-vision-exp",
    // 任务发放语音播报（硅基流动）——与 AI 设置完全分开
    val announceOn: Boolean = true,
    val ttsUrl: String = "https://api.siliconflow.cn/v1/audio/speech",
    val ttsKey: String = "",
    val ttsModel: String = "FunAudioLLM/CosyVoice2-0.5B",
    val ttsVoice: String = "FunAudioLLM/CosyVoice2-0.5B:alex",
    val ttsTemplate: String = "新任务：{任务}。目标：{目标}。第一步：{第一步}。",
    // 自动记录 App 使用
    val autoOn: Boolean = false,
    val minSec: Int = 10,
    val autoMap: List<AutoMap> = defaultAutoMap()
)

val GROUPS = listOf("生活", "护理", "工作", "社交", "娱乐", "考研", "论文")
val ALL_GROUPS = GROUPS + listOf("手机")

fun defaultAutoMap() = listOf(
    AutoMap("com.ss.android.ugc.aweme", "a_douyin"),
    AutoMap("com.xingin.xhs", "a_xhs"),
    AutoMap("tv.danmaku.bili", "a_bili"),
    AutoMap("com.tencent.mm", "a_wechat"),
    AutoMap("com.netease.cloudmusic", "e_music")
)

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
    val sumOffset: Int = 0
)
