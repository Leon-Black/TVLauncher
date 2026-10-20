# TV Launcher

实在是受够了电视内置桌面的臃肿，前几天在社交媒体上看到有人借助 AI 精简了自家电视的系统，受到启发，我也做了一个清爽的 TV 桌面。
这是个完全本地的轻量桌面，核心功能只有显示应用列表、更换首页壁纸。
我设计 UI，Codex 写代码。UI 上参考了印象中的 Apple TV。

## 截图
首页

<img width="1920" height="1080" alt="TV Launcher-Home" src="https://github.com/user-attachments/assets/aae4d66d-94be-4de5-ab96-5a5d686d22ab" />

应用列表

<img width="1920" height="1080" alt="TV Launcher-App List" src="https://github.com/user-attachments/assets/6e8d9219-495a-4c5f-ad9d-4675b6152075" />

## 兼容范围

- 最低 Android 10（API 29）。
- 验证设备是 Sony BRAVIA 4K UR2（Android 10）；没验证过其他品牌和系统版本。

## 安装

从附件下载 APK，在电视上使用文件管理器安装；系统如有提示，允许安装即可。也可使用 [ADB](docs/DEVELOPMENT.md#安装和设备测试)。

安装后，先从电视现有桌面打开“TV Launcher”，验证遥控导航和应用启动。

用包名为 `me.leon.tvlauncher`。

## 壁纸位置和权限

把图片放入电视共享存储的 **`Pictures/Wallpaper/`** 目录，支持读取子文件夹。常见完整路径为：

```text
/sdcard/Pictures/Wallpaper/
```

目录名按 `Wallpaper` 拼写。其他目录的图片不会出现在壁纸抽屉里，当前不提供目录选择或壁纸轮播。

第一次打开壁纸抽屉时会申请图片读取权限：

| 系统版本 | 读取方式 |
|---|---|
| Android 10 | 取得存储读取权限后直接扫描目录，包括尚未被媒体库索引的图片 |
| Android 11–12 | 取得存储读取权限后从系统媒体库筛选固定目录 |
| Android 13 | 取得图片读取权限后从系统媒体库筛选固定目录 |
| Android 14 及以上 | 支持全部或部分图片授权；部分授权时只显示固定目录内获准的图片 |

拒绝授权时无法浏览目录图片。可在电视的应用权限设置中重新授权；具体入口取决于系统。Android 11 及以上如果图片已放入正确目录但仍不显示，请检查授权和系统媒体库是否已索引该图片。

确认选择后，图片会复制到应用私有目录。原共享图片删除后，当前壁纸仍可保留；卸载或清除数据会删除私有副本。没有保存的壁纸，或保存文件无法读取时，背景使用默认灰色。图片按比例裁切铺满屏幕。


## 数据与网络

应用读取本机应用入口、名称和图标，并在用户授权后读取指定目录的图片。用户选定的壁纸保存在本机应用私有目录。当前清单没有声明网络权限，没有接入账号、广告或分析服务；从桌面启动的其他应用遵循各自的数据规则。

## 开发与许可证

构建环境、代码结构、签名配置和测试方式见 [开发说明](docs/DEVELOPMENT.md)。

Copyright (C) 2026 Leon。本项目原创代码采用 **GPL-3.0-only**，完整条款见 [LICENSE](LICENSE)。允许商业使用和分发，分发 APK 时须按 GPL v3 提供对应源码并履行其他许可义务。程序不作担保。

Google 图标沿用 Apache 2.0，见 [第三方声明](THIRD_PARTY_NOTICES.md)。许可文本随 APK 放在 `assets/licenses/`。
