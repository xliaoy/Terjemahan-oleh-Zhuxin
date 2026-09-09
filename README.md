# 🪟 株馨翻译器（FloatingTranslate）

一个运行在 Android 手机上的**悬浮翻译工具**：在任意 App 上方悬浮一个可拖动的窗口，把窗口对准屏幕上的外文，点击一下即可识别并翻译，译文直接显示在悬浮窗里。支持**在线 AI 翻译**与**离线本地翻译**双模式，全程无需切换应用、无需复制粘贴。

> 应用名：株馨翻译器（ZhuXin Translator） · 包名：`com.zhuxin.Translation`

---

## 📥 下载安装

| 版本 | 说明 | 下载 |
| --- | --- | --- |
| **v1.0** | 已签名安装包（Android 8.0+，arm64-v8a，约 26MB） | [⬇️ 下载 APK](https://github.com/xliaoy/Terjemahan-oleh-Zhuxin/releases/download/v1.0/app-release-unsigned_sign.apk) |

> 也可以在 [GitHub Releases 页面](https://github.com/xliaoy/Terjemahan-oleh-Zhuxin/releases) 查看所有版本。直接下载安装即可使用；安装时如提示"未知来源"，允许本次安装即可。

---

## ✨ 功能介绍

| 功能 | 说明 |
| --- | --- |
| 🪟 **悬浮窗翻译** | 任意界面悬浮一个半透明窗口，点击窗口即翻译窗口覆盖区域的文字 |
| 📸 **屏幕区域截取** | 基于 MediaProjection 截取窗口所在屏幕区域，翻译完自动恢复 |
| 🔍 **OCR 引擎可选** | 本地 ML Kit（离线，拉丁/中日韩/印地文）或 AI 视觉在线（任意文字，需模型支持图片），首页一键切换 |
| 🌐 **翻译引擎可选** | 在线 AI（OpenAI 兼容接口）/ 离线本地模型，首页一键切换 |
| 📴 **离线本地翻译** | 腾讯混元 HY-MT1.5-1.8B 与 Qwen2.5-1.5B-Instruct（29 种语言）可选，llama.cpp 推理，断网可用 |
| 🖐 **手势操作** | 单击翻译 · 长按拖动窗口 · 上下滑动滚动译文 · 按住右下角手柄实时缩放识别区域 |
| 🫧 **悬浮球收纳** | 窗口可一键最小化为悬浮球，不遮挡内容，点按重新展开 |
| 🌍 **多语言支持** | 20 种语言任意互翻：自动检测、中、英、日、韩、法、德、西、俄、葡、意、泰、越、印尼、马来、阿拉伯、印地、土耳其、波兰、荷兰 |
| 🕘 **翻译历史** | 自动保存最近 50 条翻译记录（原文 / 译文 / 时间），支持回看 |
| 🎨 **界面可调** | 悬浮窗透明度、识别区域宽高均可实时调节并保存 |
| 🗣 **应用多语言** | 界面语言支持跟随系统 / 简体中文 / English |
| 🐞 **调试模式** | 识别失败时自动保存截图与日志到「下载」目录，方便排查 |
| 💥 **崩溃日志** | 全局异常捕获，崩溃堆栈自动写入「下载」与私有目录 |

---

## ⚙️ 工作原理

整体是一条 **「截屏 → OCR → 翻译 → 展示」** 的流水线，由两个前台服务协作完成：

```
┌────────────────────────────────────────────────────────────┐
│                      ScreenCaptureService                   │
│              （媒体投影前台服务，持有 MediaProjection）        │
│   截取悬浮窗所在屏幕区域 → 输出 Bitmap                          │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│                        OcrHelper                            │
│          ML Kit 本地文字识别（TextRecognition）                │
│          Bitmap → 文本（识别失败时按调试模式存截图）              │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
            ┌──────────────────────────────┐
            │        是否含拉丁字母？        │──── 否 ──→ 「未检测到外文」
            └──────────────┬───────────────┘
                           ▼ 是
       ┌───────────────────┴───────────────────┐
       ▼ 在线模式                              ▼ 离线模式
┌─────────────────────┐              ┌──────────────────────────┐
│      AiClient       │              │      LocalTranslator      │
│  OpenAI 兼容接口     │              │  llama.cpp + HY-MT1.5-1.8B │
│  /chat/completions  │              │  JNI 本地推理，无需联网     │
└──────────┬──────────┘              └────────────┬─────────────┘
           └────────────────┬────────────────────┘
                            ▼
┌────────────────────────────────────────────────────────────┐
│                   FloatingWindowService                     │
│   译文展示在悬浮窗内（可上下滑动看全文） → 写入翻译历史            │
└────────────────────────────────────────────────────────────┘
```

**详细流程**：

1. **授权**：首次开启需授予「悬浮窗」权限与「屏幕截图」（MediaProjection）权限。
2. **常驻**：`ScreenCaptureService` 取得 MediaProjection 后常驻后台，随时准备截屏；`FloatingWindowService` 在前台显示悬浮窗口。
3. **对准**：用户拖动窗口，让窗口覆盖要翻译的文字；窗口即识别区域（长按右下角手柄可实时缩放）。
4. **触发**：点击窗口 → 先隐藏窗口自身（避免截到自己），延迟 150ms 等画面刷新。
5. **截屏**：`ScreenCaptureService` 用 `VirtualDisplay + ImageReader` 截取窗口所在区域，跳过启动时可能出现的黑帧。
6. **OCR**：`OcrHelper` 用 ML Kit 本地识别文字；小图自动放大提升识别率；识别不到外文（拉丁字母）则提示并结束。
7. **翻译**：
   - **在线**：`AiClient` 以 `temperature=0.3` 调用 OpenAI 兼容的 `/chat/completions`，系统提示词要求只输出译文；
   - **离线**：`LocalTranslator` 通过 JNI 加载 `libllama_jni.so`，用 GGUF 格式的腾讯混元 HY-MT1.5-1.8B 模型本地推理（模型约 1GB，从 ModelScope 下载，带 GGUF 魔数校验）。
8. **展示**：译文显示在悬浮窗内，上下滑动查看全文，同时写入翻译历史（SharedPreferences，最多 50 条）。

> **语言自动检测**：OCR 识别到含拉丁字母即视为外文；「自动检测」时按字符所属 Unicode Script 判断是否中日韩字符，从而决定翻译方向（如中文 → 英语，其他 → 简体中文）。

---

## 🛠 开发环境

| 项目 | 版本 / 说明 |
| --- | --- |
| 开发工具 | Android Studio（建议最新稳定版） |
| 语言 | Java 8（`source/targetCompatibility 1.8`） |
| 构建工具 | Gradle 8.5 + Android Gradle Plugin 8.2.0 |
| SDK | compileSdk 34 / targetSdk 34 / minSdk 24 |
| JDK | 17 |
| 支持架构 | arm64-v8a（含 llama.cpp 等原生库） |

**依赖**：

- `androidx.appcompat:appcompat:1.6.1`
- `com.google.android.material:material:1.11.0`
- `androidx.constraintlayout:constraintlayout:2.1.4`
- `com.google.mlkit:text-recognition:16.0.0`（本地 OCR）
- `com.squareup.okhttp3:okhttp:4.12.0`（HTTP 客户端）

**构建**：

```bash
# 命令行构建（debug）
./gradlew assembleDebug

# 构建 release（APK 输出在 app/build/outputs/apk/）
./gradlew assembleRelease
```

或用 Android Studio 打开项目根目录，直接 Run。

---

## 📁 项目结构

```
fy/
├── build.gradle / settings.gradle / gradle.properties     # 根构建配置
├── gradle/wrapper/                                        # Gradle 8.5 Wrapper
└── app/
    ├── build.gradle                                       # 模块构建配置
    └── src/main/
        ├── AndroidManifest.xml                            # 权限与组件声明
        ├── java/com/zhuxin/Translation/
        │   ├── MainActivity.java                          # 主界面：开关、语言、AI 配置、打赏
        │   ├── FloatingWindowService.java                 # 悬浮窗服务（手势、缩放、翻译调度）
        │   ├── ScreenCaptureService.java                  # 屏幕截图前台服务（MediaProjection）
        │   ├── OcrHelper.java                             # ML Kit 本地文字识别
        │   ├── AiClient.java                              # OpenAI 兼容接口客户端
        │   ├── LocalTranslator.java                       # 离线翻译引擎（JNI + llama.cpp）
        │   ├── ModelDownloader.java                       # GGUF 模型流式下载
        │   ├── Prefs.java                                 # 设置与翻译历史的存储
        │   ├── LangUtil.java                              # 语言判断与映射工具
        │   ├── HistoryActivity.java                       # 翻译历史列表
        │   ├── FloatingTranslateApp.java                  # 应用入口（语言、崩溃捕获）
        │   ├── CrashHandler.java                          # 全局崩溃日志
        │   └── DebugLog.java                              # 调试日志（调试模式）
        ├── jniLibs/arm64-v8a/                             # llama.cpp 原生库（libllama/ggml/omp + JNI 封装）
        └── res/                                           # 布局、主题、中英文资源、打赏二维码
```

---

## 🧠 代码写法特点

- **回调式异步**：网络请求（OkHttp）与 OCR（ML Kit）均采用回调 + 主线程 `runOnUiThread` 回投结果，避免阻塞 UI，代码流程清晰。
- **单例服务**：`FloatingWindowService` / `ScreenCaptureService` 通过静态实例暴露给外部调用，`isReady()` 快速判断权限状态。
- **集中配置**：所有设置（AI 接口、语言、透明度、识别区域、历史）统一走 `Prefs`（SharedPreferences），悬浮窗实时读取、实时生效。
- **手势判重**：窗口触摸事件用 `touchSlop` + 延迟回调区分「单击翻译 / 长按拖动 / 上下滑动滚动」，避免误触。
- **黑帧防御**：截图后对整帧采样亮度，自动跳过 VirtualDisplay 刚启动时的全黑帧，保证 OCR 输入有效。
- **健壮性设计**：模型文件校验 GGUF 魔数防损坏、下载用 `.part` 临时文件防半截、崩溃堆栈双写（下载目录 + 私有目录）、调试截图自动归档。

---

## 💝 打赏支持

如果这个工具帮到了你，欢迎打赏支持一下作者，你的支持是持续维护的动力！🎉

| 微信 | 支付宝 |
| --- | --- |
| ![微信打赏](app/src/main/res/drawable/reward_wechat.png) | ![支付宝打赏](app/src/main/res/drawable/reward_alipay.png) |


---

## 📄 License

本项目仅供学习交流使用。离线翻译模型为腾讯混元 HY-MT1.5-1.8B（GGUF 格式），版权归原模型作者所有，请遵守其相应许可协议。
