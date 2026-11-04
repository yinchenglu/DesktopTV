[![APK release](https://github.com/yinchenglu/LauncherTV/actions/workflows/release.yml/badge.svg)](https://github.com/yinchenglu/LauncherTV/actions/workflows/release.yml)

# LauncherTV 增强版（Simple TV Launcher 中文定制版）

基于开源项目 [alescdb/LauncherTV](https://github.com/alescdb/LauncherTV)（Simple TV Launcher 2.0.2）
的中文定制版本，面向 Android 电视盒子（兼容 Android 4.0+），遥控器友好。

## 本版本新增/修改的功能

1. **桌面直接显示所有应用** —— 去掉了原版左上角的"应用网格"按钮，
   所有已安装应用按名称排序直接铺在桌面上，应用太多时桌面可上下滚动。
2. **设置界面全面汉化** —— 简体中文环境下所有设置项均为中文。
3. **应用操作菜单** —— 选中应用后按遥控器**菜单键**（或长按）弹出菜单：
   打开、上移、下移（排序）、卸载、应用信息、从桌面移除。
   手动排序后顺序会记住；新装的应用会自动追加到桌面末尾。
4. **24/12 小时制切换** —— 设置 → 时间制式：跟随系统 / 24 小时制 / 12 小时制。

## 下载安装

到 [Releases](https://github.com/yinchenglu/LauncherTV/releases) 下载
`LauncherTV-x.x.x.apk`，用 U 盘或局域网传到电视盒子安装即可。

> 注意：与 Google Play 原版签名不同。如果盒子上装过原版，需先卸载原版再安装。

## 构建

CI 自动打包：push 到 `master` 或推 `v*` 标签时，GitHub Actions 自动构建并签名
（可选在仓库 Secrets 配置 `ANDROID_KEYSTORE_BASE64` / `ANDROID_KEYSTORE_PASSWORD` /
`ANDROID_KEY_ALIAS` 使用固定签名；不配则使用 CI 临时签名）。

本地构建：`./gradlew assembleRelease`（需 JDK 17+ 与 Android SDK）。

## 上游功能

- 可自定义桌面网格：列数、边距、透明度、图标锁定
- 快捷方式与文件夹支持
- PIN 保护与隐藏应用
- 系统壁纸或自定义桌面图片
- 时钟、日期、电池、网络状态显示

[上游构建文档](docs/building.md)
