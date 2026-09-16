# 三枪公共运行投影只读审核

审核日期：2026-09-17。范围：M16A1、SCAR-L 的源配置与公共运行时合同；UMP45 只比较通用合同，不将本人制作的 UMP 编辑源作为独立审核通过。此次不运行游戏、不修改生产器或 Java。生成器正在增加显式变体与 LOD，本文区分现有源码审查与最终生成结果验证。

## 已确认的问题

M16A1、SCAR-L 的 `bullet_in_barrel` 都挂在原生 `bolt` 骨骼下，但送审 `boneRequirements` 只要求枪管存在。移除枪机、保留枪管与膛弹状态时，弹药展示仍可浮在枪内。建议分别增加 `bolt: [m16a1_bolt]`、`bolt: [scar_l_bolt]`，与子骨骼的枪管要求组成双重条件，不能替换原弹药门禁。已修复：两枪生成配置已分别包含该祖先要求。已读取首轮真实测试 XML，`NativeGunBatchStateTest.chamberRoundRetainsNativeAmmoGateAndRequiresBothBarrelAndBolt` 通过，覆盖本批三枪高低模、拆枪机、拆枪管、恢复及原膛弹门禁。

## 源配置与运行代码核对

- M16A1 和 SCAR-L 原生弹药 ID 均为 `tacz:556x45`，组装配置 `556x45` 相符；默认自动模式均为原生允许模式。原生容量分别为 20/24/27/30 与 30/40/55/70，必须继续使用各枪专属弹匣变体，不能按同种附件 ID 混用外形。
- M16A1 原生是 `fix_sight`、`fix_carry`，不受引擎 `sight/sight_folded` 门禁影响；配置保留固定机瞄，光学安装路径在后照门/提把组件下。没有发现应自动折叠的原生状态被遗漏。
- SCAR-L 原生竖起/折叠几何分别位于 `sight`、`sight_folded` 子树。生产配置已列出全部有几何的竖起/折叠源骨映射；公共运行时按 variant 和 SCOPE 状态选择。最终生成的每个 batch 必须保留这些 variant，不能全部写成 always。
- SCAR-L `handguard_default` 是原生条件盖板：装握把或激光时隐藏。当前配置没有把它放入 alwaysVisibleBones，叶保留原父骨，原条件可继续工作。
- SCAR-L 原厂枪托几何受装配叶控制，外部枪托走 `stock_pos`；`ar_stock_adapter` 继续留在原生 `attachment_adapter` 子树，由原附件 index 的 adapter 名称控制。不要把适配器变成无条件常显，也不要将外部枪托再复制进 inline 叶。
- 公共附件 renderer 复用原生挂点矩阵与委托时机；SCAR 激光挂点存在 Z 轴 90° 旋转。工作台投影烘焙挂点一次，持枪附件沿原挂点一次，不得在附件资源中再烘焙。
- 显式骨骼依赖包裹原 functional renderer，不写动画 visible。额外弹匣在同一次枪械绘制中重绘 magazine 子树，不需要额外 prepareGeometry；其存在门禁必须与弹匣装配一致。
- 当前公共 Java 渲染/图标/视图入口没有 M4/Glock 枪名分支。支持的附件类型是原生 SCOPE/STOCK/GRIP/LASER/EXTENDED_MAG/MUZZLE；并不宣称支持同类型多挂点或任意插件自定义附件类型。

## 回归检查范围与状态

1. 两枪拆卸枪机、仅留枪管与膛弹时，弹药骨骼额外门禁应拒绝显示；重装恢复；未装膛弹时原门禁仍拒绝显示。
2. SCAR 装卸光学后竖起/折叠叶互斥，拆机瞄不残留任一姿态；高低模均覆盖。
3. SCAR 默认枪托与至少一个 AR 枪托替换时不双画，适配器仅在对应外部附件下出现；无枪托时两者均不残留。
4. SCAR 握把/激光任一存在时盖板隐藏，二者皆无时恢复；这是持枪原生行为，工作台共享姿态仍属于已明确后排事项。
5. 每枪全部目录配件均有非空 neutral 图标/工作台几何；折叠变体从 neutral 投影排除不能误删整个机瞄。
6. LOD保留原始完整功能骨架、弹药/换弹骨骼与可选姿态；低模输出不能只剩源低模不兼容拓扑。

## 首轮测试与共享枪托接线复核

首轮 XML 中 `NativeGunBatchStateTest` 为 6/6 通过；`NativeScarDisplayTest` 为 2/3 通过：高低模机瞄姿态、拆卸恢复，以及握把/激光盖板门禁已通过。枪托一项因 MOE 未进入已编辑资源 override 而失败，不能将首轮写成全部通过。

后续共享枪托接线已完成只读静态验证：SCAR 的 9 个 stock 候选均引用 `attachments/authored_stock/`，全部取自既有 M4 编辑源；每件输出局部骨骼/cube/UV 与编辑源反解结果一致，PNG 字节一致。CTR 旧记录通过 component metadata 的 `stock_pos` 身份纳入。SCAR 高低模均保留自己的 `stock_pos`，这些外部 stock 没有同时烘焙进枪体 batch，inline map 为空。独立重算 9 件工作台顶点与 UV，验证正好应用一次 SCAR 挂点，不带 M4 挂点残留或重复变换。

共享枪托本批没有额外减面 LOD，运行时明确回退到完整已编辑模型；这不等于整枪缺少 LOD，也不声明枪托已优化。修复后的 Java 全量结果仍以主任务后续日志为准，此次未重复执行 Gradle。

`ar_stock_adapter` 的实际选择依赖客户端附件 index，无单测加载 fixture；当前仅核对其保留原生 adapter 控制和父骨层级。没有启动客户端，不将这一点写成实机通过。

当前状态：明确膛弹缺口已修复且有注册态回归；共享枪托接线完成静态几何验证，等待主任务复跑 Java。最终制品和实机验收由后续证据确认；本文不替代这些检查。

## 最终注册态复跑（主线程补录）

共享枪托接线后，NativeScarDisplayTest 3/3与NativeGunBatchStateTest 7/7通过。MOE外部资源解析失败已消除；三枪完整纹理图标及拆上机匣后图像变化通过，烘焙图附同目录。ar_stock_adapter仍按上述静态证据边界，真实绘制与动作由所有者抽查。
