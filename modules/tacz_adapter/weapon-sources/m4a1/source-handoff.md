# M4A1 标准版导入交接

供 Mod 导入 thread 使用。2026-09-15 核实；本次只整理交接，未装配、绑定材质或修改 Mod。

## 目标与当前状态

导入 SPT 5.0 原版默认 **M4A1_Std**：M4A1 本体、14.5 英寸枪管、A2 消焰器、卡宾枪上下护木、提把式照门、固定准星、M4SS 枪托、A2 握把和 STANAG 30 发弹匣，完整组成见下表。

- 武器模板 ID：`5447a9cd4bdc2dbd208b4567`。
- 预设 ID：`5af08cf886f774223c269184`；预设根实例：`6396b722d604e334650f9726`。
- 共 **13 个实例／13 个不同模板（本体＋12 件配件）**，全部来自 `bulk-white-20260914`，没有缺件。
- 13 件均为 `awaiting-consumption`、`not-integrated`，白模制作已完成；单件原生载入、视觉验证均为 `not-validated`。本批其他三件抽检不能替代 M4A1 验证。
- 本次重新核对 13 对原稿／产出 SHA-256，全部匹配 component.json，且单件说明、元数据和证据文件存在。检查脚本约 **0.20 秒**；文档整理整体未单独计时。这不是重新执行全部几何验证或游戏验收。

## 文件入口

工作区绝对路径：`/Volumes/新加盘/BlockBenchAndTa rkov`。主工程为其下 `blockbench-tarkov-test`，不是 Mod 源工程。

先读本页，再按所需组件读取元数据，不必扫描全库。

- [临时清单](../../tarkov2blockbench-output/临时工作区/catalog.json)：按 `item_id` 取条目，路径相对该清单所在目录。
- 每件产出：`blockbench-tarkov-test/tarkov2blockbench-output/临时工作区/产出物/<item_id>/model.bbmodel`。
- 同目录 `component.json` 提供模型哈希、来源、坐标矩阵和原稿引用；`source-evidence.json` 提供节点／网格证据。
- 每件原稿：`blockbench-tarkov-test/tarkov2blockbench-output/临时工作区/原稿/<item_id>/main.bbmodel`。
- 原稿旁 `input/` 保存来源 `component.bbmodel`、`bindings.json`、`nodes.json`、`status.json`。input 可能含 Tarkov 贴图，仅作证据，禁止作为交付材质。
- [交付与坐标规则](../全局资产/网格导出与交付.md)；[批次验证与计时](../../work/modeling-batches/bulk-white-20260914/README.md)。

原稿保留全部来源 LOD／状态，实际消费优先使用 `model.bbmodel` 中已选的可见静态网格，避免把全部 LOD 或互斥状态一起导入。

## 组件、父级与推荐材质

下表“父模板”由原版预设 `parentId` 关联实例后得到。此预设模板不重复，因此可用模板 ID 清晰表示树；通用导入器仍应保留实例关系。组件名链接直接打开该件元数据。

材质是本次建议，尚未形成经验证的网格／面绑定；先依据单件证据和实际模型划分表面，再应用配方。

| 组件／模板 ID | 父模板 | 原始 slotId | 推荐主材质 | 分区提示 |
|---|---|---|---|---|
| [柯尔特 M4A1 5.56x45 卡宾枪](../../tarkov2blockbench-output/临时工作区/产出物/5447a9cd4bdc2dbd208b4567/component.json)<br>`5447a9cd4bdc2dbd208b4567` | `—` | `根` | `coated_metal_black` | 内部枪机、扳机等独立金属区域可用 steel_dark |
| [柯尔特A2 AR-15手枪式握把](../../tarkov2blockbench-output/临时工作区/产出物/55d4b9964bdc2d1d4e8b456e/component.json)<br>`55d4b9964bdc2d1d4e8b456e` | `5447a9cd4bdc2dbd208b4567` | `mod_pistol_grip` | `polymer_black` | 独立螺钉用 steel_dark |
| [柯尔特 5.56x45 STANAG 30发弹匣](../../tarkov2blockbench-output/临时工作区/产出物/55d4887d4bdc2d962f8b4570/component.json)<br>`55d4887d4bdc2d962f8b4570` | `5447a9cd4bdc2dbd208b4567` | `mod_magazine` | `steel_dark` | 灰色金属弹匣；不要套用聚合物弹匣配方 |
| [柯尔特M4A1 5.56x45突击步枪上机匣](../../tarkov2blockbench-output/临时工作区/产出物/55d355e64bdc2d962f8b4569/component.json)<br>`55d355e64bdc2d962f8b4569` | `5447a9cd4bdc2dbd208b4567` | `mod_reciever` | `coated_metal_black` | 独立内部金属区域可用 steel_dark |
| [14.5 英寸 AR-15 枪管 5.56x45](../../tarkov2blockbench-output/临时工作区/产出物/55d3632e4bdc2d972f8b4569/component.json)<br>`55d3632e4bdc2d972f8b4569` | `55d355e64bdc2d962f8b4569` | `mod_barrel` | `steel_dark` | 枪管保持克制深灰 |
| [柯尔特USGI A2 5.56x45 AR-15消焰器](../../tarkov2blockbench-output/临时工作区/产出物/544a38634bdc2d58388b4568/component.json)<br>`544a38634bdc2d58388b4568` | `55d3632e4bdc2d972f8b4569` | `mod_muzzle` | `steel_dark` | 与枪管同系列 |
| [柯尔特 M4准星](../../tarkov2blockbench-output/临时工作区/产出物/5ae30e795acfc408fb139a0b/component.json)<br>`5ae30e795acfc408fb139a0b` | `55d3632e4bdc2d972f8b4569` | `mod_gas_block` | `steel_dark` | 固定准星／导气箍；保留瞄准轮廓 |
| [柯尔特 M4 卡宾枪长度 AR-15 护木](../../tarkov2blockbench-output/临时工作区/产出物/5ae30db85acfc408fb139a05/component.json)<br>`5ae30db85acfc408fb139a05` | `55d355e64bdc2d962f8b4569` | `mod_handguard` | `polymer_black` | 内侧隔热金属若能明确分区，可少量用 steel_light |
| [柯尔特 M4 卡宾枪长度 AR-15 下护木](../../tarkov2blockbench-output/临时工作区/产出物/637f57a68d137b27f70c4968/component.json)<br>`637f57a68d137b27f70c4968` | `5ae30db85acfc408fb139a05` | `mod_handguard` | `polymer_black` | 与上护木保持纹理比例、颜色一致 |
| [AR-15提把式照门](../../tarkov2blockbench-output/临时工作区/产出物/5ae30bad5acfc400185c2dc4/component.json)<br>`5ae30bad5acfc400185c2dc4` | `55d355e64bdc2d962f8b4569` | `mod_sight_rear` | `coated_metal_black` | 照门及调节件可用 steel_dark；无须引入镜片材质 |
| [柯尔特 AR-15 卡宾枪缓冲管](../../tarkov2blockbench-output/临时工作区/产出物/5649be884bdc2d79388b4577/component.json)<br>`5649be884bdc2d79388b4577` | `5447a9cd4bdc2dbd208b4567` | `mod_stock` | `coated_metal_black` | 与机匣保持一致 |
| [High Standard M4SS AR-15 枪托](../../tarkov2blockbench-output/临时工作区/产出物/55d4ae6c4bdc2d8b2f8b456e/component.json)<br>`55d4ae6c4bdc2d8b2f8b456e` | `5649be884bdc2d79388b4577` | `mod_stock_000` | `polymer_black` | 锁定机构可用 steel_dark；勿仅凭托垫形状认定为橡胶 |
| [柯尔特AR-15拉机柄](../../tarkov2blockbench-output/临时工作区/产出物/55d44fd14bdc2d962f8b456e/component.json)<br>`55d44fd14bdc2d962f8b456e` | `5447a9cd4bdc2dbd208b4567` | `mod_charge` | `coated_metal_black` | 独立锁扣可用 steel_dark |

特别注意：上机匣槽位的原文是 **`mod_reciever`**，保留这个拼写；下护木挂在上护木的 `mod_handguard`，固定准星挂在枪管的 `mod_gas_block`，枪托挂在缓冲管的 `mod_stock_000`。不要把所有配件直接挂到本体。

## 材质读取与样板建议

[材质库说明](../../assets/material-library/README.md) · [预览](../../assets/material-library/preview.html) · [机器清单](../../assets/material-library/catalog.json)

推荐保留黑／深灰标准外观，让机匣、钢件和聚合物有克制的表面差异。本套没有木件，不需木纹；没有光学镜片，不扩展玻璃策略。

| 配方 | 基础图（相对 material-library） | 当前乘色 | 用途 |
|---|---|---|---|
| [coated_metal_black](../../assets/material-library/profiles/coated_metal_black.json) | `textures/coated_metal.png` | `#FFFFFF` | 机匣、缓冲管、拉机柄、提把主体 |
| [steel_dark](../../assets/material-library/profiles/steel_dark.json) | `textures/steel_dark.png` | `#FFFFFF` | 枪管、消焰器、准星、弹匣及独立钢件 |
| [polymer_black](../../assets/material-library/profiles/polymer_black.json) | `textures/polymer.png` | `#505459` | 握把、上下护木、枪托主体 |
| [steel_light](../../assets/material-library/profiles/steel_light.json)（可选） | `textures/steel_light.png` | `#FFFFFF` | 有分区依据的少量裸露金属／隔热片 |

`rubber_black` 仅在模型证据确认存在独立橡胶区域时再使用，不把整块枪托处理成橡胶。浅色钢也不代表实物金属种类，只是本库外观配方。

- 保留 1254×1254 原图。建议在 Mod 工作副本先生成 **256×256** 游戏纹理作为样板，采用保留像素块边界的最近邻缩放起步；这是待观感验证的建议，不是已确定规范。若目标项目已有统一分辨率／采样规则，优先沿用并记录。
- 配方当前 `texture_scale=1` 可作起点，逐件检查像素密度，尤其上下护木接缝、枪管纵向纹理、弧面拉伸和 UV 岛边缘。同一材质共用基础图，避免每件复制一张纹理。
- `tint_srgb` 是乘色，不能既烘焙进图片又重复乘色。混合表面按网格／面绑定，不以整件一种材质替代分区。
- [NewMod library.json](../../assets/material-library/adapters/newmod/library.json) 与 [copy-map.json](../../assets/material-library/adapters/newmod/copy-map.json) 可作适配入口，实际字段和资源路径由导入 thread 核对目标工程当前代码。
- 工作区说明记录当前 Mod 使用基础色、乘色和 UV 比例；`roughness/specular` 仅预留，不代表已有 PBR 高光。预览也未证明游戏效果或无缝平铺。

## 导入时必须核对的行为

1. **装配坐标**：模型为 Blockbench free 网格，顶点在组件局部坐标。`local_to_source_baked` 只恢复该组件来源 BB 坐标，不能直接当作父子安装矩阵；来源位置也不等于整枪装配位置。结合 `nodes.json`、`bindings.json` 与目标 Mod 挂点规则建立变换，核对单位、轴向、朝向、枪口位置和机瞄对齐。避免对已烘焙顶点重复叠加 `mesh.origin` 平移。
2. **资源与逻辑**：保持 13 件原配置，使用可复用的组件／材质配置方式接入。30 发弹匣是预设组件，不代表默认装填了 30 发弹药；弹药、射击模式、动画和枪械数值须读取 SPT 5.0 对应物品数据并对照 Mod 支持，不由白模清单推导。本次未核实这些运行参数。
3. **检查与交接**：先验证完整装配和材质分区，再检查第一／第三人称、持枪尺寸、瞄准、开火／换弹及弹匣显示。编译、静态检查、运行检查和用户验收分别记录；尚未做的明确保留。
4. **消费登记**：导入 thread 返回具体消费修订／哈希、Mod 资源路径、材质映射、检查结果与问题。复制文件不是消费确认。确认后按交付规则迁正式；新材质工作先保存在临时区或 Mod 工作副本，不覆盖白模、13 件旧配色试验或 24 件既有正式资产。若改变当前交付修订，同步元数据／哈希／引用并保留历史。

本页待验证项：安装矩阵、纹理最终分辨率与比例、分面绑定、目标 Mod 参数与动画支持、完整游戏观感。问题在本页或导入工程的关联记录持续保留，不能以“组件已齐”标记整枪验收完成。

## 原始预设证据

来源：工作区 `tarkov-resource-library/sources/5.0/database/globals.json` → `ItemPresets["5af08cf886f774223c269184"]`。

本次核对该源文件 SHA-256：`951b70ac4b69dbcd2a74a5aeea088c60e658d6e0edc3a6c001b0875a0916bfe7`。与[预设覆盖报告](../../work/modeling-batches/bulk-white-20260914/reports/preset-coverage.md)来源记录一致。下面保留完整原始预设，便于导入脚本直接获得实例与槽位；它不包含经验证的安装矩阵。

```json
{
  "_id": "5af08cf886f774223c269184",
  "_type": "Preset",
  "_changeWeaponName": true,
  "_name": "M4A1_Std",
  "_parent": "6396b722d604e334650f9726",
  "_items": [
    {
      "_id": "6396b722d604e334650f9726",
      "_tpl": "5447a9cd4bdc2dbd208b4567",
      "upd": {
        "Repairable": {
          "Durability": 100,
          "MaxDurability": 100
        }
      }
    },
    {
      "_id": "6396b722d604e334650f9727",
      "_tpl": "55d4b9964bdc2d1d4e8b456e",
      "parentId": "6396b722d604e334650f9726",
      "slotId": "mod_pistol_grip"
    },
    {
      "_id": "6396b722d604e334650f9728",
      "_tpl": "55d4887d4bdc2d962f8b4570",
      "parentId": "6396b722d604e334650f9726",
      "slotId": "mod_magazine"
    },
    {
      "_id": "6396b722d604e334650f9729",
      "_tpl": "55d355e64bdc2d962f8b4569",
      "parentId": "6396b722d604e334650f9726",
      "slotId": "mod_reciever"
    },
    {
      "_id": "6396b722d604e334650f972a",
      "_tpl": "55d3632e4bdc2d972f8b4569",
      "parentId": "6396b722d604e334650f9729",
      "slotId": "mod_barrel"
    },
    {
      "_id": "6396b722d604e334650f972b",
      "_tpl": "544a38634bdc2d58388b4568",
      "parentId": "6396b722d604e334650f972a",
      "slotId": "mod_muzzle"
    },
    {
      "_id": "6396b722d604e334650f972c",
      "_tpl": "5ae30e795acfc408fb139a0b",
      "parentId": "6396b722d604e334650f972a",
      "slotId": "mod_gas_block"
    },
    {
      "_id": "6396b722d604e334650f972d",
      "_tpl": "5ae30db85acfc408fb139a05",
      "parentId": "6396b722d604e334650f9729",
      "slotId": "mod_handguard"
    },
    {
      "_id": "6396b722d604e334650f972e",
      "_tpl": "637f57a68d137b27f70c4968",
      "parentId": "6396b722d604e334650f972d",
      "slotId": "mod_handguard"
    },
    {
      "_id": "6396b722d604e334650f972f",
      "_tpl": "5ae30bad5acfc400185c2dc4",
      "parentId": "6396b722d604e334650f9729",
      "slotId": "mod_sight_rear"
    },
    {
      "_id": "6396b722d604e334650f9730",
      "_tpl": "5649be884bdc2d79388b4577",
      "parentId": "6396b722d604e334650f9726",
      "slotId": "mod_stock"
    },
    {
      "_id": "6396b722d604e334650f9731",
      "_tpl": "55d4ae6c4bdc2d8b2f8b456e",
      "parentId": "6396b722d604e334650f9730",
      "slotId": "mod_stock_000"
    },
    {
      "_id": "6396b722d604e334650f9732",
      "_tpl": "55d44fd14bdc2d962f8b456e",
      "parentId": "6396b722d604e334650f9726",
      "slotId": "mod_charge"
    }
  ],
  "_encyclopedia": "5447a9cd4bdc2dbd208b4567"
}
```
