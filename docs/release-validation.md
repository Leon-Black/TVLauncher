# 0.1.0 发布验证

- Release APK 与 lintRelease 通过：0 错误、14 警告。
- 14 项本地滚动边界检查通过。
- APK v2 签名和 zipalign 检查通过。
- 包内 GPL、Apache 和第三方许可声明齐全，无 default_wallpaper 照片资源。
- 最低 Android 10；默认背景 #808080；保留用户自选壁纸。
- 用户已确认电视安装正式版及新版测试版，并反馈基础试用无问题。此项为用户手动验收反馈；默认 Home 接管、整机重启和完整设备自动回归未单独确认。
- Debug 与 Release 签名不同，无法覆盖更新；卸载会清除应用私有数据。

## 测试版与正式版并存配置

- Debug 包名 `me.leon.tvlauncher.debug`，名称“TV Launcher 测试版”；Release 保留 `me.leon.tvlauncher` 和“TV Launcher”。两版数据独立。
- 两版构建与 Lint 通过，Debug 设备测试 APK 编译通过。已从实际 APK 核实包名、名称、debuggable 标志、签名及对齐；正式版证书与此前发布密钥一致。
- 已检查设备测试工具指向新版 Debug 包。用户已安装两版并反馈试用无问题；本轮未运行设备自动测试。
- 早期同包名 Debug 安装仍需备份后迁移，不能直接由正式版覆盖。
