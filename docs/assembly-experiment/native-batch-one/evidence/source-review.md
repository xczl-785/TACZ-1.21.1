# 三枪原生来源独立核对

日期：2026-09-17。只读对比实际运行 JSON / PNG 和保留的原生枪包，不将作者的 validate 作为唯一依据。未启动游戏。

| 枪械 | 保留原生骨骼 | 高模 cubes | 低模 cubes | 图集尺寸 |
| --- | ---: | ---: | ---: | --- |
| M16A1 | 85 | 550 | 423 | 256×256 |
| SCAR-L | 105 | 862 | 633 | 512×512 |
| UMP45 | 69 | 473 | 347 | 256×256 |

三枪均通过以下独立检查：

- display 除 `model`、`model_type`、`texture`、`lod` 四个预期字段外，与原枪一致；原生 data 去掉注释后逐结构相等。
- 高低模型中每个原生骨骼的名字、父级、pivot、rotation 等非 cube 元数据逐项相等；没有重复骨名，原生父级引用有效。保留动画、抛壳、枪口、手部、视角和附件挂点结构。
- 当前图集大小及 RGBA 像素与原纹理相等。原始枪包目录无 Git 修改。
- 引用的原生 animation、状态机、player animator（存在时）、服务端换弹 script（存在时）均能解析到实际文件。
- 三枪 display model_type 与各自 production.weapon.modelType 相符，图标目录各自独立，运行 visibility 配置与最新生产配置相等。
- M16A1、SCAR-L 的 `bolt` 增加对应实体定义门禁，并保留 `bullet_in_barrel` 对枪管的门禁。原 M16 bolt 子树只含 bolt/group 枪机几何和膛内弹药；原 SCAR bolt 子树只含 bolt/octagon3 枪机几何和膛内弹药。该父门禁不会隐藏其它独立部件。

编辑源的真实 Blockbench 往返另见 [blockbench.json](blockbench.json)：38 个新源、1716 cubes，编辑前后位置误差 0，UV 与骨架一致。SCAR 前后瞄具的直立与折叠几何保存在同一实体定义中，源数量不按姿态重复计数。

本记录不是实机动作或视觉验收。低模保留原生骨架并减少部分 cubes，不代表已验证远距离视觉质量或实际 FPS。光学仅沿用既有原生投影，没有新增瞄具编辑制作。
