# 15枪清理结果与对账

2026-09-13。状态：代码与资源清理已实施；验证结果见下方。尚未切换NewMod运行Jar或完成弹药迁入，也未替代所有者实机验收。

## 已裁决清单

最终15枪：Glock17、M16A1、M4A1、M870、P90、QBZ191、SCAR-H、SCAR-L、UMP45、UZI，加AA12、M700、战术SKS、AWM、MK14 EBR。

[selection.json](../selection.json)是最终选择，替代清理前45/9选择。五例外：AA12不核对代际；SKS不核对改装配置；AWM保留原名与.338；MK14保留原名与7.62×51；M700恢复且源码ammo由`tacz:30_06`改`tacz:308`（7.62×51），不是“原口径本来一致”。没有新增配件或修改配件机制。

重量决定已记录：枪体基础重量+已装配件重量，固定部分不得重复计算；当前不计子弹、弹匣重量。实体弹匣与混合压弹时再做逐发重量。

## 实际修改及恢复依据

[plan.json](plan.json)保存39个删除枪ID、14个删除配件ID、全部99配件的保留枪使用关系、1497个删除路径/字节数/删除前SHA256，以及36个资源修改的前后哈希和新内容。[applied.json](applied.json)绑定该计划哈希。删除总字节48,243,265（约46MiB），不是Jar压缩后体积。

原文件可从fork基线提交`01e24c1e4ea4ba63428f0e564ce5af3d47f3969d`恢复；清理前完整3322文件快照在[inventory/resources.json](../inventory/resources.json)。实际资源剩1825文件。生成快照脚本已加冻结保护，防止用清理后树覆盖清理前证据。

删除覆盖枪械索引、数据、展示、配方、专属模型/动画/脚本/贴图/声音及原Taurus943出生奖励入口。删除配件为 bayonet_6h3、deagle_golden_long_barrel、laser_peq6、muzzle_brake_timeless50、muzzle_silencer_vulture、oem_stock_heavy、oem_stock_light、oem_stock_tactical、scope_1873_6x、scope_98k、scope_aug_default、sight_t1、stock_heavy_spas_12、stock_tactical_spas_12。

保留85个配件：依照现有`AllowAttachmentTagMatcher`递归展开保留枪的allow_attachments标签，至少有一把保留枪可匹配才保留；没有改变匹配机制。配件类型限制是另一个运行检查，本次保守保留所有标签可匹配项。保留46个带被删枪名的音效，因仍有资源引用，不能凭文件名删除。

程序修改限于：SelectedContentPolicy扩展39个退役枪/14个配件ID过滤，允许恢复M700；ModCreativeTabs更换已删枪图标。来源和原版权许可保留，不做发布。

## 历史裁决：工作台不动（已被后续清理覆盖）

2026-09-13后续裁决已移除工作台和相关默认图标，见[当前盘点与删除对账](../extra-content/README.md)。以下保留第一轮清枪时的处理证据。

枪匠台资源配置和TabConfig Java均已恢复到基线字节；没有修改其功能、分类或图标。已知5个已删枪图标引用留作暂缓，统一归[保留问题与暂缓清单](../../../../docs/进行中/文档治理待核对项.md)。验证对这一个已说明的资源入口列出例外，不将它隐藏为“零悬空引用”。不为保留样例图标而重新引入被删整枪。

## 验证与边界

- `tools/verify_cleanup.py`：精确15枪、M700口径、85配件使用关系、全部删除路径、修改哈希、资源显式引用与235项真实Java过滤策略检查。
- 显式引用检查除上述工作台图标外不允许新缺失；保留共用脚本和默认资源。静态检查不保证所有动态模型/动画运行效果，需后续客户端验证。
- `bash gradlew build --offline -Pmod_version=1.1.8-hotfix-r6-newmod.cleanup15`：最终构建通过；Jar复核15索引和1497个已删除文件均不残留。[制品哈希与验证记录](verification.json)。
- 当前NewMod仍锁旧`01e24c1e`制品。旧四手枪白名单、发放和测试夹具包含已删的CZ75/M9A4/93R，必须在接入新Jar时同步调整；本轮不让旧运行依赖与新白名单错配。其余14枪不宣称已经接入自有弹药。

下一批是弹药迁入、适配白名单与测试场景调整，再更新运行锁；描述与重量另按已裁决口径实现。现有24个原弹药定义与渲染依赖暂保留，后续替换后再退役，不把本次清枪当作弹药迁移完成。
