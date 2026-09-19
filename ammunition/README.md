# 弹药制作与运行归属

实验工程当前结果（2026-09-15）：见[弹药链路与清理](../docs/assembly-experiment/弹药链路与清理.md)。86 种弹药完整保留；原生子弹物品链已删除，退弹增加明确标注的临时固定口径返还接口。本 Mod 以现有配套环境为前提，不要求独立生存装填。以下 B1/B2 是历史联合运行记录。

2026-09-13：B1制作链、B2运行迁移均已实施。fork拥有86种弹药注册及资源；NewMod运行锁指向源码提交`a920e6ce`。集成证据见[运行接入记录](../modules/tacz_adapter/docs/self-built-runtime.md)，实机接受仍待所有者确认。

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

`tacz_adapter`负责胸挂/口袋取弹、退弹、详细查看、战斗提交；通用库存/战斗和`tarkov_content`不反向依赖TaCZ。两种开发测试弹迁到适配器development，ID和枪内弹种键不变。装填继续由配套适配器连接；当前临时固定映射只承接默认退弹，不建设独立供弹。

15枪按口径匹配；同枪只装一种弹种，弹匣计数与枪膛都清空后可换种。沿用TaCZ现有装填/拉栓机制，没有实体弹匣、混装或逐发重量。其他枪包不自动接入。

当前 24 份原口径索引只保留名称/显示与射击效果用途，item model/texture/slot/transform 及原生物品 stack/sort 元数据已清除。原 `AmmoItem` 注册壳和 builder 已删除；物品退弹默认改由临时接口返回固定塔科夫弹种。原生子弹旧物品栈不在本轮做存档迁移；既有 adapter 接管时仍按其精确退弹路径执行。枪模、动画和改装接口保持，见上述当前清理入口。

B1历史：当时261输出逐字节一致、86像素图可重现；B2变更使运行包装位置及弹丸数字段发生变化，不能用B1哈希冒充当前全量校验。
