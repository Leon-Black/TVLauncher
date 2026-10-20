# TV Launcher

因为实在是受够了电视内置桌面的臃肿，刚好前几天在社交媒体上看到有人借助 AI 精简了自家电视的系统，受到启发，我也做了一个 TV 桌面。

这是个完全本地、清爽轻量的 TV 桌面，核心功能只有：
- 首页：应用 Dock、更换壁纸
- 应用列表

我做设计，界面设计有参考一点印象中的 Apple TV，交互设计由我自己完成。Codex 写代码。

## 截图

首页：
<img width="1920" height="1080" alt="TV Launcher-Home" src="https://github.com/user-attachments/assets/aae4d66d-94be-4de5-ab96-5a5d686d22ab" />

应用列表：
<img width="1920" height="1080" alt="TV Launcher-App List" src="https://github.com/user-attachments/assets/6e8d9219-495a-4c5f-ad9d-4675b6152075" />

## 兼容范围

- 最低 Android 10（API 29）。
- 验证设备是 Sony BRAVIA 4K UR2（Android 10）；没验证过其他品牌和系统版本。

## 安装

在[这里](https://github.com/Leon-Black/TVLauncher/releases/latest)下载安装包，在电视上使用文件管理器安装；如有提示，允许即可。也可使用 [ADB](docs/DEVELOPMENT.md#安装和设备测试) 的方式安装。

安装后，从电视现有桌面打开 TV Launcher。

## 壁纸位置和权限

首次打开时，桌面背景为灰色。TV Launcher 没有内置壁纸，需要自行把图片放入电视机的 **`Pictures/Wallpaper/`** 目录。常见完整路径为：

```text
/sdcard/Pictures/Wallpaper/
```

**壁纸需要放在 `Pictures/Wallpaper/` 路径下，支持读取其中的子文件夹。**

其他文件夹的图片不会出现在壁纸抽屉里。

第一次打开壁纸抽屉时，需要允许 TV Launcher 读取图片。

如果壁纸抽屉里没有任何图片，请检查权限和图片存放位置。

## 数据与网络

TV Launcher 会读取本机应用入口、名称和图标，并在获得授权后读取指定目录的图片。

TV Launcher 会在私有目录保存一份当前壁纸的副本。即使原图被删除，副本也会保留，直到更换其他壁纸。卸载或清除数据会删除私有副本。

无需联网，没有广告，不收集数据。

## 开发与许可证

构建环境、代码结构、签名配置和测试方式见 [开发说明](docs/DEVELOPMENT.md)。

Copyright (C) 2026 Leon。本项目原创代码采用 **GPL-3.0-only**，完整条款见 [LICENSE](LICENSE)。允许商业使用和分发，分发 APK 时须按 GPL v3 提供对应源码并履行其他许可义务。程序不作担保。

Google 图标沿用 Apache 2.0，见 [第三方声明](THIRD_PARTY_NOTICES.md)。许可文本随 APK 放在 `assets/licenses/`。
