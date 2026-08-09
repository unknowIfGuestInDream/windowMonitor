# windowMonitor 窗口监控

windowMonitor 是一款 Windows 窗口监控工具，当检测到特定窗口（微信、QQ 等）处于前台时，自动截图并上传到 WebDAV 服务器。

---

## 使用方式

### 直接运行（推荐）

```bat
# 前台运行（带控制台输出）
console.bat

# 后台运行（无控制台窗口）
start.bat
```

### 注册为 Windows 服务

**注意：** 注册为服务时必须正确配置服务登录账户，否则服务运行在 Session 0（非交互式隔离会话），将导致：

- `GetForegroundWindow()` 返回 null — 无法检测前台窗口
- `java.awt.Robot` 无法访问用户桌面 — 截图失败

#### 步骤 1：构建并安装 MSI

```powershell
# 构建 fat-jar
mvn package -DskipTests

# 构建 Windows 服务 MSI
.\scripts\build-service.ps1 -AppJar "windowMonitor-1.0.0.jar" -AppVersion "1.0.0"

# 安装 MSI（需要管理员权限）
msiexec /i dist\windowMonitor-windows-service-1.0.0.msi /quiet
```

#### 步骤 2：配置服务登录账户（关键步骤）

服务安装完成后，必须将其配置为使用与当前登录用户相同的 Windows 账户运行，否则无法截图。

```powershell
# 以管理员身份运行 PowerShell，然后执行：
.\scripts\configure-service.ps1 -Username ".\YourWindowsUsername"
# 脚本会提示输入密码
```

或者在图形界面操作：

1. 打开 **服务**（`services.msc`）
2. 找到 **windowMonitor** 服务，双击打开属性
3. 切换到 **登录** 选项卡
4. 选择 **此账户**，填写当前 Windows 用户名和密码
5. 点击确定，然后重启服务

#### 步骤 3：启动服务

```powershell
Restart-Service -Name windowMonitor
```

---

## 日志

服务运行时会在 jar 同目录下生成 `windowmonitor.log` 文件，记录启动信息、前台窗口标题、截图上传情况及错误信息，可用于排查问题。

---

## 常见问题

### 注册为服务后无法截图

原因：服务默认以 SYSTEM 账户运行，位于 Session 0 隔离会话，无法访问用户桌面。

解决：按照上述"步骤 2"将服务配置为以真实用户账户登录运行。
