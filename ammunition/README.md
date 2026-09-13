# 弹药制作链（B1）

2026-09-13，状态：制作链迁入完成；运行注册仍在NewMod，等待B2一次性切换。当前目录不会自动打包进TaCZ Jar，也没有新增第二份物品注册。

## 唯一编辑入口

- `inputs/approved-ammunition.json`：86种已批准弹药的完整参数与逐条原来源定位。
- `inputs/descriptions.json`：中英文描述，来源快照路径/哈希见[migration.json](migration.json)。
- `inputs/originals/`、`inputs/pixels/`：原图与派生像素图；`pixel-provenance.json`保留原URL、来源哈希和处理配方。移入不改变素材许可，不将外部素材宣称为自有版权。
- `generated/`：261个生成结果，禁止手改。当前输出布局保留旧模块路径，B2注册切换时再改变包装位置，不修改物品ID。

命令（在fork根目录）：

```sh
python3 tools/prepare_newmod_ammunition.py
python3 tools/generate_newmod_ammunition.py
python3 tools/generate_newmod_ammunition.py --check
```

图片重建完全离线，使用既有透明度预乘BOX、64px主体及2px边框规则；发现与批准像素哈希不同会停止，不覆盖。普通生成只需Python标准库；重建原图需要Pillow。

NewMod旧`generate_ammunition.py`现在只是调用此生成器、将结果投影到当前运行模块的兼容入口；旧`prepare.py`也仅转发。NewMod旧批准表、图片和来源测试保留为历史对照，不再作为制作输入，不在两处同步编辑。两仓库需维持现有相邻位置。

## 迁移对账

[migration.json](migration.json)记录每个输入旧新路径、源哈希、所有261输出的原字节哈希以及86组旧新ID。**所有ID仍为`tarkov_content:ammo_<源ID>`**，本次没有改重量、伤害、堆叠、图片、占格或语言内容。

验证：新生成结果全部与迁移前输出逐字节一致；86原图和像素可离线重现；NewMod三项弹药来源测试通过。未运行客户端，也未宣称B2完成。

## B2运行切换的边界

目标：fork拥有弹药注册及定义；tacz_adapter拥有库存、查看与战斗的连接；tarkov_content不再拥有弹药类/注册/生成资源。通用库存/战斗不依赖TaCZ。

旧ID保留以保护存档，不因为Java包名或工程归属改变就重编号。旧测试弹与已装枪的弹种键也需逐项映射，不删除历史物品后假称迁移成功。

必须同批处理：移走旧注册、调整ContentAmmoMixin目标、解除AmmoBridge的四手枪/9×19硬编码、按15枪口径接入供弹/退弹、将详细查看放入适配层、调整旧枪白名单/发放/测试，再构建并切换锁定Jar。通用规则不放入fork；B2完成时旧兼容导出到tarkov_content的路径退役。

为什么B1仍保留旧运行投影：新注册与旧供弹不能分开切到日常客户端。投影只有一个生成源，无手工双写；退出条件就是B2注册和适配测试共同通过。所有者已授权继续，无需重新选择这一步。

原TaCZ弹药24份索引中的渲染用途仍需先拆清；不因新增这86种源数据而删除必要弹壳/弹道资源。枪匠台、子弹组装台仍全部暂缓。
