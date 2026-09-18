# TaCZ 公共非瞄具编辑源

本批对应剩余15种原生附件定义：6枪口＋9扩容等级。扩容等级没有独立装枪模型，因此按兼容枪拆出18个实际变体；共24个编辑模型、446个方块。已有M4的52个编辑源保留，含用户确认的RK-1左右修正。

## 打开与编辑

- 枪口：`editable/components/<definitionId>/model.bbmodel`，同目录 `texture.png`。
- 弹匣/弹仓管变体：`magazine_variants/components/<gun-and-attachment>/model.bbmodel`，同目录 `texture.png`。
- PNG是纹理权威输入；bbmodel内嵌图仅用于打开预览，纹理修改后同步保存PNG。
- 保持骨骼名称、父子关系、枢轴和旋转；在原有组下编辑方块。保留原动画与安装语义，不将弹匣运动焊死。

运行 `PYTHONDONTWRITEBYTECODE=1 python3 tools/native_attachments/build.py` 更新公共资源。`--extract` 仅追加本批审计清单里尚不存在的源，不覆盖已编辑文件。运行 `python3 -m unittest discover -s tools/native_attachments -p 'test_*.py'` 验证。

## 变体覆盖

| 原生等级定义 | 实际模型 |
| --- | --- |
| light_extended_mag_1/2/3 | Glock 17、UMP45、Uzi各3件 |
| sniper_extended_mag_1/2/3 | AWM（ai_awp）、M700各3件 |
| shotgun_extended_mag_1/2/3 | M870各3件弹仓管延长外形 |

M870是管式弹仓，不是可拆卸弹匣。AA12使用另一组extended_mag等级，不在本批9条定义中。其他步枪虽复用M4已接入的等级ID，也仍需在各枪拆件时提取自己的实际弹匣几何，不能把M4的弹匣外形套过去。

枪口兼容由原生标签展开，见打包数据 `data/tacz_fork_tarkov/native_attachments/catalog.json`。每个源manifest记录原始几何、纹理、相关定义/标签/动画/脚本哈希，弹匣额外记录容量、变体骨骼和运动归属；保留原版权/许可。

## 当前完成程度

已完成独立编辑源、隔离派生模型/纹理、来源和兼容清单，并随NewMod制品打包。**尚未激活到其他组装枪**；6枪口不写全局原生display，不扩大M4兼容范围。当前游戏默认外观不因这些待接入资源改变。

后续每枪接入时显式引用standalone.json的模型/纹理，并把magazine_variants.json对应本枪的几何接回原动画骨骼；同时生成该枪的工作台/图标与物理装配目录。完成实际持枪与换弹检查后，才记为该枪已接入。原生没有LOD的6枪口保留全模型回退，没有伪造简化模型。
