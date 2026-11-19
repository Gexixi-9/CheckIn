# 当下 v3（原生安卓，Kotlin + Jetpack Compose）

在 Dangxia-v1 基础上改的第三版。包名、数据文件不变，直接覆盖安装，旧数据会自动升级（标签、记录、任务、商品、金币都保留）。

## 用 GitHub 自动编译 APK
推到仓库后，Actions → Build APK → 下载 Artifacts 里的 app-debug.apk。

## 文件说明
- `Model.kt` 数据结构、默认标签、分组英文名、启动状态、Notion 链接　`Repo.kt` 读写逻辑
- `Bento.kt` 便当格卡片 + 卡片展开动画　`Motion.kt` 按压回弹 / 弹性开关 / 跟手波纹 / 磁吸 / 兑换动效 / 宝箱图标
- `PlanScreens.kt` 规划页、当前任务卡（进度底色）、待办、启动状态、任务编辑、AI 拆分、结算窗、完成弹窗
- `RecordScreens.kt` 记录页、标签组叠放动画、标签编辑　`CalendarScreen.kt` 日历（双指缩放、点开详情、长按修改）+ 日记
- `Shop.kt` 游戏商城　`SettingsScreen.kt` 设置（便当格）　`Layout.kt` 日历错开算法（App 和小组件共用）
- `Tracker.kt` 后台服务（自动记录，少于 5 分钟不记，1 分钟内切回算同一段）　`Widgets.kt` 三个桌面小组件
- `Ai.kt` DeepSeek 状态目标拆分 / 自动填写 / 结算　`Media.kt` 鼓励音频 + 播报　`Notion.kt` 一键跳转
- 密钥只在设置页填，存手机本地，源码里没有。
- `PeoplePage.kt` 人物界面（规划页右边）　`ToggleActivity.kt` 桌面标签小组件结束后跳 Notion / repov 的透明中转页
