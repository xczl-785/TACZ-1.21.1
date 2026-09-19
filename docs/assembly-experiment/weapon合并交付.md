# 第一项：weapon 合并交付

> 历史批次记录（`closed`）：正文中的版本、待验收、未实施和后续安排按文内日期理解，不作为当前运行入口。后继已交付范围及仍保留条件统一见 [当前状态](../../status.md)；原始证据与合同保留，不据此删除检查器输入。


2026-09-15。实施范围来自[整合与组装路线](整合与组装路线.md)的第一项。工程验证通过；主控复核与所有者实机验收待完成。**不进入第二项 adapter 吸收。**

## 结果与迁移

四个 weapon 目录从 NewMod 迁入本仓库 `modules/`，原 Java 包名和资源 ID 保留，由 tacz 一个 Mod 承载。NewMod 不再声明四个子工程或加载四个 Mod，adapter 仍独立，三枪生产代码、模型、动画与资源仍在 adapter。foundation/tactical/character/combat 继续独立，firearms 未修改。

- 130 个原受控文件逐个记录在 [迁移清单](weapon-module-migration.json)：119 个迁移保留，11 个旧构建文件、Mod 元数据和空入口退役。
- 正式 Java 源文件现有 24 个，编译为 72 个类（含内部类）；全部由 TaCZ 唯一提供。
- `WeaponRuntime.register(bus)` 由 `GunMod` 调用一次，仍注册 **`weapon_runtime:profile`**。未改变实体配件状态、库存交换、供退弹或开火判定算法。
- 正式 UI 资源、材质纹理与许可证原样迁入；测试与演示资源不进入玩家 Jar。
- 原三枪定位回归误指向整理前的 adapter 资源路径，改用有来源与哈希的测试快照；这是测试夹具，生产资源仍归 adapter。
- `public-dependency-lock.json` 固定编译所需 foundation Jar，作为外部依赖，未嵌入 TaCZ。NeoForge 对齐主线 21.1.249；根开发运行补齐已用于编译的 Rhino 版本约束，纯算法测试使用独立 JUnit/Gson/JOML 类路径。
- 老弹药保护清单保持不变；[两处源码接入清单](weapon-source-integration.json)严格承接 GunMod 注册与依赖元数据修改，原模型、动画、配件、弹药效果保护没有放宽。

[内部模块与新命令](../../modules/README.md)是旧模块文档的当前入口。实验室 build/check 汇总内部测试；NewMod check 汇总制品唯一性检查。旧独立组装演示脚本已转向实验室 `runAssemblyClient`，日常验收脚本仍是 NewMod 原入口。

## 工程证据

候选树验证：

| 检查 | 结果 |
| --- | --- |
| 实验室 `build`、开发类编译、`prepareAssemblyClientRun` | 通过；仅准备运行配置 |
| 组装 / 模型 / UI 单测 | 27 / 9 / 33 项，全部通过；runtime 原先没有单测，没有虚报为通过用例 |
| 纯 JDK `compilePureJava` | 通过，无 Minecraft/NeoForge 类路径 |
| NewMod adapter 正式与开发编译、`test` | 通过；2 项 JUnit 用例 |
| 原 UI 材质工具 Python 测试 | 3 项通过；通过 ADAR_MODEL_PACK 指定外盘已归档白模，旧 SHA-256 校验通过 |
| adapter `check` 与图标/制作流水线 Python 测试 | 通过；12 项 Python 用例 |
| NewMod 模块边界、分发包、语言与开发命令目录检查 | 通过 |
| 实际运行类路径 | 72 个 weapon 类各有一个 TaCZ 所有者；无四个旧 Mod 及重复类 |
| 原弹药、资源与清理校验 | 86 弹、15 枪、24 口径效果索引、85 配件保护通过；固定退弹政策测试通过 |
| 客户端与服务端实机 | **未启动，未验收** |

最终源码提交 `3b276985` → 制品 `1.1.8-hotfix-r6-newmod.3b276985` → NewMod 接入提交 `3ef90e9`。SHA-512 与完整对应关系以[运行证据](weapon-integration-evidence.json)及 NewMod `source/mods/tacz_adapter/dependency-lock.json` 为准。

构建中既有 NeoForge 过时 API 警告不影响通过。初次迁入测试因意外继承宿主可选集成依赖而离线解析失败，已将算法测试类路径独立并通过最终验证；未改业务逻辑规避失败。补查材质工具时发现原白模路径失效，已增加显式输入路径配置，并在实际归档原件上通过三项测试；未复制或修改模型。上游 `.gitignore` 的全局 test 规则也已对内部模块测试目录解除，全部测试与夹具已纳入 Git。

## 所有者验收步骤

1. 完全退出旧客户端，然后从 NewMod 根运行 `启动开发客户端.command`。继续使用原来的日常世界；不要运行 reset/refill/clear 命令。Mod 列表应有 tacz 和 tactical_tacz_adapter，没有 weapon_assembly、weapon_models、weapon_runtime、weapon_assembly_ui 四个独立 Mod。
2. 在有开发命令权限的日常测试角色运行 **`/devitems`**，按来源 `adar`、`radian`、`m4a1` 筛选领取三枪预设和相应配件。目录键分别为 `newmod_adar:gun/adar`、`newmod_radian:gun/radian`、`newmod_m4a1:gun/m4a1`；这些是目录条目键，**不是 `/give` 物品 ID**。背包/胸挂留出配件返还空间。
3. 通过正常装备流程持枪，按 **Z** 进入真实库存组装。拆一个部件及其子树，确认枪上消失、库存只收到一份；再装回，确认库存扣除与枪上状态一致。关闭重开界面后状态应保持。
4. 退出并重进该世界，检查旧枪及刚修改的枪仍保留装配。枪膛、弹量、已选弹种按原行为保存；旧 `weapon_runtime:profile` 不应丢失。
5. 检查持枪、右键瞄准、射击和 **R** 换弹。拆下必要枪管后应禁止射击，装回恢复。相对原客户端不能新增姿势、瞄准或动画退化；本项没有修正三枪原有交互质量。
6. 原生 TaCZ M4A1 等保留枪也通过 `/devitems` 的 `tacz` 来源领取，按 Z 测安装/替换/卸下兼容配件。扩容配件变化时沿用 adapter 的精确弹种返还，数量不重复，不出现原生 `tacz:ammo`。
7. 射击核对抛壳、枪口与弹道效果，确认依旧可见。本轮没有新增退弹键，也没有改变临时固定口径返还政策。

`/devitems` 的权限要求和三枪目录键已核对实际注册代码；不要求输入并不存在的 kit 命令。实机确认由所有者给出，工程通过不能代替这一步。

## 回退

这次包含源码迁移，**不能只把旧 TaCZ Jar 换回来**：旧包不包含四项能力，需要同步恢复 NewMod 的四个模块与运行声明。

- 开始前 NewMod 基线：`94c31d5921a1a94edc79212e50bf397f53737dd0`。
- 开始前实验室基线：`5fc2f4af74359bf73e65db6dbc5a2191943bed43`。
- NewMod 完成后记录的接入提交可用 `git revert <接入提交>` 整体回退，恢复四模块、原运行锁及旧包组合；先确认没有其他未提交工作。实验室如也需回退，单独撤销本轮工程提交。不要用 reset 覆盖后续任务。
- 旧 `69b1a3af` 制品继续保留。回退不删除或替换任何世界；本轮也未修改世界。遇注册/启动错误先退出客户端并保留日志，交主控判定是否回退。

## 主控复核（2026-09-15）

主控复核通过，可进入所有者实机验收；不代表实机接受。现场核对两仓库提交与干净状态、迁移构建配置、唯一注册入口和最终 Jar：SHA-512 与运行锁一致，72 个 weapon 类由 tacz 唯一提供，Jar 只声明 tacz，foundation 未嵌入。独立重跑 NewMod `:tacz_adapter:verifyWeaponRuntime verifyModuleBoundaries --offline` 通过。比对基线确认 adapter 生产 Java、三枪内容、firearms 与公共玩法模块无差异；其他单测结果依据已提交的实施证据复核，未全部重复执行。第一项仍待所有者验收，第二项未授权启动。
