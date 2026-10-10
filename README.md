# LynxTools

Minecraft **26.1.2 / NeoForge 26.1.2.115** 的 Kotlin 服务端基础管理 Mod。
不依赖 Bukkit、Spigot、Paper、Vault 或 Essentials，不包含经济、领地或多 home。

## 安装

1. 使用 **JDK 25** 启动上述版本的独立 NeoForge 服务端。
2. 将 `LynxTools-1.0.4.jar` 和兼容的 **LuckPerms NeoForge** 放进服务器 `mods/`（最低 5.5.52）。
3. 正常启动。首次启动会生成默认配置、语言文件和 SQLite 数据库。
4. 根据需要通过 LuckPerms 授予权限；OP 身份本身不会绕过 LynxTools 的权限检查。

LuckPerms 是必需服务端依赖，编译使用 API 5.5；本项目实际加载验证的是 NeoForge 5.5.52 发行包。
当前生产环境使用 5.5.85；1.0.1 本次只执行构建与单元测试，没有连接生产服或重新启动游戏验证该发行包。
勿使用 Bukkit/Fabric/Forge 版本替代。更新 LuckPerms 时先确认其发行包支持 MC 26.1.2。

Kotlin 标准库 **2.3.20** 和 SQLite JDBC **3.51.3.0** 通过 NeoForge Jar-in-Jar 嵌入成品 JAR，各一份。
可选名牌格式另外内嵌 Adventure MiniMessage/API/Key **4.26.1** 与 Examination **1.3.0**，不要求客户端安装这些库。
不需要额外安装 KotlinForForge；没有把整个 Kotlin 工具链、LuckPerms、SLF4J 或 NeoForge 重复打包。
TOML 解析使用 NeoForge 自带 NightConfig **3.8.3**。Mixin/MixinExtras 使用 NeoForge 自带实现。

**客户端不需要 LynxTools、Kotlin 前置或上述两个内嵌库。** 没有自定义必需网络 payload、方块、物品或客户端渲染代码。
服务端 Mod 仅在 dedicated server 加载；暂不将单人整合服务端作为支持场景。
混合服务端以及其它 NeoForge 版本没有作兼容承诺；不要据此假设与它们的死亡/能力补丁兼容。

## 命令、权限与 Tab

所有节点使用当前玩家、当前 LuckPerms 上下文查询；LP 明确 `false` 优先于默认策略。
下表“默认”仅表示 LuckPerms 返回 `UNDEFINED` 时的可配置策略；用户未加载或 LP 不可用时不会放行。

| 命令/用途 | 权限节点 | 默认 | Tab 补全 |
|---|---|---|---|
| `/warp <名称>` | `lynxtools.command.warp` | 开放 | 允许访问的已保存 warp |
| `/setwarp <名称>` | `lynxtools.command.setwarp` | 拒绝 | 允许访问的已保存名称 |
| `/setwarp <名称> <x> <y> <z> [yaw] [pitch]` | 同上 | 拒绝 | 名称后逐项补全服务端实体当前坐标与朝向 |
| `/home` | `lynxtools.command.home` | 拒绝 | 命令名，无参数 |
| `/sethome` | `lynxtools.command.sethome` | 拒绝 | 命令名，无参数 |
| `/back` | `lynxtools.command.back` | 拒绝 | 命令名，无参数 |
| `/gm <0/1/2/3/s/c/a/p>` | `lynxtools.command.gm` | 拒绝 | 八种模式别名 |
| `/fly` | `lynxtools.command.fly` | 拒绝 | 命令名 |
| `/vanish` | `lynxtools.command.vanish` | 拒绝 | 命令名，无参数；仅创造模式可开启，同一权限可看见隐身玩家 |
| `/fly <在线玩家名>` | `lynxtools.command.fly.others` | 拒绝 | 有此权限才返回在线玩家名 |
| `/suicide` | `lynxtools.command.suicide` | 开放 | 命令名 |
| `/suicide KeepInventory` | 同时需要 `lynxtools.command.suicide` 和 `lynxtools.command.suicide.keepinventory` | 拒绝 | 仅有附加权限且当前维度 gamerule 为 false 时显示 `KeepInventory` |
| `/tpr <在线玩家>`、`/tprequest <在线玩家>` | `lynxtools.command.tpr` | 开放 | 可处理传送状态的在线玩家，不包含自己 |
| `/tpa [请求者或请求ID]`、`/tpaccept ...` | `lynxtools.command.tpa` | 开放 | 仅补全自己收到的待处理请求者 |
| `/tpd [请求者或请求ID] [ignore]`、`/tpdecline ...` | `lynxtools.command.tpd` | 开放 | 自己收到的请求者；第二参数补全 ignore |
| `/lynxtools reload` | `lynxtools.command.reload` | 拒绝 | `reload` |
| 受限 warp（可选配置） | `lynxtools.warp.<小写名称>` | 拒绝 | 无权访问的点位不会补全 |

无参数命令补全命令名后不提供多余参数。模块关闭或无权限时，正常客户端命令树不提供对应命令。
手动输入不允许的命令会收到明确错误。权限变化触发刷新；当前上下文变化还会周期重查，执行和参数补全则每次实时查询。
不缓存权限结果供后续命令复用。转发 `/execute as` 不会把命令方块/RCON 变成被信任的真实玩家来源。

除 reload 外，本 Mod 新增命令只支持真实玩家。真实服务端控制台默认可以 reload（可关闭）；
RCON、命令方块、其它实体和 FakePlayer 不拥有这些管理命令的执行权，也不以 OP 等级兜底。

示例（控制台执行 LuckPerms 自身命令）：

```text
lp user <玩家或UUID> permission set lynxtools.command.setwarp true
lp user <玩家或UUID> permission set lynxtools.command.gm true
lp user <玩家或UUID> permission set lynxtools.command.fly true
lp user <玩家或UUID> permission set lynxtools.command.fly.others true
lp user <玩家或UUID> permission set lynxtools.command.suicide true
lp user <玩家或UUID> permission set lynxtools.command.suicide.keepinventory true
lp user <玩家或UUID> permission set lynxtools.command.tpr true
lp user <玩家或UUID> permission set lynxtools.command.tpa true
lp user <玩家或UUID> permission set lynxtools.command.tpd true
```

## 行为

1.0.3 名牌支持当前 LuckPerms 上下文的 `name-gradient-start` / `name-gradient-end` 元数据，
两个有效 `#RRGGBB` 端点会覆盖配置模板，对完整“前缀 名字 后缀”统一渐变。
缺失或无效时回退 `nametags.name_format`；整条标签会清理历史颜色控制码，称号文本保持不变。
详细优先级、组配色示例与迁移说明见 [NAMETAGS.md](docs/NAMETAGS.md)。

### 加入与退出公告（1.0.4）

默认接管原版广播：加入 `&8&l[&a&l+&8&l]`，退出 `&8&l[&c&l-&8&l]`，
后接完整的 LuckPerms 前缀、名字和后缀；使用当前上下文组渐变端点，与头顶名牌共享格式计算。
这些是服务器加入/退出通知，与账户认证 Mod 的 `/login` 登录提示不同。
无需客户端 Mod，也不要求开启可选的 `nametags.enabled` 网络同步。

`modules.presence=false` 恢复原版消息；模块开启时 `presence.join=false` 或 `presence.quit=false`
分别静音对应公告。均可热更新。语言资源中的 `join_message`/`quit_message` 默认为 `{marker} {name}`；
`join_marker`/`quit_marker` 可调整颜色与加粗，支持传统 `&0–&f`、`&k–&o`、`&r`（也支持 §）。
仅标记解析旧颜色码；其余模板为纯文本占位符，名字是已格式化的原生 Component，不执行输入中的标签。
加入通知延后到玩家注册完成的下一 tick；退出在 LP 卸载前计算，隐身玩家退出仅对有可见权限的玩家广播。
停服期间不额外发送退出公告。模块仅替换原版调用点；第三方 Mod 自行发公告需要关闭其对应功能以避免重复。

### 命令反馈（1.0.2）

所有 LynxTools 命令都向执行者反馈结果；`/gm` 成功也会向本人显示当前模式。
单人命令不向其它玩家发送反馈；`/suicide` 产生的原生死亡聊天公告同样仅发给本人，
其它自然死亡继续使用原版规则。后台日志保留，不修改全服 gamerule。
传送请求等多人操作仅通知相关双方，不向无关玩家广播。

### 创造模式隐身（1.0.2）

`/vanish` 切换自己的隐身，`lynxtools.command.vanish` 默认拒绝。
只有创造模式可以开启；有同一权限的玩家仍能看到隐身玩家，无权限玩家不能看到其实体、
装备、手持物、名牌及依赖该实体渲染的效果，TAB 和原生玩家名补全也会隐藏。
每 tick 复核当前权限和模式；离开创造模式、失去权限、模块关闭时解除并提示本人。
状态不持久化，退出后清除，重新登录默认可见；跨维度保持，死亡/重生后清除。
`[modules] vanish` 控制模块，`[permissions] vanish` 控制 LP UNDEFINED 的策略，均可热重载。
消息可在语言文件中修改；旧配置不覆盖，缺失键使用内嵌默认值。

**保留**攻击、物品使用、丢弃/拾取、实体交互和方块操作。
因此攻击结果、掉落物以及其它世界交互仍可暴露存在，不承诺这些操作无痕。
原版箱子（含陷阱箱）、末影箱、木桶和潜影盒不会因为隐身玩家打开而改变开合计数、
触发开合声音或动画；正常玩家同时打开时保留正常行为。切换隐身前会关闭当前容器，避免计数错配。
不接管其它 Mod 自己实现的容器动画。

为了隐藏原生客户端的玩家名补全，必须移除该客户端的玩家资料。隐身玩家发言时，
无权限接收者收不到消息；本人及有权限接收者仍收到正常玩家聊天，签名计数不会被伪造。
第三方聊天 Mod 如果完全替换原生聊天分发、使用无来源系统消息，需要另做适配。
服务端名牌扩展也按同一可见性发送。客户端无需安装 LynxTools 或新版 LynxTags。

隐身依靠服务器原生实体追踪和按来源过滤的声音/粒子包，不使用药水、盔甲架或展示实体。
不会按坐标范围删除周围环境特效。其它 Mod 脱离玩家调用链发送的无来源声音/粒子、
自定义数据和第三方玩家查询不在可靠过滤范围内；原版 `/list`、管理命令/选择器也未接管。

### 1.0.1 默认权限与旧配置迁移

LP 返回 `UNDEFINED` 时，仅默认开放 `tpr`、`tpa`、`tpd`、`warp`、`suicide`。
其它玩家能力默认拒绝；普通自杀仍遵循当前世界 keep_inventory，附加 `KeepInventory` 默认拒绝。
LP 的 `TRUE` / `FALSE` 直接决定结果；插件不自行解释 `*` 或 `lynxtools.command.*`，不绕过 LP 返回的明确拒绝。

已有 `plugins/LynxTools/Config.toml` 不会被覆盖。升级 JAR 需要重启服务器；旧配置要采用新策略，
请将 `[permissions]` 中 `home`、`sethome`、`back` 改为 `false`，`suicide` 改为 `true`，
并核对其它项与上表一致。配置编辑后可由真实服务端控制台执行 `lynxtools reload` 热重载；
有 reload 权限的玩家也可执行。解析失败会保留上一份有效配置，缺失字段使用内嵌默认值。
`console_reload` 仍为独立的真实控制台开关，不代表给予玩家 reload 权限。

权限组由服主在 LuckPerms 配置，插件不创建组或修改权限数据库。建议继承与增量授权如下：

| 组 | 继承 | 显式增加的节点（值为 true） |
|---|---|---|
| default | 无 | 使用上述默认策略 |
| trusted | default | `lynxtools.command.home`、`lynxtools.command.sethome`、`lynxtools.command.back`、`lynxtools.command.suicide.keepinventory` |
| friend | trusted | `lynxtools.command.fly`（自己） |
| staff | friend | `lynxtools.command.fly.others`（他人） |
| admin | staff | `lynxtools.command.gm` |

未列出的管理能力不会随组名自动授予。普通组无需用显式 false 表示默认拒绝；
已有显式 false 如需移除，应由服主在 LP 中处理，插件不会替通配符授权改变 LP 优先级。
原版权限继续由现有 LP Auto-OP 管理，本版不接管 `/tp`、`/teleport`、`/op`，不添加权限桥接。

### 传送请求

`/tpr A` 请求把**发起者传送到 A 附近**，不是把 A 拉到发起者处。
A 收到原生彩色可点击消息：`[ ✓ 接受 ] · [ ✕ 拒绝 ] · [ — 忽略 ]`，有悬浮说明；无需任何客户端 Mod。
接受、拒绝分别通知双方；忽略只通知 A，静默删除本条请求，不永久屏蔽发起者。

可点击按钮实际执行 `/tpa <本条请求ID>`、`/tpd <本条请求ID>`、`/tpd <本条请求ID> ignore`。
请求 ID 绑定接收者，旧按钮不能操作后来的请求。手动 `/tpa` 或 `/tpd` 在只有一条请求时直接处理；多条时必须指定请求者或 ID。
例如 `/tpd Alex ignore` 静默忽略 Alex 的本条请求。忽略与拒绝共用 `lynxtools.command.tpd`。

默认超时 60 秒、发起冷却 5 秒、每人最多收到 5 条、全服最多 256 条，都可以在 `[requests]` 修改。
每个发起者只能有一个待处理请求，新请求替换旧请求并通知原接收者；同时取消发起者仍未完成的旧传送。
死亡、退出、重生或 reload 清理相关请求，停服不持久化。请求者的 tpr 权限在接受时再次检查。

接受提示是“准备传送”，不是“已经到达”。实际传送共用 warp/home/back 的安全检查、等待与冷却。
发起者移动/受伤会取消；接收者在准备期间移动、死亡、离线或切换维度也会取消，不追踪移动目标或强行传送。
仅在原生传送成功后发送到达消息。冷却或状态暂时不允许接受时保留请求，可在有效期内再试。

**原版 `/tp`、`/teleport` 本版保持原样，没有用 LynxTools 节点覆盖其权限。** 用户最终要求先完成附加请求功能，原版接管仅评估，见 [TP_EVALUATION.md](docs/TP_EVALUATION.md)。

### warp 与 home

- warp 默认长度 1–32，可配置上限 64。只允许英文字母、数字、下划线、连字符，保存/查询统一小写，不受系统语言环境影响。
- 重复名称默认拒绝；`warp.overwrite=true` 才允许覆盖，反馈区别“保存”和“覆盖更新”。
- 坐标版 setwarp 必须有名称；坐标为绝对数值，不使用 `~`/`^`。维度为执行玩家当前服务端维度。
- 坐标版未给出的 yaw/pitch 默认各为 0，可配置。当前位置版保留执行者真实实体朝向。
- 每名 UUID 仅一个 home，sethome 原子覆盖并明确提示更新。Freecam 摄像机不参与位置读取。

### 传送与死亡点

warp/home/back 共用处理器，默认无等待、无冷却。数据库读取与区块准备异步进行，因此“0 秒等待”仍可能需要准备时间。
源/目标维度白名单以外的禁止列表、不存在的维度、非有限数值、世界边界、建造高度和玩家状态都会检查。
玩家须存活、完成重生/客户端加载确认、不在睡眠或维度切换期间。载具默认拒绝；可配置下车后传送，但不会把载具或乘客带过去。

`safe` 模式先检查原始位置，再按到原始方块的格点距离寻找附近站立点。搜索半径、候选数、全服每 tick 检查数、
每次区块组数、每 tick 新加载组数、并发数和总超时均有上限。区块通过原版异步出生点 ticket API 准备，
不调用会在服务端线程 managedBlock 的 `getChunkFuture`，也不会在数据库线程访问世界。
使用原版短期搜索 ticket，自然到期，不删除其他调用者的同类 ticket。

站立检查采取保守策略：完整上表面支撑、1.8 格站立空间、世界边界、碰撞和配置的危险方块/流体检查。
部分台阶、特殊 Mod 方块或非标准体型可能被保守拒绝。不会破坏方块或生成平台。
`exact` 不移动原始目标，但仍做边界、支撑和空间检查；可通过 `reject_danger` 控制危险物拒绝。
默认 back 额外强制安全搜索；脚下虚空/岩浆时找附近可站立点，有限范围内没有则拒绝。
原始坐标在世界有效高度之外时直接拒绝，不声称可以从无限深的虚空找到任意远地面。

等待和准备期间：移动超过容差、实际受伤、死亡、断线、切换维度、权限撤销或模块关闭都会取消；转头不取消。
重复请求替换旧请求；重载取消全部待处理请求。成功后开始共享冷却，失败不占用冷却。
传送 API 返回成功并核对目标维度/位置后才发送成功消息。

死亡事件记录当时的精确维度、坐标与实体 yaw/pitch，不使用重生位置。记录是最近一次死亡，不是传送历史。
默认 back 无限重复使用；安全搜索结果不回写死亡记录。启用 `back.consume` 时仅在成功传送后按记录 revision 删除，
并发产生的新死亡记录不会被误删。消费写入失败会另外明确提示“传送完成、消费失败”。

### 游戏模式与飞行

`gm` 只修改自己：0/s 生存、1/c 创造、2/a 冒险、3/p 旁观。
调用原生 `ServerPlayer.setGameMode`，成功只向执行者发送可配置反馈，并记录后台（UUID、名字、前后模式、请求及结果）；不向其它玩家广播。
原生同步包正常发送，不修改全服 gamerule 或日志开关。其他 Mod 拦截/改变模式时返回失败提示。

自己与他人权限完全分离：`/fly <自己的名字>` 仍需 `lynxtools.command.fly`，不能用 `.fly.others` 绕过；只有自己权限不能操作他人，只有他人权限不能操作自己，Tab 按实际目标权限过滤。

`fly` 切换**额外许可**，不会强制开始飞行。使用唯一的临时 `CREATIVE_FLIGHT` 属性修饰符，
只删除自己的修饰符，不清除其它 Mod 的属性修饰符，也不覆盖创造/旁观模式的原生飞行。
同步原生 abilities 包的有效许可，客户端不需要 LynxTools。

fly 状态不持久化。死亡、重生、登出和正常停服清除；跨维度和模式切换后重新同步。
自己开启的许可跟随自己的当前权限；他人授予的许可跟随**授予者**的 `fly.others` 权限及在线状态，
授予者离线或失去权限时撤回（默认最多 20 tick），避免目标没有 self 权限时立即失效。
模块关闭/重载以及 LP 权限变化也会协调。其它 Mod 主动删除本模块修饰符时不反复抢写。

空中撤回默认给予最长 100 tick 的一次性坠落保护：落地、第一次坠落伤害、超时、死亡或离线即清除；
只作用于这一次撤回，不赋予永久免伤。可设 `immediate` 完全不保护。

### 自杀与单次 KeepInventory

`suicide` 直接调用原生 `player.kill(player.level())`，与原版 `/kill` 一样走 generic_kill 和死亡事件，不以命令文本绕过权限。
不填参数遵循当前维度原生 gamerule。`KeepInventory` 大小写不敏感，需附加权限；
当前维度 `keepInventory=true` 时不提供此补全，手动填写则提示直接使用 `/suicide`，不会再执行第二次自杀。

为本次自杀标记玩家后，在原生物品掉落、经验掉落计算和重生复制的 KEEP_INVENTORY 读取点做局部判断。
不修改任何世界 gamerule、不靠事后捡回掉落物恢复背包；遵循原生 KeepInventory 的物品、装备和经验保留语义。
标记随原版 NeoForge 玩家数据保存，支持死亡后退出/重启再重生；复制完成后清除。
若其他 Mod 取消死亡，立即撤销标记，不把效果留给下次意外死亡。
全服规则及其它玩家不受影响。第三方额外背包/墓碑/自定义掉落须另作集成测试，不承诺覆盖绕过原生流程的物品系统。

## 配置与数据

启动成功时默认输出 `Lynx` ASCII 字符画。`logging.startup_banner=false` 可关闭，重启生效。

```text
<服务器工作目录>/plugins/LynxTools/
  Config.toml
  Data.db
  Lang/zh_cn.toml
```

`plugins` 只是数据目录名称。详细默认值和范围写在 [Config.toml](src/main/resources/defaults/Config.toml) 中。
消息模板见 [zh_cn.toml](src/main/resources/defaults/zh_cn.toml)。复制该语言文件为 `<代号>.toml` 后设置 `language.code` 即可切换。

`/lynxtools reload` 在独立线程读配置与语言，整份解析校验成功后才切换。
所有模块、默认权限、消息、传送、飞行参数支持热更新。数据库路径/超时/队列/关闭等待和 warp 最大名长需要重启；
重载时保留这些项的旧值，并列出需要重启的键。不删除或改写用户配置。未知键、类型错误、越界或错误语言模板会拒绝重载，保留旧快照。

消息为**纯文本模板**，品牌前缀使用两个 RGB 端点逐字插值，正文使用成功/警告/错误/信息语义色。
不会把用户名字、点位名或动态参数解析为格式语法；不是 MiniMessage，不要填入 `<gradient:...>` 或 `&` 色码期望其生效。
普通玩家错误消息不含文件路径、SQL、异常或堆栈；后台保留操作和 UUID 上下文。

SQLite 使用参数化 SQL、事务、`PRAGMA user_version=1`、WAL、FULL synchronous 和有界单线程队列。
初始化/迁移可重复执行；未知未来 schema 拒绝打开。数据库损坏时拒绝启动，不自动删除、重建或覆盖。
保存成功必须以事务提交完成为准；锁定、只读、队列满等失败不会更新 warp 补全缓存或发送“已保存”。
死亡写入失败会记录后台并提示玩家，本次运行期间阻止 back 静默回到较旧记录，直到后续死亡成功保存。
存储不可用时无法承诺该次死亡在重启后恢复；重启只能读取最后成功提交的记录，必须检查并处理后台存储错误。
数据库以连接时实际 UUID 为主键，名字只是显示/查询辅助；数据库同时记录 `online-mode`，模式不一致时拒绝启动，需管理员制定身份迁移，绝不自行变换 UUID。

备份建议正常停服、确认数据库关闭无错误后，复制整个 `plugins/LynxTools`。
运行时复制单独 Data.db 不可靠，WAL 可能仍有提交数据；需要在线备份时使用 SQLite backup API 或一致性快照，保留 db/wal/shm 的一致状态。
关闭会排空已接受写入并关闭连接；超时/关闭失败明确记入后台。不要在异常后仅凭看到 Data.db 就删除 WAL。
升级前备份，数据库迁移失败保留原库。旧 LinxTools 的 MongoDB/序列化位置不自动导入；旧 UUID/维度名映射必须先核对，本项目不猜测身份。

## 构建与验证

构建工具链：JDK 25、Gradle 9.2.1、Kotlin 2.3.20、ModDevGradle 2.0.148。
本机 PowerShell 7：

```powershell
$env:JAVA_HOME = 'D:\Environment\Amazon Corretto\coretto25'
./gradlew.ps1 build --console=plain
```

产物在 `build/libs/LynxTools-1.0.0.jar`。正常 `build` 不生成/加载验证 Mod。
可选 `./gradlew.ps1 verificationJar` 构建独立原生方法探针，**不要把 verification JAR 部署到正式服**；它会在隔离服启动时创建测试玩家并临时切换 gamerule，结束后恢复。

开发环境 `runServer` 的资产工具还需要 Gradle 能找到 JDK 21（游戏仍运行 JDK 25）。本机可额外传：
`'-Porg.gradle.java.installations.paths=D:/Environment/Amazon Corretto/coretto21,D:/Environment/Amazon Corretto/coretto25'`。
实际启动验收使用官方安装器搭建的生产式 NeoForge 隔离服，直接加载成品 JAR，未依赖开发 classpath 掩盖运行时缺包。

验证结果与剩余项目见 [VALIDATION.md](docs/VALIDATION.md)，可选名牌方案见 [NAMETAGS.md](docs/NAMETAGS.md)。

## 可选 LynxTags 与许可

独立客户端 [LynxTags](https://github.com/Theresa-owo/LynxTags) 已实现。启用服务端 `nametags.enabled=true` 并 reload 后，支持协议的客户端可获得当前 LP 前缀/渐变用户名/后缀。
没有该客户端的玩家保持原版显示且仍可使用管理命令；不修改队伍，不放置盔甲架/显示实体。

Copyright (C) 2026 Theresa-owo。本项目采用 **GPL-3.0-or-later**，见 [LICENSE.md](LICENSE.md) 与 [NOTICE.md](NOTICE.md)。
允许合规商用，分发受 GPL 覆盖的衍生作品须遵守对应源码等义务；第三方依赖保留各自许可。

## 上游参考

- [NeoForge 26.1.2 官方 MDK](https://github.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle)
- [NeoForge 26.1 工具链说明](https://neoforged.net/news/26.1release/)
- [LuckPerms API](https://luckperms.net/wiki/Developer-API)
- [LuckPerms 5.5.52 NeoForge 发行包](https://modrinth.com/plugin/luckperms/version/v5.5.52-neoforge)

本项目还直接检查了 NeoForge 26.1.2.115 修补后的 Minecraft 源码，包含 ServerChunkCache、ServerPlayer、Player、GameRules、KillCommand 与 IPlayerExtension。
