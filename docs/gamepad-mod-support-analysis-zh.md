# ZalithLauncher2 项目对“游戏内额外手柄 Mod”支持情况分析

## 结论摘要

- **当前代码中明确、专门适配的“游戏内额外手柄 Mod”只有 `touchcontroller`。**
- 启动前会扫描 `mods` 目录，若发现 `mod.id == "touchcontroller"`，则设置 `version.enableTouchProxy = true`，随后在真正拉起 JVM 前启动代理通讯。 
- 该适配通过 `top.fifthlight.touchcontroller:proxy-client-android` 提供的 Unix Socket 代理实现，同时注入 `TOUCH_CONTROLLER_PROXY_SOCKET` 环境变量，并接入震动回调。
- 对其他常见手柄 Mod（例如 Controlify/Controllable/MidnightControls）**未看到等价的专用探测、握手、环境变量或协议桥接实现**，因此可判断为“未做显式专项支持”。

---

## 1) 已实现的额外手柄 Mod 支持：TouchController

### 1.1 触发条件：按 Mod ID 自动识别

`LaunchGame.checkEnableTouchProxy(version)` 会读取当前版本 `mods` 目录并遍历本地 Mod；当且仅当 `mod.id == "touchcontroller"` 时，置 `enableTouchProxy = true`。

这说明当前“额外手柄 Mod 支持”是 **ID 精确匹配触发**，而不是通用“任意手柄 Mod”探测。

### 1.2 启动时机：游戏启动参数准备后、JVM 启动前

`GameLauncher.launchGame(...)` 在组装完启动参数后调用 `tryStartTouchProxy()`；若版本标记了 `enableTouchProxy`，就会执行 `ControllerProxy.startProxy(...)`。

这意味着该适配属于启动器侧的“前置桥接层”，并不是 Mod 内部逻辑。

### 1.3 通讯机制：Unix Socket + 环境变量

`ControllerProxy.startProxy(...)` 内部做了三件关键事：

1. 使用 `UnixSocketTransport(InfoDistributor.LAUNCHER_NAME)` 建立本地 socket 传输；
2. `Os.setenv("TOUCH_CONTROLLER_PROXY_SOCKET", InfoDistributor.LAUNCHER_NAME, true)` 注入环境变量；
3. 构造 `LauncherProxyClient` 并 `run()`。

这与 TouchController 的代理协议对接方式一致，属于“协议级适配”。

### 1.4 能力面：文本状态 + 震动回调

当前声明 capability 为 `PlatformCapability.TEXT_STATUS`，并将 `VibrationHandler` 绑定到 `client.vibrationHandler`。震动策略支持 one-shot/click/double/heavy/tick（受 Android API 版本约束）。

因此，TouchController 的支持不只是“能识别”，还包含了输入法/文本状态与触觉反馈链路。

---

## 2) 启动器自身手柄能力（与“额外 Mod”并行存在）

项目本身已实现了一整套原生手柄输入链路（与外部 Mod 是两套机制）：

- `GameHandler` 对 `dispatchKeyEvent` 的手柄事件进行分发；
- `ui/control/gamepad` 下存在按键/摇杆重映射、死区处理、配置切换、菜单内与游戏内分流映射等实现；
- 设置与游戏内菜单均提供手柄开关、灵敏度、映射配置入口。

这意味着：

- **即使没有额外手柄 Mod，启动器也提供基础手柄控制能力。**
- TouchController 适配是“额外增强通道”，不是唯一手柄通路。

---

## 3) 对“额外手柄 Mod”支持范围的判断

基于代码证据可给出如下判断：

- **明确支持：** `touchcontroller`（显式 ID 检测 + 专用代理协议）。
- **未见显式支持：** 其他手柄 Mod（未发现对应 ID 白名单、专用代理、环境变量、桥接客户端）。

> 注：这并不等于“其他 Mod 一定无法使用”。
> 
> 许多 Mod 若仅消费 Minecraft/LWJGL 常规输入事件，可能在“无专项适配”情况下仍可工作；但项目目前没有为它们提供与 TouchController 同等级别的启动器侧协同能力。

---

## 4) 兼容性风险与边界

1. **ID 依赖风险**：TouchController 触发条件是固定字符串 `touchcontroller`，若分叉包改 ID，则不会自动启用代理。
2. **能力范围有限**：目前 capability 仅见 `TEXT_STATUS`，如果后续 Mod 侧协议新增能力，需同步扩展。
3. **并行输入冲突**：启动器原生手柄映射与 Mod 侧控制可能出现重复输入/行为叠加，需要用户在设置中按需关闭某一路输入。

---

## 5) 如果要扩展到“更多手柄 Mod”

可考虑按以下方向演进：

- 做“Mod 适配器注册表”（`modId -> Adapter`），避免硬编码单一 `touchcontroller`。
- 将代理启动、环境注入、能力协商抽象为统一接口。
- 在 UI 提供“检测到手柄 Mod 时的冲突提示/推荐配置”（如建议关闭原生映射）。
- 为常见 Mod 增加识别策略（ID 别名、元数据特征、协议探测）。

