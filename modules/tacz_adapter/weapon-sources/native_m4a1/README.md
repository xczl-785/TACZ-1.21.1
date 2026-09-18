# 当前默认套件：方块编辑源

配件兼容关系、默认装配和关键槽位路径的编辑入口为 `assembly.json`。`tools/native_m4a1/generate.py` 读取并校验候选、默认组合与循环关系，再生成 catalog、scene 等运行资源；不要直接修改生成文件。模型仍由 editable 提供，原生动画绑定保持原入口。本轮只提取已有关系，安装点及绑定来源统一尚未完成。

15 件默认配件的正式编辑入口为 [editable/README.md](editable/README.md)。它们生成工作台、图标和原生持枪高低模；下面的三角网格说明主要描述旧导出与其余候选。不要编辑默认件的 source-pack 生成物。

# 原生 M4A1 标准配件源模型

## 入口

- `manifest.json`：67 个现有物品身份到独立源模型的映射，另有前瞄具折叠状态模型。
- `source-pack/components/<definition>/model.bbmodel`：Blockbench free mesh 格式，可单独打开；纹理嵌入文件，同时旁边存有原像素 `texture.png`。
- 同目录 `component.json`：安装参考点、烘焙矩阵、槽参考点、几何范围、原生骨骼和动作归属、输入 SHA256。
- `validation.json`：静态导出与雷电转换器校验结果。Blockbench 打开与用户视觉验收由主任务另行记录，不能用此静态报告替代。

## 这次如何合并

按“受到同一组原生动画祖先控制”合并网格。一个配件里不会单独运动的方块合成一个 Mesh 对象，可包含互不连接的几何，不做减面或焊接破坏 UV。

下机匣保留主壳体、空仓挂机解脱件、弹匣释放件、快慢机的独立运动所有权；枪机和拉机柄是独立配件，分别保留各自运动链。前瞄直立/折叠是不同几何状态，折叠文件单独列在 variants，避免工作台把两份重叠显示。

每个输出 mesh 的 origin/rotation 均为零，所有内部骨骼与方块旋转已经烘焙进顶点。非活动块合并不意味着删除原生 held rig 的骨骼，也不意味把动画焊死：原持枪模型和动画文件没有改变。动作归属元数据是后续编辑/重绑依据；这些 .bbmodel 是中立姿态编辑源，不宣称已有可直接播放的 Blockbench 动画。

## 坐标与安装点

采用雷电同向的艺术坐标：X 横向、Y 向上、+Z 枪口，保留原生模型单位。由原生 Bedrock 艺术坐标做 Y 轴 180 度旋转，即 (-X, +Y, -Z)，没有旧工作台预览的 .4 缩放和轴交换。

`model.bbmodel` 顶点在配件局部坐标；`local_to_assembly` 为原生安装骨骼艺术坐标的平移矩阵。消费时使用雷电现有 `convert_component(model, local_to_assembly, selected_anchor)`，不要额外应用已经烘焙的骨骼旋转。

`anchor`/`anchorBone` 保留原始骨骼旋转参考点，`slots` 是候选默认件的绝对源 anchor。原生动画 pivot 经常远离几何，不能直接当作界面标签位置。提供绝对艺术坐标 `geometryBounds`、`boundsCenter` 和 `geometryCenter`，工作台可以改用默认件 boundsCenter 来选择可视化安装参考点，同时对所有候选重新计算相对顶点，保持装配姿态。

## 材质与来源

每个三角面保留原始像素 UV，按项目 BedrockCubeBox/BedrockCubePerFace/BedrockPolygon 的顶点、box UV 和镜像规则转换；输出纹理与原 PNG 字节完全一致。并未用灰色缩略图、随机材质或重新绘制贴图代替。

15 个新增物品模型从项目已有原生 M4A1 方块所有权清单拆分；52 个已有附件保持现有身份，其中扩容弹匣来自 M4A1 自身几何，其余读取原附件模型及纹理。模型版权和来源保持原项目许可，未引入 EFT 外部资产。原生镜片、准星特殊渲染仍由运行时负责；此源模型不模拟瞄具特殊通道。

## 再生成

从仓库根执行：

```sh
PYTHONDONTWRITEBYTECODE=1 python3 tools/native_m4a1/export_parts.py
```

脚本只读原生资源及已生成的 catalog/mapping，不执行 generate.py，不修改运行模型、动画、组装规则或 ledger。每次生成校验 67 身份覆盖、68 模型的雷电转换器兼容、原贴图字节一致性及小于 1e-8 的中立位坐标往返误差。

## 每部位输出

| 身份 | 模型 | Mesh 数 | 三角形数 |
| --- | --- | ---: | ---: |
| `tacz_assembly:m4a1_barrel` | [barrel](source-pack/components/barrel/model.bbmodel) | 1 | 242 |
| `tacz_assembly:m4a1_barrel_mount_collar` | [barrel_mount_collar](source-pack/components/barrel_mount_collar/model.bbmodel) | 1 | 216 |
| `tacz_assembly:m4a1_bolt` | [bolt](source-pack/components/bolt/model.bbmodel) | 1 | 312 |
| `tacz_assembly:m4a1_buffer` | [buffer](source-pack/components/buffer/model.bbmodel) | 1 | 66 |
| `tacz_assembly:m4a1_charging_mechanism` | [charging_mechanism](source-pack/components/charging_mechanism/model.bbmodel) | 1 | 134 |
| `tacz_assembly:m4a1_front_sight` | [front_sight](source-pack/components/front_sight/model.bbmodel) | 1 | 190 |
| `tacz_assembly:m4a1_gas_block_and_tube` | [gas_block_and_tube](source-pack/components/gas_block_and_tube/model.bbmodel) | 1 | 314 |
| `tacz_assembly:m4a1_handguard_default` | [handguard_default](source-pack/components/handguard_default/model.bbmodel) | 1 | 756 |
| `tacz_assembly:m4a1_handguard_tactical` | [handguard_tactical](source-pack/components/handguard_tactical/model.bbmodel) | 1 | 2614 |
| `tacz_assembly:m4a1` | [lower_receiver](source-pack/components/lower_receiver/model.bbmodel) | 4 | 602 |
| `tacz_assembly:m4a1_magazine_standard` | [magazine_standard](source-pack/components/magazine_standard/model.bbmodel) | 1 | 324 |
| `tacz_assembly:m4a1_muzzle_default` | [muzzle_default](source-pack/components/muzzle_default/model.bbmodel) | 1 | 254 |
| `tacz_assembly:m4a1_pistol_grip` | [pistol_grip](source-pack/components/pistol_grip/model.bbmodel) | 1 | 106 |
| `tacz_assembly:m4a1_rear_sight` | [rear_sight](source-pack/components/rear_sight/model.bbmodel) | 1 | 398 |
| `tacz:bayonet_m9` | [tacz_bayonet_m9](source-pack/components/tacz_bayonet_m9/model.bbmodel) | 1 | 504 |
| `tacz:extended_mag_1` | [tacz_extended_mag_1](source-pack/components/tacz_extended_mag_1/model.bbmodel) | 1 | 356 |
| `tacz:extended_mag_2` | [tacz_extended_mag_2](source-pack/components/tacz_extended_mag_2/model.bbmodel) | 1 | 356 |
| `tacz:extended_mag_3` | [tacz_extended_mag_3](source-pack/components/tacz_extended_mag_3/model.bbmodel) | 1 | 76 |
| `tacz:grip_cobra` | [tacz_grip_cobra](source-pack/components/tacz_grip_cobra/model.bbmodel) | 1 | 226 |
| `tacz:grip_cqr` | [tacz_grip_cqr](source-pack/components/tacz_grip_cqr/model.bbmodel) | 1 | 364 |
| `tacz:grip_magpul_afg_2` | [tacz_grip_magpul_afg_2](source-pack/components/tacz_grip_magpul_afg_2/model.bbmodel) | 1 | 202 |
| `tacz:grip_osovets_black` | [tacz_grip_osovets_black](source-pack/components/tacz_grip_osovets_black/model.bbmodel) | 1 | 246 |
| `tacz:grip_rk0` | [tacz_grip_rk0](source-pack/components/tacz_grip_rk0/model.bbmodel) | 1 | 390 |
| `tacz:grip_rk1_b25u` | [tacz_grip_rk1_b25u](source-pack/components/tacz_grip_rk1_b25u/model.bbmodel) | 1 | 376 |
| `tacz:grip_rk6` | [tacz_grip_rk6](source-pack/components/tacz_grip_rk6/model.bbmodel) | 1 | 246 |
| `tacz:grip_se_5` | [tacz_grip_se_5](source-pack/components/tacz_grip_se_5/model.bbmodel) | 1 | 162 |
| `tacz:grip_td` | [tacz_grip_td](source-pack/components/tacz_grip_td/model.bbmodel) | 1 | 264 |
| `tacz:grip_vertical_military` | [tacz_grip_vertical_military](source-pack/components/tacz_grip_vertical_military/model.bbmodel) | 1 | 360 |
| `tacz:grip_vertical_ranger` | [tacz_grip_vertical_ranger](source-pack/components/tacz_grip_vertical_ranger/model.bbmodel) | 1 | 636 |
| `tacz:grip_vertical_talon` | [tacz_grip_vertical_talon](source-pack/components/tacz_grip_vertical_talon/model.bbmodel) | 1 | 124 |
| `tacz:laser_compact` | [tacz_laser_compact](source-pack/components/tacz_laser_compact/model.bbmodel) | 1 | 498 |
| `tacz:laser_lopro` | [tacz_laser_lopro](source-pack/components/tacz_laser_lopro/model.bbmodel) | 1 | 804 |
| `tacz:laser_nightstick` | [tacz_laser_nightstick](source-pack/components/tacz_laser_nightstick/model.bbmodel) | 1 | 546 |
| `tacz:laser_peq15` | [tacz_laser_peq15](source-pack/components/tacz_laser_peq15/model.bbmodel) | 1 | 376 |
| `tacz:muzzle_brake_cthulhu` | [tacz_muzzle_brake_cthulhu](source-pack/components/tacz_muzzle_brake_cthulhu/model.bbmodel) | 1 | 608 |
| `tacz:muzzle_brake_cyclone_d2` | [tacz_muzzle_brake_cyclone_d2](source-pack/components/tacz_muzzle_brake_cyclone_d2/model.bbmodel) | 1 | 192 |
| `tacz:muzzle_brake_pioneer` | [tacz_muzzle_brake_pioneer](source-pack/components/tacz_muzzle_brake_pioneer/model.bbmodel) | 1 | 382 |
| `tacz:muzzle_brake_trex` | [tacz_muzzle_brake_trex](source-pack/components/tacz_muzzle_brake_trex/model.bbmodel) | 1 | 456 |
| `tacz:muzzle_compensator_trident` | [tacz_muzzle_compensator_trident](source-pack/components/tacz_muzzle_compensator_trident/model.bbmodel) | 1 | 260 |
| `tacz:muzzle_silencer_knight_qd` | [tacz_muzzle_silencer_knight_qd](source-pack/components/tacz_muzzle_silencer_knight_qd/model.bbmodel) | 1 | 344 |
| `tacz:muzzle_silencer_phantom_s1` | [tacz_muzzle_silencer_phantom_s1](source-pack/components/tacz_muzzle_silencer_phantom_s1/model.bbmodel) | 1 | 144 |
| `tacz:muzzle_silencer_ursus` | [tacz_muzzle_silencer_ursus](source-pack/components/tacz_muzzle_silencer_ursus/model.bbmodel) | 1 | 288 |
| `tacz:scope_acog_ta31` | [tacz_scope_acog_ta31](source-pack/components/tacz_scope_acog_ta31/model.bbmodel) | 1 | 1140 |
| `tacz:scope_elcan_4x` | [tacz_scope_elcan_4x](source-pack/components/tacz_scope_elcan_4x/model.bbmodel) | 1 | 1442 |
| `tacz:scope_hamr` | [tacz_scope_hamr](source-pack/components/tacz_scope_hamr/model.bbmodel) | 1 | 2552 |
| `tacz:scope_qmk152` | [tacz_scope_qmk152](source-pack/components/tacz_scope_qmk152/model.bbmodel) | 1 | 2816 |
| `tacz:sight_552` | [tacz_sight_552](source-pack/components/tacz_sight_552/model.bbmodel) | 1 | 750 |
| `tacz:sight_acro_rifle` | [tacz_sight_acro_rifle](source-pack/components/tacz_sight_acro_rifle/model.bbmodel) | 1 | 644 |
| `tacz:sight_coyote` | [tacz_sight_coyote](source-pack/components/tacz_sight_coyote/model.bbmodel) | 1 | 576 |
| `tacz:sight_deltapoint_rifle` | [tacz_sight_deltapoint_rifle](source-pack/components/tacz_sight_deltapoint_rifle/model.bbmodel) | 1 | 408 |
| `tacz:sight_exp3` | [tacz_sight_exp3](source-pack/components/tacz_sight_exp3/model.bbmodel) | 1 | 876 |
| `tacz:sight_fastfire_rifle` | [tacz_sight_fastfire_rifle](source-pack/components/tacz_sight_fastfire_rifle/model.bbmodel) | 1 | 416 |
| `tacz:sight_okp7` | [tacz_sight_okp7](source-pack/components/tacz_sight_okp7/model.bbmodel) | 1 | 520 |
| `tacz:sight_pk06_rifle` | [tacz_sight_pk06_rifle](source-pack/components/tacz_sight_pk06_rifle/model.bbmodel) | 1 | 506 |
| `tacz:sight_srs_02` | [tacz_sight_srs_02](source-pack/components/tacz_sight_srs_02/model.bbmodel) | 1 | 944 |
| `tacz:sight_t2` | [tacz_sight_t2](source-pack/components/tacz_sight_t2/model.bbmodel) | 1 | 820 |
| `tacz:sight_uh1` | [tacz_sight_uh1](source-pack/components/tacz_sight_uh1/model.bbmodel) | 1 | 1284 |
| `tacz:stock_ak12` | [tacz_stock_ak12](source-pack/components/tacz_stock_ak12/model.bbmodel) | 1 | 1192 |
| `tacz:stock_carbon_bone_c5` | [tacz_stock_carbon_bone_c5](source-pack/components/tacz_stock_carbon_bone_c5/model.bbmodel) | 1 | 340 |
| `tacz:stock_hk_slim_line` | [tacz_stock_hk_slim_line](source-pack/components/tacz_stock_hk_slim_line/model.bbmodel) | 1 | 844 |
| `tacz:stock_m4ss` | [tacz_stock_m4ss](source-pack/components/tacz_stock_m4ss/model.bbmodel) | 1 | 564 |
| `tacz:stock_militech_b5` | [tacz_stock_militech_b5](source-pack/components/tacz_stock_militech_b5/model.bbmodel) | 1 | 442 |
| `tacz:stock_moe` | [tacz_stock_moe](source-pack/components/tacz_stock_moe/model.bbmodel) | 1 | 298 |
| `tacz:stock_ripstock` | [tacz_stock_ripstock](source-pack/components/tacz_stock_ripstock/model.bbmodel) | 1 | 248 |
| `tacz:stock_sba3` | [tacz_stock_sba3](source-pack/components/tacz_stock_sba3/model.bbmodel) | 1 | 642 |
| `tacz:stock_tactical_ar` | [tacz_stock_tactical_ar](source-pack/components/tacz_stock_tactical_ar/model.bbmodel) | 1 | 532 |
| `tacz_assembly:m4a1_upper_receiver` | [upper_receiver](source-pack/components/upper_receiver/model.bbmodel) | 1 | 620 |
| `tacz_assembly:m4a1_front_sight` 折叠 | [front_sight](source-pack/components/front_sight/model-folded.bbmodel) | 1 | 220 |
