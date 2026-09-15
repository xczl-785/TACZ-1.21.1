# TaCZ 内部战术接入模块

原 `tactical_tacz_adapter` 已合入单一 `tacz`。此目录保留专属接入、三枪内容、制作工具、测试及历史证据，不再是独立 Mod。旧资源、组件和协议命名空间保留；真实装配归 foundation，库存归 tactical，人物归 character，伤害防护归 combat。

- 当前结果、验收与回退：[第二项交付](../../docs/assembly-experiment/adapter合并交付.md)。
- 制作入口：[枪械导入流程](docs/枪械导入流程.md)。现有 ADAR、Radian、M4A1 均保留。
- 主题合同与历史证据：[文档索引](docs/README.md)。
- 公共编译依赖：[锁](public-dependency-lock.json)。正式代码依赖 foundation、tactical、character、combat；tarkov_content 只供开发工具使用，不嵌入 TaCZ。

实验室根目录执行 `bash gradlew build`（Java 21）。正式 Jar 排除开发命令；`developmentJar` 是含验收工具的完整单一 TaCZ 包，不能与正式 Jar 同时安装。NewMod 的 `source/integration-tests/tacz-runtime` 宿主通过锁选择其中一个，日常启动脚本仍沿用原世界目录。

三枪制作输入统一在 `weapon-sources`，配置在 `weapon-authoring`，生产输出在 `weapon-content`，旧报告在 `weapon-reports`。迁移未修改枪模、动画、数值或配件规则。当前没有新增退弹键；精确弹种返还和固定口径临时接口沿用各自原适用范围。
