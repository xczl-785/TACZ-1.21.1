# 首批可选兼容退出

2026-09-24；基线 `d5ca6a1278083e342a4ab499b245dc7d8dd04048`，实施分支 `codex/tacz-compat-slimming`。

本批只退出 JEI 配件查询、Cloth 图形配置、Carry On 黑名单、Controllable 输入/震动、KubeJS 专项接口。不是历史 V4 完成，也不改变枪械统一 27 项计数：B3-01 / B5-05 待验收，B6-01 待复验。

来源与逐文件裁决见主仓[调查](../../../../docs/进行中/TaCZ轻量化调查与无依赖评测.md)和[候选清单](../../../../implementation-evidence/tacz-slimming-compat-candidates-20260924.json)。原路径直接退役，没有替代 Mod ID、物品 ID 或空兼容壳；原许可证和署名保留。完整 diff 与本目录结果清单供后续追溯。

## 后继：日常制品替换授权

2026-09-24 所有者要求提交推送并将精简 TaCZ 打包替换到日常测试入口，并将此设为以后默认收尾流程，已写入 fork AGENTS.md。后续运行版本以主仓 dependency-lock.json 为准；下文“锁不变/未推送”均为首轮离线交付时点，不能当作替换后的状态。既有 source-audit/artifact-audit 保留首轮证据；制品替换后，旧审计脚本中的原运行锁不变断言不再适用。新运行制品使用主仓 verifyTaczDependency / verifyRuntime 校验。

## 已完成的离线交付

实际删除 31 个 Java 文件、2,124 物理行，另删两个 KubeJS 文本入口；清调用、依赖、版本别名、测试依赖、Mixin 和 21 份语言文件。其中生产 Java 净减 2,347 行；生产源码/资源/构建范围共 87 文件，新增 35 行、删除 4,509 行，净减 4,474 行；此数字不含新增测试和审计文档，不是性能收益。详见[逐文件来源、删除理由与哈希](source-audit.json)。

- `29502d3e`：JEI / Cloth / Carry On 退出。
- `5a6a1c1c`：Controllable 退出与 M870 解析回归。
- KubeJS 退出及本批证据随本页后继提交记录；无推送。

执行 `bash gradlew compileJava compileStandaloneCore verifyPlatformExtensionBoundary adapterTest jar developmentJar --offline --console=plain`：通过（1m 41s，16 tasks，15 executed / 1 up-to-date）。4 项平台扩展测试与 32 项 adapter 测试通过；包括实际 NeoForge 总线取消开火/换弹、LuaJ 配件公式、M870 display 加载。15 枪 / 146 物理物品资源检查实际通过。编译仍有既有 API 弃用及 unchecked 警告。

额外执行 `verifyCorePlatformBoundary verifyWeaponModules verifyAdapterIntegration` 和带独立版本号的 `jar developmentJar`：通过（37s，14 tasks，9 executed / 5 up-to-date）。第二次打包仅改版本元数据，沿用前一次已通过的内容验证，显式 `-x verifyConfiguredWeapons`；不称为全新干净构建。移除 Rhino 后的 `compileTestJava --offline --console=plain` 也通过（1m 10s）。本批没有跑全量 `check`，也没有 registry-backed `test` 或 Minecraft 启动验收。

候选都留在 fork `build/libs/`（正式/开发仅选一个）：

- [正式 Jar](../../../build/libs/tacz-neoforge-1.21.1-1.1.8-hotfix-r6-compat-slimming.20260924.jar)，60,597,461 bytes。
- [开发 Jar](../../../build/libs/tacz-neoforge-1.21.1-1.1.8-hotfix-r6-compat-slimming.20260924-development.jar)，60,830,255 bytes。

[制品审计与 SHA-256](artifact-audit.json)：两包均无 31 个退役类（含内部类）、KubeJS 文本入口、手柄 Mixin 或退役外部 API 字节码引用；保留平台服务、四类配置、配置命令、渲染/动画兼容、15 枪索引、20 份 Lua、LuaJ 内嵌依赖及靶子/靶车/展示架。M870 JSON 与源文件逐字节一致。双方锁和主仓 vendor 制品哈希与起点相同。可在 fork 运行 `python3 docs/newmod/compat-slimming/verify_artifacts.py` 重验上述两包。

## 行为边界

- JEI 不再展示 TaCZ 配件查询；公共组装与配件计算保留。
- Cloth 配置页、缺依赖下载提示、Alt+T 入口退出。四类 ModConfigSpec、配置持久化和配置命令保留。
- Carry On 不再收到 TaCZ 黑名单。另装该 Mod 时靶子、靶车或展示架是否能搬运，以及搬运后的物品/内容保存，均待实机核验。
- Controllable 专用输入、Mixin 和震动退出，普通键鼠逻辑保留。M870 源与生成 display 中未消费的 `controllable` 元数据原样保留，不手改生成物。
- KubeJS 专项事件/API 退出；外部脚本不能继续调用旧入口。NeoForge 事件类型、取消语义、LuaJ 和枪包 Lua、配件属性计算保留。
- Iris、Sodium、Accelerated Rendering、Shoulder Surfing、Player Animator 以及平台 META-INF/services 保留。15 枪和靶子/靶车/展示架资源未裁剪。

## 集中实机清单（均未执行）

使用新建隔离客户端目录和世界，不复用日常世界。不自动晋升候选 Jar。

1. **带精简 TaCZ**：只加载正式或开发候选其中一个，确认 15 枪获取、库存/装备图标、组装预览与换件；键鼠开火、瞄准、切模式、换弹、检视、趴下、近战、缩放；M870 与使用 Lua 的枪械动作；配件属性与 NeoForge 事件取消；四类配置加载、修改与保存重进；靶子、靶车、展示架及渲染/玩家动画。
2. **附加 Mod 场景**：可用时分别加 JEI、KubeJS、Controllable、Cloth、Carry On；检查无残余插件/Mixin/类加载异常，已退出功能确实缺席。记录 KubeJS 旧脚本失效、Cloth 无配置页和 Carry On 搬运的预期变化。缺少附加 Mod 条件则保持未验。
3. **无 TaCZ 完整组合**：另一个新目录，显式包含公共七模块、自有枪和 raid，确认 Mod 列表无 TaCZ；M4A1/Radian 获取、预览和换件；背包、装备、仓库；弹药装填/混装、射击、伤害与护甲；角色体力；保存重进；raid 进入/退出、死亡/撤离与库存保留。现有 firearms 最小宿主不等于完整组合，raid 宿主需核显式 firearms sourceSet。

无 TaCZ 的公共七模块独立编译与所查类路径此前通过（见调查），本批未重跑或提升结论；客户端/服务端启动与完整玩法仍未验。无 TaCZ 与带精简 TaCZ 是两套验收，不可相互替代。

## Deferred

旧枪匠配方链、默认包共享资源裁剪、构建证据资源排除、公共供弹/伤害迁移和另建 Mod 仍归主仓调查，未处理。双方公共依赖锁、主仓 vendor 制品与运行锁不变。

新增测试曾通过生产 Gson 成功读取 M870，随后尝试将对象序列化回 JSON 时触发现有 `Vector3fSerializer.serialize` 对空 JsonArray 调用 `set(0, ...)` 的越界。该序列化器本批未修改；本批只验证加载，不用序列化往返代替加载验收。序列化输出需求另行复现/修复。
