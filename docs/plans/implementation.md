# 交付检查点

- 目标工程 E:/Projects/Minecraft/LynxTools；配套客户端 E:/Projects/Minecraft/LynxTags。旧 LinxTools 项目保持只读，没有改动。
- 最终范围仅 MC 26.1.2 / NeoForge 26.1.2.115。Fabric/Bukkit/全版本只评估，未实施。
- 最终许可 GPL-3.0-or-later，作者 Theresa-owo；先前非商业许可讨论已被用户选择替代。
- 用户明确授权：本地隔离服/客户端测试，完成后提交并推送 Theresa-owo/LynxTools 与 Theresa-owo/LynxTags。不得 force push 或改写已有历史。
- 基础管理、原生 suicide/单次 KeepInventory、全命令补全、Lynx 字符画、可选名牌协议已实现。
- 用户追加 tpr/tprequest、tpa/tpaccept、tpd/tpdecline 与原生点击卡片（接受/拒绝/忽略）；原版 tp 接管后续被用户明确暂缓，仅评估，不包含在本版。
- 15 项单元测试通过；最终生命周期117阶段、新JVM恢复8阶段通过；真实图形客户端、无扩展客户端连接及请求卡片原生点击通过，详见 docs/VALIDATION.md。
- 专用只读 reviewer 的三个 P2 均已修复并复核关闭；预期队列饱和错误有明确故障注入证据。
- 客户端测试临时 offline-mode 使用 ClientTest.db；原 online-mode/Data.db 配置已恢复。
- Git 空仓已初始化为 main，用户已授权提交/推送；交付源码与证据清单已检查。提交和远端身份以 git log/ls-remote 为准，交付两个 JAR。
- 运行时/验证文件在 .verification/ 和 build/，均不推送；源码与选定验证日志/截图可推送。没有自动发布 Release 的任务。
