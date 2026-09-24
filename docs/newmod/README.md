# TaCZ fork 工程入口

本页负责当前阅读顺序；实际交付状态见[实验室状态](../../status.md)，逐次操作见[改动记录](改动记录.md)。旧文档中的“当前”、旧版本和旧模块命令仅在其批次有效。

正式工作目录：`NewMod/TACZ-1.21.1`，当前主线为 `dev`。2026-09-19 从原实验室迁入；原同级实验室路径不再使用。

## 当前阅读路径

1. [任务盘点](任务盘点.md)：已接受范围、待执行的附件替换与保留边界。
2. [内部模块](../../modules/README.md)与[战术接入](../../modules/tacz_adapter/README.md)：单一 TaCZ 包和公共玩法依赖。
3. [枪械生产来源](../../modules/tacz_adapter/weapon-sources/README.md)：十五把正式枪；M4 专用生成器、其他十四枪公共生成器。
4. [全量瞄具交付](../assembly-experiment/optics-batch/README.md)：28 件独立源、222 组兼容关系，所有者已整体接受。
5. [文档治理台账](文档治理.md)：全仓文档角色、历史证据、仍被检查器读取的台账及后续条件。

2026-09-24 首批五组可选兼容退出的离线结果、候选制品与待实机清单见[兼容打薄交付](compat-slimming/README.md)。它不改变历史 V4 或主仓运行锁。

第二批旧枪匠配方退出和构建证据停止打包见[第二批交付](recipe-slimming/README.md)。交付后等待所有者实机，不继续全面内容盘点。

## 权威与边界

- 用户已确认的范围和验收约束实施；代码、作者输入、构建配置描述实际行为。交付报告只证明对应提交，不能覆盖当前源码。
- 本仓只发布一个 `tacz` Mod；`weapon_*`、`tacz_adapter` 是内部源码目录。foundation、tactical、character、combat 继续归 NewMod 公共玩法。
- 十五把正式枪及本地物理零件使用 `tacz_fork_tarkov`。原版附件与新瞄具目前仍并存；下一阶段才做原版配件/瞄具退役、替换和身份统一。
- NewMod 实际运行选择以父级 NewMod 工程 [dependency-lock.json](../../../source/integration-tests/tacz-runtime/dependency-lock.json) 为准；本页不维护另一份版本锁。独立克隆时此链接可能不可用。
- 保护原许可、署名、日常世界和共享动作/声音/弹药/效果。正式包与完整开发包只加载一个。

## 按主题深入

| 主题 | 入口 |
| --- | --- |
| 实验批次与交付证据 | [融合与组装索引](../assembly-experiment/README.md) |
| 编辑原生枪 | [M4 作者合同](../../modules/tacz_adapter/weapon-sources/native_m4a1/README.md)、[其余枪生成流程](../../tools/native_guns/README.md) |
| 非瞄具与光学源 | [非瞄具源](../../modules/tacz_adapter/weapon-sources/native_attachments/README.md)、[光学源](../../modules/tacz_adapter/weapon-sources/optics/README.md) |
| 弹药 | [物品链退役与保留边界](../assembly-experiment/弹药链路与清理.md)；具体弹药定义、注册与详情展示归 NewMod `tarkov_content`，本仓库不再持有 `ammunition/` 目录 |
| 早期清理与来源证据 | [清理](cleanup/README.md)、[清理前盘点](inventory/README.md)、[额外内容](extra-content/README.md)、[首轮隔离](../content-isolation/README.md) |

历史 RouteLedger 区块号和早期运行锁保留在各批次记录中；它们不表示当前绑定或当前授权。本次文档治理未写 RouteLedger。

第三批 R03/R06/R08/R10 的边界、删除清单和交付检查见[内容退出交付](content-slimming/README.md)。

slim3 已由所有者验收。随后授权的 R07/R09、退休尾项与开发调试隔离见[slim4 交付](presentation-slimming/README.md)。
