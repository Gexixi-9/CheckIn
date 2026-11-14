# 打卡 CheckIn v0.1（原生安卓，Kotlin + Jetpack Compose）

## 怎么得到 APK（不用装 Android Studio）
1. 在 GitHub 新建仓库（比如 `checkin`），把这个文件夹里的**所有内容**传上去（注意 `.github` 文件夹也要传；`app/src/main/assets/audio` 有110个文件，网页上传一次最多100个，分两次传，或用 git/GitHub Desktop 一次推上去）。
2. 仓库页面点 **Actions** → 选 `Build APK` → 等 3~6 分钟变绿。
3. 点进这次运行，最下面 **Artifacts** 下载 `checkin-debug-apk`，解压得到 `app-debug.apk`，传到手机安装（要允许「安装未知来源应用」）。
4. 如果变红，把报错那一段文字发给我，我来改。

## 用法
- 长按桌面 → 小组件 → 「打卡」→ 「标签计时」，点标签开始/结束；
- App 里 设置 → 填 DeepSeek Key（AI拆分）和硅基流动 Key（任务语音播报），两个互相独立。
