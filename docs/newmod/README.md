# NewMod TaCZ fork 改造入口

状态：15枪与85配件清理已实施，构建及静态验证通过；弹药制作链B1已迁入，运行注册/供弹B2与NewMod切换尚未完成。更新：2026-09-13。

目标：沿用 TaCZ 枪械引擎和现有模型、动画等资源，筛选所需枪械，把选定弹药内容归入 fork，统一描述与重量，再交付 NewMod 验证。不是另写一个枪械引擎。

## 渐进式阅读

1. [本轮清理结果](cleanup/README.md)：最终裁决、逐文件删除记录与验证。
2. [本轮盘点结果](inventory/README.md)：54枪、资源引用、86弹药迁移与自研留证。
2. [任务盘点](任务盘点.md)：范围、顺序、待决定问题和验收。
2. [改动记录](改动记录.md)：只记录实际发生的改动，作为唯一 fork 改造流水账。
3. [既有隔离](../content-isolation/README.md)：首批清单、资源依赖和现状；其中 JSON 是本次开始时的基线，不代表最终删枪结果。

弹药当前入口：[制作链与B2切换边界](../../ammunition/README.md)。

## 工程地图

| 位置 | 职责 |
| --- | --- |
| `src/main/java/com/tacz/guns/` | 现有枪械程序；resource 管加载，item 管物品，client 管显示，api 管扩展接口 |
| `src/main/resources/assets/tacz/custom/tacz_default_gun/` | 默认枪包，包含枪械数据、展示和美术等资源；先查引用再删除 |
| `src/main/resources/` | Mod 元数据、语言和其他资源，保留原许可标记 |
| `docs/content-isolation/` | 已有选择、文件哈希及隔离验证证据 |
| `tools/` | 现有审计/验证脚本，后续按批次扩展 |
| `docs/newmod/` | 本 fork 的当前任务和改动记录 |
| `build.gradle.kts`、`gradle/` | 原有构建入口，继续沿用 |

不预先搬动源码、资源或新建空模块。具体迁移路径在弹药批次盘点后确定。

## 基线与外部接入

本地独立仓库 `TACZ-1.21.1`，分支 `dev`；origin `xczl-785/TACZ-1.21.1`，upstream `MUKSC/TACZ-1.21.1`。本次开始基线 `01e24c1e4ea4ba63428f0e564ce5af3d47f3969d`；上游参考 `ff715d80176f9ca61f5b2e6f029864f5adf209c2`。

NewMod 当前运行锁已指向上述隔离提交。工作区配套资料（独立克隆本 fork 时可能不可用）：

- [运行锁](../../../source/mods/tacz_adapter/dependency-lock.json)与[运行接入记录](../../../source/mods/tacz_adapter/docs/self-built-runtime.md)。
- [许可证核对](../../../docs/进行中/TaCZ改造许可证核对.md)：代码 GPLv3，资源 CC BY-NC-ND 4.0；本地推进不等于许可公开修改资源。
- [86种弹药现状](../../../source/mods/tarkov_content/docs/保留口径弹药接入.md)。

RouteLedger 已核对绑定为 NewMod 主项目，当前插入区块为 `203f4032-4182-453b-8cb8-f92fb00268cf`（TaCZ fork清理与弹药归入）。历史闭合节点不允许插入，故在路线尾部追加后切为当前，不改变其他暂缓区块。
