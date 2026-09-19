# TaCZ 全量光学独立源交付

2026-09-19。范围为当前默认包全部28个光学附件定义：保留已接受的T2、ELCAN样本ID，新增另外26个独立ID；原附件、枪械高低模几何和默认装配内容保留。只处理TaCZ，不修改自有枪械工程。

## 用户测试入口

更新测试包并完整重启后，在 `/devitems` 搜索“光学制作”领取26件新增配件；原两件仍搜索“光学测试”。新件外观继承各自原件，本批验证的是完整独立作者源与导入，不宣称重新设计外观。

AWM（内部ID `ai_awp`）、P90、M16A1三把枪合起来覆盖28件。各件只继承原附件允许的枪械，共222组兼容关系；M870不新增光学候选。表中“测试枪”是一组覆盖建议，不是该件唯一兼容枪。

优先检查 HAMR、MK5HD、Vudu：它们是混合瞄具，需切换所有倍率/视点，观察红点与主镜分划、黑边和定位是否正确。随后检查 P90 的专用瞄具及各类手枪/步枪安装高度，其他普通镜按表逐件比较原件。进入退出瞄准、左右手、拆装/切枪、工作台及第三人称都需保留检查。

**新增26件及原两件在其他枪上的视觉效果仍待所有者验收。** 自动检查不是实际Blockbench界面导出，也不是Minecraft镜面验收。原两件仅保留已经得到的M4整体接受反馈。

| 来源 | 新ID（前缀均为tacz_fork_tarkov:） | 倍率 | 类型 | 测试枪 |
| --- | --- | --- | --- | --- |
| tacz:scope_acog_ta31 | `scope_acog_ta31_authored` | 2.5 | scope | ai_awp |
| tacz:scope_contender | `scope_contender_authored` | 4.25 | scope | ai_awp |
| tacz:scope_elcan_4x | `scope_elcan_sample` | 4.25 / 1.25 | scope | ai_awp |
| tacz:scope_hamr | `scope_hamr_authored` | 3.25 / 1.25 | hybrid | ai_awp |
| tacz:scope_lpvo_1_6 | `scope_lpvo_1_6_authored` | 6.25 / 1.25 | scope | ai_awp |
| tacz:scope_mk5hd | `scope_mk5hd_authored` | 5 / 25 / 1.25 | hybrid | ai_awp |
| tacz:scope_qmk152 | `scope_qmk152_authored` | 3 | scope | ai_awp |
| tacz:scope_retro_2x | `scope_retro_2x_authored` | 3.25 | scope | m16a1 |
| tacz:scope_standard_8x | `scope_standard_8x_authored` | 4.5 / 10 | scope | ai_awp |
| tacz:scope_vudu | `scope_vudu_authored` | 6.5 / 1.35 | hybrid | ai_awp |
| tacz:sight_552 | `sight_552_authored` | 2 | sight | ai_awp |
| tacz:sight_acro_pistol | `sight_acro_pistol_authored` | 2 | sight | p90 |
| tacz:sight_acro_rifle | `sight_acro_rifle_authored` | 2 | sight | ai_awp |
| tacz:sight_coyote | `sight_coyote_authored` | 1.5 | sight | ai_awp |
| tacz:sight_deltapoint_pistol | `sight_deltapoint_pistol_authored` | 1.5 | sight | p90 |
| tacz:sight_deltapoint_rifle | `sight_deltapoint_rifle_authored` | 1.5 | sight | ai_awp |
| tacz:sight_exp3 | `sight_exp3_authored` | 2 | sight | ai_awp |
| tacz:sight_fastfire_pistol | `sight_fastfire_pistol_authored` | 1.5 | sight | p90 |
| tacz:sight_fastfire_rifle | `sight_fastfire_rifle_authored` | 1.5 | sight | ai_awp |
| tacz:sight_okp7 | `sight_okp7_authored` | 1.5 | sight | ai_awp |
| tacz:sight_p90 | `sight_p90_authored` | 1.35 | sight | p90 |
| tacz:sight_pk06_pistol | `sight_pk06_pistol_authored` | 2 | sight | p90 |
| tacz:sight_pk06_rifle | `sight_pk06_rifle_authored` | 2 | sight | ai_awp |
| tacz:sight_rmr_dot | `sight_rmr_dot_authored` | 1.5 | sight | p90 |
| tacz:sight_sro_dot | `sight_sro_dot_authored` | 1.5 | sight | p90 |
| tacz:sight_srs_02 | `sight_srs_02_authored` | 1.5 | sight | ai_awp |
| tacz:sight_t2 | `sight_t2_sample` | 2.5 | sight | ai_awp |
| tacz:sight_uh1 | `sight_uh1_authored` | 2.5 | sight | ai_awp |

新增索引显示为“光学制作”候选，便于测试；来源的隐藏显示标志不复制到新候选，原索引保持不变。

## 制作和重复执行

在实验室仓库根执行，沿用NumPy/Pillow环境：

```sh
# 首次或新增清单时准备作者源并显式绑定兼容枪；已有源不覆盖
PYTHONDONTWRITEBYTECODE=1 python3 tools/native_optics/catalog.py --bind-guns
# 重建光学资源、M4及其他14枪，随后逐枪检查与光学回归
PYTHONDONTWRITEBYTECODE=1 python3 tools/native_optics/batch.py
# 只检查现有输出，不重新生成枪械
PYTHONDONTWRITEBYTECODE=1 python3 tools/native_optics/batch.py --check-only
```

28件清单在 `modules/tacz_adapter/weapon-sources/optics/catalog.json`。每件源保留完整方块骨架、功能面、贴图、原始配置与来源哈希；现有源编辑规则见[作者说明](../../../modules/tacz_adapter/weapon-sources/optics/README.md)。新目录初始化未完成时会拒绝覆盖并要求检查；编译失败不得打包交付。批次阶段计时和逐件状态见[report.json](report.json)。

其他14枪的 `production.json.authoredOptics` 是显式接入清单；只能选择其原兼容表已有的来源。挂点框架复制该来源现有安装关系；完整光学索引同时供显示与ADS读取，光学不加入普通外观覆盖表。没有在各枪复制28份完整光学作者源。

## 特殊处理与验证边界

- HAMR、MK5HD、Vudu使用 `ocular_sight` / `ocular_scope_2`、双分划和编号视点；校验按原生节点规则识别，保留全部模式。部分原始纯倍率镜的views指向2而只有一个scope_view，原生代码回退到第一个视点；本批保留这项行为，不擅自改倍率或视点。
- P90外壳位于 `default_sight`，静态预览显式提取该层级；功能镜面仍保留完整。
- Contender、QMK152、P90原低模模型或纹理引用缺失。独立源明确使用完整模型及匹配纹理作为低模回退，防止空模型/错UV；它们不宣称已减面。后续美术减面保留在本页待办。
- 全量几何/UV/骨骼/模式/纹理往返通过；原来源哈希不变。首次跑全量用例确实拒绝三件混合节点及P90层级，扩展合法规则后通过；非法节点、无效倍率和模式仍失败。
- 全部222组兼容关系验证新旧候选成对出现、不进入默认装配、不走非光学覆盖。注册物品测试覆盖安装/拆卸、每档倍率状态和原物品不被修改；原有两件替换测试继续保留。
- 十五枪默认装配的部件及槽位完全一致；14枪重放现有生成器后实例UUID随当前命名空间确定性更新，未改用户世界。所有枪械高低模JSON语义一致，部分数值格式规范化。
- 初始化/绑定重复执行后已有作者文件字节不变；本批未启动Minecraft或推送。构建与实际包/运行锁的最终证据另记package-proof.json。

## 完成与后续

本批目标为28件完整作者源、222组既有兼容关系接入、专项回归和统一测试包；自有枪械不在本任务范围。用户接下来验收新增镜面及多视点效果，有问题按单件回到作者源修改，再增量检查、批量打包。全新外观、三件回退LOD的减面及进一步性能调整后置，不混入本批已完成声明。
