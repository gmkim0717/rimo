# CLAUDE.md

本文件是本仓库的最高优先级指令。每次会话开始必须先读 `specs/000-constitution.md`。

---

## 1. 项目是什么

Android 电视盒子上的 IPTV 播放器。**不上架应用商店,自己侧载给认识的人用。**

用户自带 m3u 播放列表 URL 或 Xtream Codes 账号。app 本身不含任何频道源。

**开发约束(直接影响你的技术建议):**
- 单人开发,每周 5–10 小时,无收入。任何"以后再重构"的方案默认拒绝,因为没有以后。
- 优先选生态默认方案,不选更优雅但小众的方案。踩坑时间比省下的代码贵。
- 范围固定。不在 spec 里的功能一律进 backlog,不顺手实现。
- 用户是一份具体的名单,不是市场。需求不明确时正确做法是"去问他们"。

---

## 2. SDD 工作流(强制)

四个阶段,**每个阶段之间必须停下来等我确认**。禁止一次跑完多个阶段。

```
/specify  →  spec.md    写清楚做什么、为什么、验收标准    ← 不出现任何技术名词
    ⏸ 等我确认
/plan     →  plan.md    技术方案、数据模型、模块边界      ← 不写实现代码
    ⏸ 等我确认
/tasks    →  tasks.md   拆成可独立验证的任务清单
    ⏸ 等我确认
/implement            按 tasks.md 顺序实现,每完成一个任务停下来报告
```

**违反工作流的典型行为(不要做):**
- 我说"做个收藏功能",你直接开始写 Kotlin → 错。应该先 `/specify`。
- spec 阶段写"用 Room 存收藏" → 错。Room 是技术选型,属于 plan。
- plan 通过后一口气实现完 8 个任务才回来 → 错。一个任务一停。
- 发现 spec 有问题就自己改了继续做 → 错。停下来告诉我,我决定。

### 目录约定

```
specs/
  000-constitution.md          项目宪法,不可绕过
  001-self-update/             自动更新(优先级最高,见宪法第四条)
  002-m3u-playlist/
  003-player-core/
  004-korean-epg/
```

编号顺序即依赖顺序。开新 feature 前确认前置 feature 已完成。

---

## 3. 硬性禁止事项

这几条没有例外,也不需要问我:

1. **仓库内不得出现任何真实的频道源 URL、m3u 播放列表文件、Xtream 服务商地址或账号。**
   测试 fixture 一律使用 `https://example.invalid/...` 这类保留域名。
   不上架不改变这条。
2. 不得内置任何默认播放列表、"推荐源"、"发现频道"之类的功能入口。
3. 不得在 app 内实现任何源的抓取、爬取或代理转发。用户输入什么就播什么。
4. 不得提交 keystore、`local.properties`、API key。检查 `.gitignore` 覆盖后再 commit。
5. 不得引入任何依赖 Google Play Services 的能力。目标设备大多没有 GMS。

---

## 4. 技术栈

| 项 | 选型 |
|---|---|
| 语言 | Kotlin,JDK 17 |
| UI | **View 体系**(AppCompat + RecyclerView,列表界面起加 Leanback)。不用 Compose。决策见下方 |
| 播放 | Media3 / ExoPlayer,HLS + DASH,开启软解回落 |
| 本地存储 | Room(频道、播放列表、EPG 缓存) |
| 设置 | DataStore (Preferences) |
| DI | Hilt |
| 异步 | Coroutines + Flow |
| 网络 | OkHttp |
| minSdk / targetSdk | 28 (Android 9) / 34。实际用户设备:Android 9 和 11 |
| 测试 | JUnit4 + Turbine(Robolectric 需要时再加) |
| applicationId | `com.rimo.player`(首次分发后不可更改) |

**架构**:单 module 起步,`data / domain / ui` 三层分包。不要提前拆 module。

### UI 栈决策(已定,2026-09-05)

**View 体系 + RecyclerView + Leanback,minSdk 28。** 依据:名单上的盒子是 Amlogic S905L3A,Android 9 和 11 都有,**其中有 1GB 内存的机器**。

原判断标准(保留作记录):

- Android 9+ 且内存 ≥ 2GB → Compose for TV
- Android 7–8 或内存 1GB → View 体系

弱盒子上 Compose 的列表滚动和焦点动画会掉帧,这不是优化能补的,是架构选择。性能基线按 1GB 那台定。

---

## 5. 目标设备:国产安卓盒子

不是 Google TV,不是 Shield。以下每一条都是实际会翻车的地方:

**图标出不来** —— 没有 Leanback launcher。Manifest 两个 category 都要声明:

```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
    <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
</intent-filter>
```

并且:

```xml
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
```

**解码兜底** —— 便宜盒子常缺 HEVC 硬解,AV1 基本没有。必须配
`DefaultRenderersFactory.setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)`,
让不支持的编码回落软解。宁可卡也不要黑屏——黑屏时用户分不清是源挂了还是 app 坏了。

**遥控器键位** —— 国产遥控器几乎都没有 `KEYCODE_CHANNEL_UP/DOWN`。
不要把任何功能只绑在电视专用键上,所有操作必须能用方向键 + 确定 + 返回完成。
新盒子到手先用 `adb shell getevent -l` 把实际 keycode 记进 `docs/remote-keys.md`。

**D-pad 基本规则:**
- 一切交互必须能用方向键完成,没有触摸。写任何界面前先想清楚焦点怎么进、怎么出、怎么移。
- 焦点必须**可见**,用明确的边框或缩放,不要依赖系统默认高亮。
- `KEYCODE_BACK` = 返回上一层,不是退出 app。
- 四边留 5% overscan margin,老电视会切边。
- 文字最小 14sp,3 米观看距离下更小的看不清。

**直播流细节:**
- m3u 里的 `#EXTVLCOPT:http-user-agent=` / `http-referrer=` 必须解析并透传给
  `DefaultHttpDataSource.Factory().setDefaultRequestProperties()`。
  这是最常见的"为什么 VLC 能播我不能播"。
- 源挂掉自动重连,指数退避,上限 5 次。
- 切台时释放上一个 player 实例,弱盒子内存吃不消两个。
- `#EXT-X-DISCONTINUITY` 在直播源里很常见,不是错误。

---

## 6. 分发与更新

不上架,所以更新要自己做。这是 MVP 的一部分,不是后续优化。

**自更新机制:**
- 静态托管一个 `update.json`:`versionCode` / `versionName` / `apkUrl` / `changelog`
- app 启动时拉取比对,有新版下载后调 `PackageInstaller` 弹安装确认
- 需要 `REQUEST_INSTALL_PACKAGES` 权限,不上架所以无所谓
- 下载失败静默重试,不打扰用户

**首次安装**:用 Downloader (`com.esaba.downloader`),报一串短 URL 让对方自己输,不用上门。盒子装不了 Downloader 的走 U 盘或 `adb install`。

**keystore 必须异地备份。** 自签名 APK,keystore 丢了就发不了更新,所有人得卸载重装。生成后立刻抄一份到别的地方,并把这件事记进 `docs/release.md`。

`versionCode` 每次发布必须递增,否则 `PackageInstaller` 会拒绝安装。

---

## 7. 常用命令

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
./gradlew installDebug

adb connect 192.168.x.x:5555     # 盒子局域网调试
adb shell input keyevent 20      # 模拟 D-pad DOWN,调焦点用
adb shell getevent -l            # 抓遥控器实际 keycode
adb logcat -s ExoPlayerImpl      # 只看播放器日志
```

**盒子摸底(UI 栈决策依赖这个):**

```bash
adb shell getprop ro.build.version.release
adb shell cat /proc/meminfo | head -1
adb shell getprop ro.product.model
adb shell pm list packages | grep -c gms
adb shell pm list packages | grep leanback
```

**环境**:在 Mac (Apple Silicon) 上开发。不要在 WSL2 里跑 Gradle 和 adb,USB 和网络转发会持续出问题。

---

## 8. 代码约定

- 代码、标识符、注释、commit message 一律英文。和我对话用中文。
- Commit 用 Conventional Commits:`feat(playlist): parse EXTVLCOPT headers`
- 一个 commit 对应 tasks.md 里的一个任务。任务没做完不 commit。
- 公开函数写 KDoc,私有的不用。不写"// 设置变量 x"这种注释。
- 新增依赖前先问我。每个依赖都是未来的维护负担。

**测试按层区分,不要全覆盖:**
- 解析器(m3u / XMLTV / 频道名匹配)→ 必须有单测,最容易出 bug 也最容易测
- ViewModel → 关键状态流转测
- UI → 不写单测,手动在真机上验。模拟器验不出弱盒子的性能问题

---

## 9. 和我协作的方式

- 有歧义就问,不要猜完了往下做。返工比多问一句贵。
- 不同意我的技术判断就直说,给出理由。我要的是能跑的东西,不是顺从。
- 每次改动完告诉我:改了什么、为什么、我需要手动验证什么(具体到按哪个键、看什么现象)。
- 不要为了显得完整而输出大段我没要的代码。
