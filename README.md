# Windows Monitor

Windows Monitor（窗口监控）是一个 Windows 后台服务程序，监控当前活动窗口并通过 WebDAV 上传截图。

## 运行方式

### 直接运行

双击 `start.vbs` 或运行 `console.bat` 以前台模式启动。

### 注册为 Windows 服务（推荐）

本项目使用 [WinSW](https://github.com/winsw/winsw) 作为 Windows 服务包装器。

1. 下载 WinSW 可执行文件，重命名为 `windowMonitor.exe`，放置于 `scripts/win/` 目录下。
2. 以管理员身份运行 `install-service.bat` 安装服务。
3. 服务配置文件为 `windowMonitor.xml`，已启用 **允许服务与桌面交互**（`<interactive>true</interactive>`）。
4. 若需卸载服务，以管理员身份运行 `uninstall-service.bat`。
