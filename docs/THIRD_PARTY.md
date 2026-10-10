# 运行时与构建依赖

| 依赖 | 版本 | 提供方式 | 用途 |
|---|---|---|---|
| Minecraft | 26.1.2 | 服务器安装 | 原生实体/世界/命令/网络 |
| NeoForge | 26.1.2.115 | 服务器安装 | 生命周期、事件、Mixin/MixinExtras、TOML |
| LuckPerms NeoForge | 5.5.52（加载验证版本） | 单独服务端 Mod | 权限与当前上下文 |
| LuckPerms API | 5.5 | compileOnly，运行时由 LP 提供 | 不打入成品 |
| Kotlin stdlib | 2.3.20 | Jar-in-Jar | 唯一嵌入一份，无 reflect/coroutines |
| SQLite JDBC | 3.51.3.0 | Jar-in-Jar | JDBC 和平台原生库 |
| NightConfig TOML | 3.8.3 | NeoForge 提供 | 配置解析 |
| Gradle | 9.2.1 | Wrapper | 构建 |
| ModDevGradle | 2.0.148 | 构建插件 | NeoForge 修补源码/开发工具链 |
| JUnit Jupiter | 5.12.2 | 仅测试 | 存储与配置回归 |

第三方库的原始 JAR 内许可证保留，未重新打散/重定位其文件。
Kotlin：[项目与 Apache 2.0 许可证](https://github.com/JetBrains/kotlin)。
SQLite JDBC：[项目与许可说明](https://github.com/xerial/sqlite-jdbc)。
LuckPerms：[项目与 MIT 许可证](https://github.com/LuckPerms/LuckPerms)。
本工程原创代码按用户最终选择使用 GPL-3.0-or-later，版权 Theresa-owo，第三方依赖不被重新授权。
Adventure MiniMessage/API/Key 4.26.1 与 Examination API/String 1.3.0 通过 Jar-in-Jar 提供，许可证随原始库保留。
