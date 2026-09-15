# 雷电 Model 1 FA 接入

源枪：**雷电 Model 1 FA 5.56x45 突击步枪 / Radian Weapons Model 1 FA 5.56x45 assault rifle**。使用 `original-components/4.1.3/radian-original-20260914-r2` 的组件，原预设 ID `68d3db3e0c47fe28c2067ab9`；本体 1 件、配件 12 件，默认展开 MBUS 机瞄，无瞄准镜、灯或镭射。

## 领取和验收

启动根目录 `启动开发客户端.command`，在 `/devitems` 选择 `radian` 来源，领取整枪及配件。枪放入主武器槽并切换持枪，Z 打开真实组装工作台。武器 ID 为 `newmod_radian:radian`；完整预设有 12 个真实配件，30 发弹匣，半自动/全自动。领取预设不自动赠送弹药，使用已接入的 5.56×45 弹种。

配件名称从 EFTForge 正式 `Name` 字段对应的中英文词表读取，并精确保存在 [names.json](names.json)。完整数据见 [items.raw.json](items.raw.json)，预设见 [preset.source.json](preset.source.json)。模型目录的简写只用于来源追溯，不用作物品展示名称。

## 当前结果与边界

2026-09-14 已实现并编译，**按用户要求没有启动游戏测试，待用户亲自验收**。不要把接入、静态预览或编译打包写成运行验收成功。

- 13 个型号、26 个网格、66,773 个三角面；完整组件矩阵参与转换，原 UV 保留，原白模与原稿不改动。
- 原创 6 种基础材质：石墨色阳极金属、炭灰色金属、磷化钢、加工钢、黑色聚合物与橡胶。尾垫和弹匣底板独立分区；12 个配件图标使用同一数据。
- 枪械身份、预设、口径、开火条件、材质、模型、工作台入口全部经通用配置注册。ADAR 一并迁入共用逻辑，保留其已有物品和存档身份。
- 枪械模式、口径、容量与原始数据核对；TaCZ 后坐力、时间及声效仍为本工程演示配置，不宣称完整还原原作手感。
- 当前默认只显示展开机瞄；折叠源文件保留在 source-pack，不同时叠加。折叠交互/动画、本体内部独立活动件动画、跨枪共有配件目录、动态整枪图标与完整 PBR 留后续。实体弹匣仍为结构件，未实现带弹弹匣物品生命周期。
- 持枪/机瞄/换弹参数尚未在客户端校准验收。请重点看左右手接触点、机瞄光轴、射击模式、拆匣退弹和实际帧率；新模型网格量明显高于 ADAR。

[材质静态预览](material-preview.png)由正式几何与材质离线渲染，透明背景，**不是游戏截图**。

## 维护入口

唯一主流程：[枪械导入流程](../../../source/mods/tacz_adapter/docs/枪械导入流程.md)。材质只在[材质补充说明](../../../source/mods/weapon_assembly_ui/材质使用说明.md)维护。雷电创作配置位于 `source/mods/tacz_adapter/weapon-authoring/radian/`。

根目录重建：

```sh
python3 source/mods/tacz_adapter/tools/build_weapon.py source/mods/tacz_adapter/weapon-authoring/radian
./source/gradlew -p source assemble :tacz_adapter:compileDevelopmentJava verifyModuleBoundaries verifyLocalization verifyDistribution
```

本次上述编译、模块边界、本地化和制品检查全部通过，产物为 `source/mods/tacz_adapter/build/libs/tactical_tacz_adapter-0.0.0-dev.jar`。资源生成和静态检查不启动 Minecraft。所有源文件快照与哈希见 [source.json](source.json)，矩阵、挂点和数量见 [归档 import-report.json](../../../source/mods/tacz_adapter/weapon-reports/archive/radian/import-report.json)。模型单位沿用共同艺术坐标，没有改称毫米；源包状态文件保留当时原文，当前 Mod 接入状态以本页为准。

RouteLedger Todo `b11c3ae7-d1a6-4a05-aa9c-57ab96359286` 保持待验收；用户提出“只编译，验收我来做”后，原计划中的运行 smoke/audit 本批不执行。
