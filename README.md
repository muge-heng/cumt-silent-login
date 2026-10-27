<div align="center">

<img src="assets/icon.png" width="128" height="128" alt="校园网无感登录">

# 校园网无感登录

连上校园 Wi‑Fi，自动完成登录，全程不打扰——只在后台发一次请求，完成后弹一则安静的通知告诉你结果。

[![版本](https://img.shields.io/github/v/release/muge-heng/cumt-silent-login?color=E6B325&label=%E7%89%88%E6%9C%AC)](https://github.com/muge-heng/cumt-silent-login/releases)
[![下载](https://img.shields.io/github/downloads/muge-heng/cumt-silent-login/total?color=7A5230&label=%E4%B8%8B%E8%BD%BD)](https://github.com/muge-heng/cumt-silent-login/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#下载安装)
[![语言](https://img.shields.io/badge/Java-100%25-orange?logo=openjdk&logoColor=white)](#从源码构建)
[![零依赖](https://img.shields.io/badge/%E7%AC%AC%E4%B8%89%E6%96%B9%E4%BE%9D%E8%B5%96-0-brightgreen)](#从源码构建)
[![许可](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

</div>

---

## 界面预览

<div align="center">
  <img src="assets/preview.png" width="300" alt="首次设置页预览">
</div>

> 上图是**首次打开**时的设置页。填过一次并成功登录后，这个页面就再也不会出现——之后每次打开都是无感的。

## 特性

- **无感登录**：配置好之后打开应用，不显示任何前台页面，后台直接完成登录，随即退出。
- **只弹一则通知**：登录结果用一条安静、明确的中文通知告知（成功 / 已在线 / 失败原因），不响铃、不打断。
- **一次配置，长期有效**：账号、密码、运营商填一次即记住；想改只需清除应用数据。
- **支持四大接入**：校园网 / 中国移动 / 中国联通 / 中国电信，登录地址一致，仅账号后缀不同。
- **零第三方依赖**：纯 Android 框架实现，包体极小（约 25 KB），无广告、无统计、无联网后台。
- **本地存储**：账号密码只保存在本机应用数据里，除校园网门户外不向任何服务器发送信息。

## 下载安装

1. 到 [Releases](https://github.com/muge-heng/cumt-silent-login/releases) 下载 `cumt-silent-login-v1.0.0.apk`。
2. 在手机上打开安装包，允许“来自此来源的应用”安装。
3. 安装完成即可，见下方使用方法。

> 需要 Android 8.0（API 26）及以上。

## 使用方法

**第一次打开**
1. 填写「账号 / 学号」和「校园网密码」。
2. 选择「运营商」：校园网 / 中国移动 / 中国联通 / 中国电信。
3. 点「立即登录」。首次会申请一次通知权限，建议允许，以便无感登录时能看到结果。
4. 登录成功后，账号即被记住。

**之后的每一次**
- 直接点开应用图标即可：应用不显示任何页面，在后台自动完成登录，完成后弹一则通知告诉你结果。
- 配合下面的「妙用」，可以做到连上 Wi‑Fi 就自动登录，连“点开”都省了。

## 填错了怎么改

应用没有提供修改入口（保持极简）。如果账号、密码或运营商填错了：

- **Android**：设置 → 应用 → 校园网无感登录 → 存储 → **清除数据**（或“清除全部数据”）。
- 重新打开应用，即回到首次设置页，重新填写即可。

> 只是网络没连上导致的登录失败，不需要清除数据——连上校园 Wi‑Fi 后重新打开应用重试即可。

## 妙用：连上 Wi‑Fi 自动登录

支持“快捷指令 / 自动化”的手机系统，可以设置成**一连上校园 Wi‑Fi 就自动打开本应用**，实现真正零操作的无感登录。基本思路都是新建一条“自动指令”：触发条件选“连接到指定 Wi‑Fi”，执行动作选“打开应用 → 校园网无感登录”。

- **OPPO（小布指令）**：小布助手 → 指令 / 快捷指令 → 新建“自动指令” → 触发条件选「连接到指定 Wi‑Fi」（填校园网 SSID，如 `CUMT_Stu`）→ 执行动作选「控制应用 → 打开 → 校园网无感登录」。
- **vivo（蓝心小 v）**：蓝心小 v → 快捷指令 → 创建自动化 → 条件设为「连接到指定 Wi‑Fi」→ 操作设为「打开应用 → 校园网无感登录」。
- 其它带“Bixby 例程 / 小艺建议 / Tasker”等自动化能力的机型同理，条件用“连接 Wi‑Fi”，动作用“打开应用”。

## 工作原理

应用向学校 ePortal 认证接口发起一次登录请求（账号 = 学号 + 运营商后缀，如 `@cmcc`），解析门户返回的 JSONP 结果来判定“登录成功 / 已在线 / 失败”，并在结果含义不明确时以一次真实外网连通探测作为兜底，最后把结论用一条通知呈现。整个过程在后台线程完成，处理完即结束。

- 门户地址为校园网内网私有地址，只有连上校园 Wi‑Fi 时才可达；未连上时会明确提示“未连到校园网”。
- 认证只依赖账号与密码，`wlan_user_ip / mac` 等仅为接入侧信息位。

## 从源码构建

环境：JDK 17、Android SDK（compileSdk 34 / build-tools 34.0.0）、Gradle 8.7（AGP 8.4.0）。

```bash
# 指定你的 Android SDK 路径
echo "sdk.dir=/你的路径/android-sdk" > local.properties

# 构建 debug APK
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开本目录，点 Run 安装到手机。

## 许可与免责

- 本项目以 [MIT](LICENSE) 许可开源。
- 请仅用于你**有权接入**的校园网络。校园网门户策略可能随学校调整而变化，届时登录判定可能需要相应更新。
