# LynxTags 可选名牌协议

独立客户端项目：[Theresa-owo/LynxTags](https://github.com/Theresa-owo/LynxTags)。
LynxTools 只提供可选同步，不包含客户端渲染或摄像机代码。

`nametags.enabled` 默认 false。启用后读取 LuckPerms 当前上下文前后缀，使用白名单 MiniMessage 标签解析。
`name_format` 默认青蓝到紫色的用户名渐变，`<name>` 是安全纯文本占位符。
前后缀支持颜色、渐变、装饰、reset；不执行 click/hover/selector。无效元数据一次警告后回退用户名。
重载、登录、LP 数据重算及定期上下文检查刷新；每 tick 重算数量有上限，只向确认支持协议的客户端发送变化。

不创建/接管 scoreboard team，不改友伤、碰撞、可见性、TAB 或聊天显示名。
没有客户端扩展时保持原版名牌，不影响管理命令。

客户端使用原生 RenderNameTagEvent、Component 和 submitNameTag；Inventory 适配原生人物预览。
第一人称自身隐藏、F5 自身可见；其它玩家和命名生物按原版隐藏规则。没有盔甲架、Text Display、实体替身、HUD 或自定义字体 shader。
渐变是原生逐字 RGB，用户已经确认长串单次渐变的实机效果。

## Wire v1

通道 `lynxtags:name_v1`，optional play clientbound，版本字符串 `1`。

1. 原版 16-byte UUID。
2. VarInt 段数 0–256；0 移除覆盖样式。
3. 每段为 UTF-8 字符串（VarInt byte-length 前缀）、32-bit RGB、unsigned byte flags。
4. flags bit0 bold、bit1 italic、bit2 underline、bit3 strikethrough，其他位拒绝。

总文本最多 256 Unicode 码点，RGB 范围 0x000000–0xFFFFFF，不允许控制字符。
两个仓库编解码器结构一致，不能单边修改已发布的 v1 数据格式。
当前支持 MC 26.1.2 / NeoForge 26.1.2.115；其它平台需要另行适配，不作为本版兼容承诺。
