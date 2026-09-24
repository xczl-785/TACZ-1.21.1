# 第二批打薄：旧枪匠配方与构建证据

2026-09-24，fork 从 `3259a441` 在 `codex/tacz-compat-slimming` 实施。仅本两项；交付日常测试入口后停下来等所有者实机验证，验证后才做内容盘点。本批不推进旧枪/共享资产裁剪、公共供弹/伤害迁移或 27 项统一。

## 范围与结果

- 旧工作台已退役。本批删除独占的 crafting、recipe POJO、smith UI 组件、两种 Gson serializer、ModRecipe/ModIngredientTypes；清理注册、RecipeManager/TagsUpdatedEvent 初始化、TimelessAPI 旧空配方 API、PackConvertor 旧配方转换、两个专属配置和 defaultTableLimit 命令/文案。没有替代 ID；原许可证与署名不变。
- 退役 `tacz:gun_smith_table_crafting` 与 `tacz:nbt`。导出的外部枪包在 DelegatingPackResources 的枚举与直接读取路径，按 recipe/recipes 下 JSON 的具体类型过滤（含嵌套旧 ingredient），不修改磁盘文件、不拦截其他数据目录或普通配方。旧版转换器不再输出旧配方。直接安装到世界的第三方数据包不经过枪包包装器，若引用旧类型需由包作者迁移；本批不读取/修改用户世界。
- 共享边界明确保留：CommonBlockIndex 仍要求 RecipeFilter，BlockData/TabConfig 与过滤器加载/网络同步维持原状。不能为了清旧配方顺带删通用 block index；这段共享契约的进一步整理 Deferred。靶子、靶车、展示架、火药的 4 个原版配方保留。
- 15 份 geometry-evidence、15 份 workbench-anchors、1 份 M4 authoring-contract 只从 processResources 排除。作者源及生成物源文件保留，verifyConfiguredWeapons 继续读取源目录。未查到运行 Java 消费这三类文件；preview/library/materials 与模型、动画、声音、Lua 保持原字节。

实际删除 16 个 Java 文件、857 物理行；加上调用/配置/文案清理和旧包过滤，生产源码/资源/构建合计新增 62 行、删除 992 行，净减 930 行（不含测试/文档/台账）。

离线编译、独立 core、4 项平台扩展测试、33 项 adapter 测试、1 项 NeoForge 注册与保留配方解码测试、15 枪/146 物品源验证、core/模块/适配边界检查通过。全量 check 未运行，未启动 Minecraft 或服务器。

逐文件路径、旧新哈希、旧 ID 和理由见 [源码审计](source-audit.json)。正式/开发两包的精确排除和保留校验见 [包审计](artifact-audit.json)，可运行 `python3 docs/newmod/recipe-slimming/verify_artifacts.py` 重验（依赖 build/libs 本批包和主仓保留的上一版包）。

## 可测试制品

版本 `1.1.8-hotfix-r6-slim2-newmod.963361c2`，构建源码 `2ea4a767b6f5282ebf8b69c93e6df2b21bf66174`，公共制品锁仍为 `963361c2`。正式包 60,195,188 bytes，开发包 60,427,982 bytes，均在 fork build/libs 并交主仓 vendor 作为日常版本；主仓锁记录来源与 SHA-512。上一版 compat-slimming 双包保留为回退，更早 scope15 从 vendor 移出但可从 Git 历史恢复。

两包各排除 31 个指定条目，共 363,738 压缩字节；证据源 SHA-256 均未变化。逐文件核验剩余 weapon-content、preview/library/materials、贴图、声音、Lua、保留 compat 类和平台服务无差异。上述排除量不是 FPS 提升证据。

构建通过（1m 24s；12 tasks，8 executed / 4 up-to-date）。命令、测试范围和未验项目见 [验证记录](validation.json)。本批未启动 Minecraft/服务器、未访问日常世界；带精简版与无 TaCZ 场景仍分别等待所有者验收。

## 实机交接与停止点

完成推送、打包、替换后，从 NewMod 原 `启动开发客户端.command` 启动，完全退出旧客户端后再测。检查资源重载无旧 recipe/ingredient 类型错误；15 枪图标、查看/组装预览、换件与 Lua 动作；靶子、靶车、展示架及其原版合成；保存重进。可在单独临时枪包目录测试旧配方过滤，不修改日常枪包或世界。注册单元测试、资源枚举测试和编译均不代替实际客户端验收。

无 TaCZ 是另一组：本批补 `:no_tacz_runtime` 非生产宿主，显式包含公共七模块（含 firearms 与 raid）、vanilla adapter 和开发示例。命令、隔离目录与缺口见主仓 `source/integration-tests/no-tacz-runtime/README.md`；未启动客户端或服务器。此前公共独立编译通过、本批类路径和启动配置准备通过，也不能写成完整玩法已通过。

B3-01/B5-05 待验收、B6-01 待复验；不改完成计数，不关闭旧 V4。所有者实机验证前，本任务停止进一步裁剪和全面内容盘点。
