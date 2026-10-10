# LynxTags 可选名牌协议

独立客户端项目：[Theresa-owo/LynxTags](https://github.com/Theresa-owo/LynxTags)。
LynxTools 只提供可选同步，不包含客户端渲染或摄像机代码。

`nametags.enabled` 默认 false。启用后读取 LuckPerms 当前上下文前后缀，使用白名单 MiniMessage 标签解析。
1.0.2 的 `name_format` 默认 `<gradient:#42D9E8:#B58AFF><label></gradient>`。
`<label>` 将 LP 前缀、玩家名、LP 后缀用单个空格连接，空前后缀不留多余空格；整行共用一次渐变。
前后缀先经白名单解析再取纯文本，不把动态内容当标签执行。此模式统一整行样式，不保留前后缀单独指定的颜色。
旧 `<name>` 模板仍支持，保留前后缀独立样式和原有间隔。旧配置不覆盖；要采用新效果需改为上述 `<label>` 模板并 reload。
前后缀支持颜色、渐变、装饰、reset；不执行 click/hover/selector。无效元数据一次警告后回退用户名。
重载、登录、LP 数据重算及定期上下文检查刷新；每 tick 重算数量有上限，只向确认支持协议的客户端发送变化。

不创建/接管 scoreboard team，不改友伤、碰撞、可见性、TAB 或聊天显示名。
没有客户端扩展时保持原版名牌，不影响管理命令。

客户端使用原生 RenderNameTagEvent、Component 和 submitNameTag；Inventory 适配原生人物预览。
第一人称自身隐藏、F5 自身可见；其它玩家和命名生物按原版隐藏规则。没有盔甲架、Text Display、实体替身、HUD 或自定义字体 shader。
渐变是原生逐字 RGB，用户已经确认长串单次渐变的实机效果。

## LuckPerms 上下文渐变（1.0.3）

每次计算名牌时，在服务端线程使用当前玩家 QueryOptions 读取两个有效 meta：
`name-gradient-start`、`name-gradient-end`。两者均为完整 `#RRGGBB`（十六进制大小写均可）时，
优先使用 `<gradient:START:END><label></gradient>`。任一缺失或无效时回退 `nametags.name_format`，
不接受颜色名、额外空白或格式标签作为端点，不缓存跨上下文的 meta。
继承和组优先级完全由 LuckPerms 决定；插件不按组名硬编码颜色，也不修改 LP 数据。

服主计划配置如下（由 LP 管理，本 Mod 不自动创建）：

| 组 | name-gradient-start | name-gradient-end |
|---|---|---|
| default | `#A3ADB8` | `#F5F7FA` |
| trusted | `#66D6B5` | `#C7F3D7` |
| friend | `#68B8F5` | `#C4E7FF` |
| staff | `#AA8AE8` | `#E1CEFF` |
| admin | `#FFB45E` | `#FFE7A3` |
| theresa | `#FFB7D5` | `#B8DFFF` |

`<label>` 组合前后缀时，保留 MiniMessage 的纯文本处理，并剥离历史 `&#RRGGBB`、`&r`、
传统 `&`/`§` 颜色装饰代码和展开的 `&x&1&2&3&4&5&6` 格式；称号文字、括号、❤、内部空格保留。
玩家名仍作为未解析的纯文本插入，不做控制码转换。旧 `<name>` 分段模板行为不变。
长度限制、协议 v1、隐身可见性与刷新规则均保持不变，客户端继续使用 LynxTags 1.0.1。

替换服务端 JAR 需要重启。已有 Config 不覆盖；无需新增配置键，LP 元数据变化会按已有机制刷新。

## Wire v1

通道 `lynxtags:name_v1`，optional play clientbound，版本字符串 `1`。

1. 原版 16-byte UUID。
2. VarInt 段数 0–256；0 移除覆盖样式。
3. 每段为 UTF-8 字符串（VarInt byte-length 前缀）、32-bit RGB、unsigned byte flags。
4. flags bit0 bold、bit1 italic、bit2 underline、bit3 strikethrough，其他位拒绝。

总文本最多 256 Unicode 码点，RGB 范围 0x000000–0xFFFFFF，不允许控制字符。
两个仓库编解码器结构一致，不能单边修改已发布的 v1 数据格式。
当前支持 MC 26.1.2 / NeoForge 26.1.2.115；其它平台需要另行适配，不作为本版兼容承诺。
