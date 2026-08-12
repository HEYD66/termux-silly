# SillyTavern Termux Android

这是一个面向 Android 的 SillyTavern 启动与运行客户端：在 Termux 运行环境上完成依赖安装、源码拉取、启动和日志查看，并提供适配手机的内置浏览器。

## 这个项目解决什么问题

- 一键安装 SillyTavern 运行环境，并在真实终端中显示命令和实时进度。
- 自动连接 Termux 服务，支持创建、停止和重新连接会话。
- 在应用内打开 SillyTavern Web 界面，适配移动端比例和软键盘输入。
- 支持 Android 文件访问，用于导入角色卡等本地文件。
- 与设备上其他 Termux 安装保持独立数据目录，可以共存。

## 两个母项目

本项目是在以下两个开源项目基础上的 Android 集成与功能修改：

1. [Termux App](https://github.com/termux/termux-app)：提供 Android 终端、Shell 会话和基础运行环境。
2. [SillyTavern](https://github.com/SillyTavern/SillyTavern)：提供实际的 Web 应用和角色扮演界面。

本项目不是 SillyTavern 官方客户端，也不代表两个母项目的官方立场。SillyTavern 的源码默认在运行时从配置的 GitHub/Gitee 源获取，本仓库主要维护 Android 集成层和启动流程。

## 构建

Windows：

```powershell
.\gradlew.bat :app:assembleRelease
```

产物位于：

```text
app/build/outputs/apk/release/
```

## 开源协议与版权

- 本项目的 Termux 应用部分遵循 [GPLv3-only](LICENSE.md)。
- `terminal-emulator` 和 `terminal-view` 中的相关代码包含 [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) 许可部分。
- `termux-shared` 的例外和版权说明见 [`termux-shared/LICENSE.md`](termux-shared/LICENSE.md)。
- SillyTavern 本身遵循其仓库中的 [AGPL-3.0](https://github.com/SillyTavern/SillyTavern/blob/release/LICENSE)；本项目不替换、不重新声明 SillyTavern 的许可证。分发或修改 SillyTavern 文件时，应保留其 LICENSE、版权声明和对应源码信息。
- 本项目新增和修改的 Android 集成代码随本项目 GPLv3 条款发布，并保留母项目原有版权与许可证文件。

## 发布说明

APK、源码快照和 SHA256 校验文件见 [GitHub Releases](https://github.com/HEYD66/termux-silly/releases)。
