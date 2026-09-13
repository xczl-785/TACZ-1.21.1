# 弹药制作与运行归属

2026-09-13：B1制作链、B2运行迁移均已实施。fork拥有86种弹药注册及资源；NewMod运行锁指向源码提交`a920e6ce`。集成证据见[运行接入记录](../../source/mods/tacz_adapter/docs/self-built-runtime.md)，实机接受仍待所有者确认。

## 唯一编辑入口

- `inputs/approved-ammunition.json`：86种已批准弹药参数和逐条来源。
- `inputs/descriptions.json`：中英文描述。
- `inputs/originals/`、`inputs/pixels/`、`inputs/pixel-provenance.json`：原图、派生图及处理留证。迁移不改变素材许可。
- `generated/`：保留旧布局的生成结果，用于对账，不在NewMod重复打包。
- `runtime/`：同一生成器产生的实际Jar资源，禁止手改。
- `src/main/java/com/tacz/guns/ammunition/`（相对fork根）：注册、弹药类和定义加载。

在fork根执行：

```sh
python3 tools/prepare_newmod_ammunition.py
python3 tools/generate_newmod_ammunition.py
python3 tools/generate_newmod_ammunition.py --check
```

原图重建离线，沿用既有像素配方，需Pillow；普通生成只需Python标准库。NewMod旧生成命令仅校验fork产物，旧批准表及图片保留作历史对照，禁止两处同步编辑。

## 迁移对账与边界

[migration.json](migration.json)保留B1输入路径、261份原输出哈希及86组旧新ID。[runtime-migration.json](runtime-migration.json)记录B2代码与资源移动。物品ID仍为`tarkov_content:ammo_<源ID>`；命名空间不等于工程所有权，不因Java类搬迁重编号。弹药重量、伤害、堆叠、图片与语言保留来源值，B2另将来源弹丸数和初速交给实际发射链。

`tacz_adapter`负责胸挂/口袋取弹、退弹、详细查看、战斗提交；通用库存/战斗和`tarkov_content`不反向依赖TaCZ。两种开发测试弹迁到适配器development，ID和枪内弹种键不变。正式fork只有数据与物品注册；本项目自有弹药供弹需要适配器，不宣称fork单独安装已完成自有弹药玩法。

15枪按口径匹配；同枪只装一种弹种，弹匣计数与枪膛都清空后可换种。沿用TaCZ现有装填/拉栓机制，没有实体弹匣、混装或逐发重量。其他枪包不自动接入。

24份原ammo索引仍用于显示，186份效果相关资源核对未变，见[额外内容盘点](../docs/newmod/extra-content/README.md)。原AmmoItem保留旧数据注册壳（AmmoBox已按后续裁决物理移除），正常供弹与创造/配方入口仍退役；并非物理删除全部原ammo文件。工作台已按后续裁决物理移除。存档中原生旧弹量及已删除枪不被强制清空；此类旧数据不等于新的受支持装填链。

B1历史：当时261输出逐字节一致、86像素图可重现；B2变更使运行包装位置及弹丸数字段发生变化，不能用B1哈希冒充当前全量校验。
