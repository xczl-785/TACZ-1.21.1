# TaCZ适配文档入口

当前整合与验收从[第二项交付](../../../docs/assembly-experiment/adapter合并交付.md)开始。本文以下主题合同随源码迁移；旧 JSON 证据保持原样，只证明各自历史批次。当前状态为前两项待用户联合验收，未使用 NewMod 历史路线代替本项授权。

| 用途 | 入口与角色 |
| --- | --- |
| 导入下一把自有模型枪械 | [枪械导入流程](枪械导入流程.md)，主执行入口；[材质补充](../../weapon_assembly_ui/材质使用说明.md)，白模/已带材质分支 |
| 持枪、可换瞄具与射击反馈 | [通用方案及校准流程](握持瞄准与射击反馈方案.md)，当前作者合同与实机待验收范围 |
| Z列表/安装/替换/拆卸 | [改装库存合同](refit-inventory.md)，当前规则；[本批证据](refit-verification.json)，待所有者实机消费 |
| 构建、制品和供弹边界 | [自建运行依赖](self-built-runtime.md)，当前运行说明 |
| devitems目录与来源 | [开发物品目录](devitems-catalog.md)，当前规则 |
| 旧开发弹清除与兼容 | [测试弹退役](test-ammo-retirement.md)，现行退役边界及历史对账 |
| 分批工程证据 | `b2-verification.json`、`extra-content-verification.json`、`devitems-verification.json`、`test-ammo-retirement.json`，各自证明当批范围，不是当前锁或整体接受 |
| 早期桥接追溯 | [evidence/弹药桥接早期记录](evidence/弹药桥接早期记录.md)及截图，仅历史，不可恢复测试弹或按旧单枪假设继续 |

不移动或覆盖旧验证快照；当前实机/交接仍需要它们证明分批范围。各主题接受之后保留作证据，新的状态只更新当前合同、交接及账本。
