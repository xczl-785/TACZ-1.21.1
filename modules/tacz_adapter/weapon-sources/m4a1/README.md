# M4A1 标准预设接入

2026-09-15：本次完成资源生成、静态检查与编译；**游戏验收和最终尺寸映射待所有者对照图**。入口为 `/devitems` 的 `m4a1` 来源，整枪 ID `newmod_m4a1:m4a1`，展示名称“柯尔特 M4A1 5.56x45 卡宾枪”。不要与 TaCZ 原生 `tacz:m4a1` 混淆。

[材质静态预览](material-preview.png) · [握持/瞄准坐标报告](../../../source/mods/tacz_adapter/docs/calibration/m4a1.html) · [标准流程](../../../source/mods/tacz_adapter/docs/枪械导入流程.md) · [工程验证](verification.json)

## 本轮范围与后续归属

长期目标是相同模型单位、可更换配件、装配决定瞄准与握持，再通过代表性预设验证批量流程。本轮是其中的同枪尺寸对照样本，沿用现有运行架构，不新增按 M4A1 名称分支的 Java 类。

- Todo：13 件预设、源哈希/安装矩阵、原版名字、材质分区、持枪/机瞄接入、可重建资源与编译。本轮工程工作完成，实机待验收。
- Deferred：手部精修、换弹动画、动态图标、跨枪共同物品目录、批量接入仍归[通用方案](../../../source/mods/tacz_adapter/docs/握持瞄准与射击反馈方案.md)后续阶段；本轮不启动。
- Constraint：原始素材只读；不改日常世界；保持 `meshScale=0.38` 和第三人称 `0.6`；不将编译当作游戏接受。
- 验收：M4A1 标准外形及13件层级正确；空枪领取、可装填/开火；瞄准正常；与 TaCZ M4A1 同条件截图比较。
- 待裁决：收到图后确定尺寸比例；需要统一考虑接触点、机瞄眼点和枪口，不能只放大网格。

RouteLedger Todo `6d463019-db3e-4003-b931-2a05132dea1a` 保持待验收。

## 来源与装配核对

原交接：[M4A1标准版导入交接.md](source-handoff.md)。来源 SPT 5.0 `M4A1_Std`，预设 `5af08cf886f774223c269184`，本体 `5447a9cd4bdc2dbd208b4567`。全部来自 `bulk-white-20260914` 的产出物，共13实例/13模板/19网格/36,537三角面。

`source.json` 保存外部输入路径与 SHA-256；逐件验证原稿、产出以及 input 中4个文件哈希。`source-pack` 保留原样产出白模、原元数据、节点、绑定和分面证据；不复制原游戏贴图。`component.json` 为派生安装元数据，原版在 `source-component.json`。`items.spt.json` 保存13件完整 SPT 记录；`items.raw.json` 是供当前生成器读取的字段归一化结果，不冒充 EFTForge 原始数据。名称取同版 SPT `ch.json`/`en.json` 的 `<id> Name`，保留原文。

安装公式：父组件装配矩阵 × 父源根逆矩阵 × 源挂点矩阵 × 显式安装矩阵。Unity/BB 轴转换为 `C=diag(-100,100,100,1)`；产出顶点已经局部化，不再累加 `mesh.origin`。根件使用已归一化的源根坐标，运行基准挂点 `[-13.27,-5.8,30]` 与既有AR样本一致。

`placement-components.json` 保存本批源根 GameObject/MonoBehaviour 与 bundle 哈希：12件配件根没有 ModPlacer，只有 PreviewPivot。PreviewPivot 的图标缩放不参与装配。`placement.json` 显式采用本批静态安装 X90°，与各挂点的反向旋转配合；通过整枪预览、完整顶点往返、前后机瞄对齐核对。本轮未检查 SPT 5.0 游戏 DLL，不宣称旧4.1.3动态安装规则已被验证为5.0通用规则；其他批次仍须检查安装元数据，不按版本号自动套用。

下护木仍挂上护木 `mod_handguard`，固定准星挂枪管 `mod_gas_block`，枪托挂缓冲管 `mod_stock_000`，保留 `mod_reciever` 原拼写。上护木产出内另有名为 `handguard_ar15_kac_ris_LOD0` 的可见网格，装配预览显示其端部/内部金属结构；本轮保留供消费白模中的可见选择，并将其单独绑定深灰钢，不因名称而删掉几何。

机瞄取源 `mod_align_rear`、`mod_align_front`；眼距采用 `mod_aim_camera` 与后照门的纵向间距23.9277268源单位。对齐点横向偏差约0.0325源单位，低于现有0.5容差。握持点在握把与下护木上配置；拆下下护木后左手接触点消失，未给它保留悬空点。

## 参数与材质

同版数据确认：800 RPM、半自动/全自动、5.56×45 NATO、外置弹匣、STANAG容量30；容量不是默认装填量。预设固定后坐参考为垂直78.064、水平224.352，由本体119/342与配件修正合计-34.4%得到。继续使用通用后坐响应，装配变化只归一化一次。

射击曲线、动作时长和简易换弹沿用现有闭膛AR演示配置，未提取 Tarkov 动画。子弹行为由既有弹药适配链提供；`execution.json` 中占位弹道值不是原版物理参数。

材质使用交付库原图工作副本 `material-source/`，1254×1254原图保留并记录哈希；生成器最近邻缩到256×256，四个配方共用 `firearm_materials` 命名空间的基础纹理。聚合物只在运行时乘 `#505459`，不重复烘焙乘色。

- 机匣、缓冲管、拉机柄、提把：涂层金属。
- 弹匣、枪管、消焰器、准星及本体独立机件：深灰钢。
- 握把、上下护木外壳、枪托：黑色聚合物。
- 材质绑定具体网格；未把枪托猜成橡胶。整件单网格内无法确证的小螺钉未虚构分区。浅钢配方保留可选，但当前未用于表面。

原UV保留，未引入原游戏图片。基础色预览不代表PBR、无缝纹理、游戏光照或最终观感。几何和材质同源生成独立配件图标，整枪图标仍沿用本体示意。

## 重建与用户检查

不需要外部盘，NewMod 根目录运行：

```sh
python3 source/mods/tacz_adapter/tools/build_weapon.py source/mods/tacz_adapter/weapon-authoring/m4a1
python3 source/mods/tacz_adapter/tools/preview_weapon.py source/mods/tacz_adapter/weapon-authoring/m4a1 docs/参考资料/m4a1-20260915-data/material-preview.png
python3 source/mods/tacz_adapter/tools/calibrate_weapon.py source/mods/tacz_adapter/weapon-authoring/m4a1 source/mods/tacz_adapter/docs/calibration/m4a1.html
./source/gradlew -p source assemble :tacz_adapter:compileDevelopmentJava :weapon_models:test verifyModuleBoundaries verifyLocalization verifyDistribution --console=plain
```

源快照需重新取材时才使用 `prepare_local_components.py`，三个参数为作者目录、外部产出物目录、同版database目录。安装配置必须先核对，不能把该入口当作任意版本的一键批量装配器。原始来源地址见 `source.json`。

所有者检查可在开发客户端新建空白测试世界/角色，开启实际加载的 `raid_gameplay-server.toml` 中 `developmentMode=true`；保留已有日常世界。运行组合为根目录 `启动开发客户端.command`。`/devitems` 选择 `m4a1`，领取整枪1把，放主武器槽并切换持枪；弹药选择 `tarkov_content:ammo_54527a984bdc2d4e668b4567` 60发放口袋，R装填，右键瞄准，Z打开组装台。以相同视角/FOV/距离和站姿拍本枪与TaCZ原生M4A1，尤其第三人称侧面；整枪配件不同则优先对比机匣，不以总长直接定比例。上述是待执行步骤，本轮没有启动游戏。

消费状态在本工程记录为“已接入并编译，待所有者接受”。`consumption.json` 给出产出修订、资源和材质映射及哈希，可返回资产制作流程。未将外部暂存区迁正式、未修改其原稿/旧实验/消费状态；这样不会把未验收内容提前记为已接受。
