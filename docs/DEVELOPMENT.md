# 开发说明

## 构建环境

- JDK 17。
- Android SDK Platform 35、Build Tools 35.0.0。
- Gradle Wrapper 8.13、Android Gradle Plugin 8.13.0。
- Release applicationId：`me.leon.tvlauncher`；Debug applicationId：`me.leon.tvlauncher.debug`；minSdk 29，targetSdk / compileSdk 35。

先安装 JDK、Android SDK 并阅读接受相关 SDK 许可，设置 `JAVA_HOME` 和 `ANDROID_HOME` 指向实际目录。然后在项目根目录运行：

```sh
./scripts/build.sh
```

默认执行 Debug APK、Lint 和设备测试 APK 构建。首次构建需要联网下载 Gradle 和插件依赖。脚本也可使用本地 `.tools/` 工具目录，但源码包不包含 JDK、SDK 或缓存，不会自动安装 JDK 或接受 SDK 许可。

| 输出 | 路径 |
|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 设备测试 APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| Debug Lint | `app/build/reports/lint-results-debug.html` |

Debug 密钥由构建脚本首次生成在 `.tools/debug.keystore`，仅供测试。

## 正式签名与版本

通过环境变量提供私有签名配置，密钥和密码应在源码目录之外保存：

| 变量 | 含义 |
|---|---|
| `TVLAUNCHER_KEYSTORE` | PKCS12 密钥文件绝对路径 |
| `TVLAUNCHER_STORE_PASSWORD` | 密钥库密码 |
| `TVLAUNCHER_KEY_PASSWORD` | 私钥密码 |
| `TVLAUNCHER_KEY_ALIAS` | 私钥别名，默认 `tvlauncher-release` |

在本地安全地准备上述环境变量后执行：

```sh
./scripts/build.sh :app:assembleRelease :app:lintRelease
```

输出：`app/build/outputs/apk/release/app-release.apk`。发布前使用 SDK Build Tools 中的 `apksigner verify --verbose --print-certs` 检查签名，并用 `zipalign -c -P 16 4` 检查对齐。

当前版本为 0.1.0 / versionCode 1。后续更新递增 versionCode，并继续使用相同签名密钥。源码公开不需要公开私钥或密码。分发 APK 时同时提供实际用于构建该版本的完整对应源码、构建脚本及许可文件。

## 代码结构与实现

| 文件 | 职责 |
|---|---|
| `MainActivity.java` | 首页、列表、壁纸抽屉、权限和焦点状态协调 |
| `AppCatalog.java` | 应用发现、同包名去重、排序和图标代表色缓存 |
| `WallpaperStore.java` | 图片查询、解码和壁纸私有副本写入 |
| `WallpaperBackdropView.java` | 静态壁纸模糊及背景遮罩 |
| `RevealScrollView.java`、`GutterScrollFrame.java`、`ScrollBounds.java` | 焦点揭示、裁切与滚动边界 |
| `FocusFeedback.java`、`FocusScrollMotion.java`、`FocusRules.java` | 焦点动画、滚动和导航规则 |
| `ActionButton.java`、`AppCardView.java`、`ClockTextView.java` | 按钮、应用卡片和原生文字绘制 |

项目使用 Java 17 和系统 Activity / View、ScrollView、GridLayout、ImageView、TextView，无第三方运行时库。设计尺寸按窗口宽度 / 1920 换算，采用 ViewPropertyAnimator / ValueAnimator 实现过渡。

壁纸由 ImageDecoder 解码并限制尺寸，通过 AtomicFile 保存私有副本；缩略图使用 8 MB 缓存。Android 10 使用文件扫描，其他支持版本使用 MediaStore 查询固定目录。没有私有图片时返回空 Bitmap，由现有灰色底层绘制背景。

模糊沿用 Android 10 可用的 ScriptIntrinsicBlur / RenderScript，生成最长 480px 宽的静态缓存；列表标题随内容滚动。RenderScript 自 Android 12 起弃用，较新系统的替代方案 RenderEffect 需要 API 31，当前尚未迁移。现有实现兼顾已验证的 Android 10 环境；新系统仍需单独测试。4K 面板不保证应用窗口以 4K 渲染，输出取决于电视系统提供的窗口分辨率。

## 安装和设备测试

准备 Platform Tools，将 `TV_ADDRESS` 替换为电视实际调试地址：

```sh
adb connect TV_ADDRESS
adb -s TV_ADDRESS install -r app/build/outputs/apk/debug/app-debug.apk
adb -s TV_ADDRESS shell am start -n me.leon.tvlauncher.debug/me.leon.tvlauncher.MainActivity
```

新版 Debug 使用独立包名和名称“TV Launcher 测试版”，可与正式版共存，两版数据各自保存。以上更新安装适用于签名兼容的新版 Debug 安装。早期 Debug 使用正式包名 `me.leon.tvlauncher`，仍与正式签名冲突；首次迁移前须备份并征得设备所有者同意，卸载会删除私有数据。

本地滚动边界检查：

```sh
./scripts/check-rendering.sh
```

脚本使用 `JAVA_HOME`，未设置时尝试本地 `.tools/jdk/Contents/Home`。现有 14 项检查覆盖边界计算，不能代替真机焦点和视觉验收。

设备自动测试面向 Debug 构建：

```sh
adb -s TV_ADDRESS install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s TV_ADDRESS shell am force-stop me.leon.tvlauncher.debug
adb -s TV_ADDRESS shell am instrument -w \
  me.leon.tvlauncher.debug.test/me.leon.tvlauncher.LauncherSmokeInstrumentation
```

测试会操作应用界面并临时打开系统设置；请在获得设备所有者同意后执行。Debug 测试 APK 不应直接用于验收正式签名版。正式包另需验证全新安装、灰色背景、权限拒绝和授权、空目录、壁纸持久化、遥控连按、应用返回、默认 Home 和重启。当前验证结果见 [发布验证](release-validation.md)。

## 官方参考

- [Android TV 应用入口](https://developer.android.com/training/tv/get-started/create)
- [方向键导航](https://developer.android.com/training/tv/get-started/navigation)
- [共享媒体与权限](https://developer.android.com/training/data-storage/shared/media)
- [命令行构建](https://developer.android.com/build/building-cmdline)
- [应用签名](https://developer.android.com/studio/publish/app-signing)
- [RenderScript 迁移](https://developer.android.com/guide/topics/renderscript/migrate)
