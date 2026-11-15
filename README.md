# 当下（原生安卓，Kotlin + Jetpack Compose）

## 在 Android Studio 里看和改
1. File → Open，选这个文件夹，等 Gradle 同步完。
2. 打开 `Preview.kt`（或任何 *Screen.kt），右上角切到 **Split**，右边就是实时预览；改完点预览窗格的刷新。
3. 连手机（USB 调试）点绿色三角，直接装到手机上。

## 用 GitHub 自动编译 APK
推到仓库后，Actions → Build APK → 下载 Artifacts 里的 app-debug.apk。

## 文件说明
- `Model.kt` 数据结构 + 默认标签/色板映射　`Repo.kt` 所有读写逻辑（计时、任务、重复、小红花、心愿、日记、备份）
- `TodayScreen.kt` 当前任务卡片 / 待办 / 任务详情 / AI 拆分 / 完成弹窗
- `RecordsWishes.kt` 生活记录 + 心愿超市　`CalendarScreen.kt` 日历 + 日记　`SettingsScreen.kt` 设置
- `Tracker.kt` 后台服务（通知栏常驻 + 自动记录 App）　`Widgets.kt` 三个桌面小组件
- `Media.kt` 鼓励音频 + 任务播报语音　`Ai.kt` DeepSeek 与硅基流动接口
- 密钥只在设置页填，存手机本地，源码里没有。
