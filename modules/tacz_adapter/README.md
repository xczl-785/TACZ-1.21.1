# TaCZ 内部战术接入模块

原 `tactical_tacz_adapter` 已合入单一 `tacz`。本目录承载枪械接入、十五把正式枪的作者源与生成资源、测试和开发工具；不是独立 Mod。真实装配实例归 foundation，库存事务归 tactical，人物规则归 character，伤害防护归 combat。

- 当前生产：[枪械来源](weapon-sources/README.md)、[生成资源](weapon-content/README.md)、[光学作者源](weapon-sources/optics/README.md)。
- 现行职责与历史资料：[文档索引](docs/README.md)。旧 ADAR/Radian/单独导入 M4A1 生产源和工具已退役，旧三枪文档只作来源证据。
- 编译依赖：[公共锁](public-dependency-lock.json)。正式代码依赖 foundation、tactical、character、combat；tarkov_content 只供开发工具使用，公共 Mod 不嵌入 TaCZ。
- 构建与装载：[运行依赖](docs/self-built-runtime.md)。正式 Jar 和含开发工具的完整 Jar 二选一；NewMod 宿主按锁加载。

实验室根目录使用 `bash gradlew assemble --offline`（Java 21）构建；相关验证命令见具体制作入口。全量 `check` 的既有迁移台账限制见[状态](../../status.md)，不把打包成功描述为全量门禁通过。
