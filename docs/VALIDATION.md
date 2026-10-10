# LynxTools 1.0.0 验证记录 — 2026-10-11

目标：Minecraft 26.1.2、NeoForge 26.1.2.115、LuckPerms NeoForge 5.5.52、JDK 25。

服务端 JAR SHA-256：`8FE4D82C8F65F66B6B99B90F54F6F416E21C6D18B775B3833681ED7A6E07E98C`。
客户端 LynxTags 1.0.0 SHA-256：`00353B9158E414B7DAEE5B90A4B431874272DFF9CB2340DA4A85419A7181C5C4`。

## 已完成

| 层次 | 结果 |
|---|---|
| 编译/单元测试 | build 成功；12 项存储配置测试 + 3 项名牌格式测试，全部通过 |
| 实际服务端 | 官方安装器生产式隔离服加载成品 JAR，SQLite schema 1、Kotlin/SQLite/Adventure 内嵌依赖正常 |
| 生命周期探针 | 最终主流程 117 阶段通过；新 JVM 恢复流程 8 阶段通过；正常退出码 0 |
| 权限/命令 | 实际 LP 默认/授予/拒绝/上下文不匹配、命令树权限、gm 静默及日志、控制台拒绝、reload |
| 跨维度 | warp/home/back、朝向保留、异步区块准备、目标维度不存在/禁止/高度越界、危险精确落点拒绝和附近安全搜索 |
| 死亡回溯 | 原生 kill → 死亡记录 → 原生重生 → 跨维度 back；死亡期间拒绝，重复使用，岩浆脚下安全搜索不覆盖原始位置，单次消费与再次使用拒绝 |
| 进程恢复 | 独立 JVM 重启后读取死亡点并执行跨维度 back，fly 不恢复 |
| fly | 四模式、模式切换、跨维度、其它修饰符共存、空中关闭一次性保护及到期、权限撤销、模块关闭、授权者退出、死亡/重生/登出清理 |
| 取消条件 | 移动、受伤、带活动飞行和待传送请求退出，重新注册后无旧请求/飞行 |
| KeepInventory | 原生物品/装备/经验/重生方法探针：不掉物品，保留护甲/副手和经验，标记清理，不修改全服规则 |
| 真实图形客户端 | LynxTags 实际连接隔离服并接收名牌；F5 前后、第一人称自身隐藏、命名生物、Inventory 标签，均有截图 |
| 无扩展客户端 | 不加载 LynxTags 的真实 NeoForge 客户端（Java-only 验证探针）连接成功，执行 /sethome 并收到提交后的成功消息 |
| 传送请求 | 三别名及 Tab、三原生按钮、接受/拒绝双方通知、静默忽略、旧按钮失效、多请求选择、超时、接收者移动取消、暂时状态恢复后重试 |
| 真实请求点击 | 无 LynxTags 客户端收到聊天卡片，调用原生 ClickEvent 处理器接受并收到到达消息；含实际画面截图，未模拟鼠标命中测试 |
| fly 权限隔离 | self-only 不能操作他人，others-only 不能通过 /fly 或指定自己名字绕过；Tab 按目标权限过滤 |

阶段数包含准备、配置和等待，不是独立用例数量。存储测试另覆盖锁定、损坏文件保留、参数化 SQL、UUID/online-mode、关闭排空、NaN、目录误作数据库以及重载输入校验。

## 审查发现及修复

用户要求的只读 reviewer 已完成审查，并复核关闭以下三项 P2：

1. MiniMessage strict 模式与 reset 冲突：改为非严格白名单解析；真实 reset/未闭合前缀测试通过。
2. 新死亡在队列满时立即失败，旧成功回调可能清掉新失败：加入 per-UUID generation；真实 Store 队列饱和注入确认旧成功不能解除阻止，新成功才恢复。
3. 后置 LOWEST 监听器可能取消死亡：改在原生 CommonHooks.onLivingDeath 最终返回后同步记录；注入后置取消确认原记录不变、有效 fly 未撤销。

另外在生命周期验证中修复：其它 Mod 删除本模块飞行修饰符后，仍必须清除跟踪状态、撤回正在飞行并同步 abilities；不得重新抢写修饰符。
追加请求审查发现的“接收者暂时不允许传送会误删请求”已修正并通过睡眠状态恢复后接受原请求的探针。接受后的发送者退出/死亡也会向仍在线的接收者发取消通知。

主流程日志中一条 RejectedExecutionException 是队列饱和注入的预期错误，有后续明确 PASS；不能据此误报整个测试失败。

## 证据与边界

- `docs/validation/lifecycle-main.log`：最终 117 阶段，包括后置取消、回调乱序、请求和权限隔离。
- `docs/validation/lifecycle-restart.log`：新 JVM 8 阶段恢复。
- `docs/validation/plain-client-server.log`：无扩展真实客户端连接。
- `docs/validation/request-client.log`、`request-card.png`：聊天卡片显示及原生点击事件处理链路。
- LynxTags 仓库的 `docs/validation/`：真实客户端日志与 PNG 截图。
- JUnit XML/HTML 在 `build/test-results/test/`、`build/reports/tests/test/`。

生命周期探针使用真实世界、ServerPlayer、原生命令/死亡/重生/传送和数据库；连接为内存通道，加载/传送确认由探针模拟。
PlayerList 注册及部分有效配置快照由测试代码注入，不冒充认证后的真实用户操作。
图形客户端测试则是真正的 Minecraft 客户端，通过 127.0.0.1 连接；为开发账号使用 offline-mode 且单独的 ClientTest.db，未转换原 online-mode Data.db 身份。测试后已恢复原服务端 online-mode 与数据库路径。
客户端是开发启动，不包含用户列出的额外渲染 Mod；所有验证 Mod 都与生产 JAR 分离。

## 具体剩余限制

- 未覆盖在线认证服务、代理、多玩家并发负载或跨平台/跨游戏版本。
- 未验证用户列出的 Freecam、Freelook、Sodium、Iris、Entity Culling 等组合；不宣称所有客户端组合已通过。
- 跨维度/back/fly 的服务端逻辑已验证，但真实玩家的飞行动作、网络延迟/丢包、载具/睡眠/门户交互及第三方 Mod 全组合仍需服内验收。
- Inventory 生存预览已截图；创造库存与不同 GUI 比例/资源包的全组合未穷尽。
- 额外背包、墓碑、第三方死亡系统、其它名牌/队伍系统和混合服务端未完整集成验收。
- GPU 上可见的文字为原生逐字 RGB；没有实现单字内部独立 shader 渐变。

没有将“build 成功”或“启动成功”替代上述独立层次的验收。

原版 /tp、/teleport 按用户最后指示保留原实现；本版没有接管其权限，评估见 TP_EVALUATION.md。
