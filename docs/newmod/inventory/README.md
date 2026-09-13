> 本目录为清理前快照。最终裁决和已执行结果见[清理记录](../cleanup/README.md)，不再以本页待定项判断当前保留范围。

# 改造盘点与自研迁移依据

状态：盘点提案，待决定相近型号处理标准。日期：2026-09-13。所有者强调记录优先，未来可能自研枪械mod；本目录保存可追溯数据与迁移边界，不把TaCZ实现视作未来引擎必须照搬的设计。

## 先读结果

54枪：10把名称/口径明确对应、22把相近型号待判、13把当前资料未找到同款、9把沿用既有排除。详细看[逐枪盘点](逐枪盘点.md)，表格版[guns.csv](guns.csv)，完整路径/来源/引用见[guns.json](guns.json)。所有建议尚未修改实际选择。

- **口径第一批**：既有6枪为 kar98（7.92×57）、lonetrail / m700（.30-06）、springfield1873（.45-70）、taurus500（.500）、taurus943（.22WMR）。继承已确认清单，不假称重新核对了最新游戏全量数据。RPG7、M320、M134是另外3项既有排除，不能都解释为口径不存在。
- **同款第二批**：13个未找到项是 b93r、cz75、fn_evolys、hk_g3、hk_mk23、m107、m249、m95、p320、qbz_95、spas_12、timeless50、type_81。这是本地177条资料快照的检索结论，不是“最新塔科夫绝对没有”的断言。
- **待判22项**：相似不等于相同；例如416D/A5、M9A4/A3、M16A4/A2、M1014/M3、RPK/RPK16不能自动合并。建议按具体型号和口径筛选，允许有明确证据的外观/附件变体；不要仅因枪族相同保留。未决定前不删这些项。
- **不要误删**：资料中已有AA12、M16A1、QBZ191；AA12仍需确定代际。M700虽同名，当前TaCZ口径不符，不自动恢复。

## 文件级留证及删除边界

[resources.json](resources.json)逐文件保存默认包3322文件路径、大小、SHA256、可见引用与引用枪械。它能在删除后对账，但不能替代Git中的原始文件恢复。

[guns.json](guns.json)逐枪记录索引、名称、口径、原选择、建议和理由、EFT ID与来源、模型/材质/动画/脚本/声音配置、显式引用路径、单枪引用及共享引用。`single_gun_references`仅指当前显式图中被一把枪引用，**不是已证明独占、可直接删除**。`unresolved_ids`保留未解析项。

静态图不能证明Lua字节码、运行时拼接路径、附件、外部枪包、默认动画回退的完整闭包。下次清理应先删已裁决枪的索引/数据/展示/独占配方，再逐组核对美术资源及动态依赖；最终每个物理删除路径进入实际删除清单并保留删除前哈希，不能用文件名批量猜删，也不能把只关入口记作彻底删除完成。

## 弹药迁移盘点

[ammunition.json](ammunition.json)记录86种/12口径及逐ID来源。此次不擅自按10把明确对应枪缩减全部弹种；最终枪单与相近型号裁决会决定保留口径。

| 当前内容/位置（相对NewMod） | 计划归属与迁移记录要求 |
| --- | --- |
| `source/mods/tarkov_content/src/main/java/dev/tarkovcontent/TarkovAmmoItem.java` | 当前Item和Definition含口径/肉伤/穿透/损甲；fork承载弹药内容时替换归属，通用战斗参数不硬绑TaCZ类 |
| `source/mods/tarkov_content/scripts/ammunition/prepare.py`、`scripts/generate_ammunition.py`及后续生成链 | 迁入生成输入与流程，逐项记录输入ID→注册ID→资源路径；不得只复制生成结果后留下两个编辑入口 |
| `docs/参考资料/ammunition-audit/adoption/approved-ammunition.json` | 86种批准来源；保存原始来源与哈希，未来自研也从同一事实数据转换 |
| `source/mods/tarkov_content/assets-pixel/ammunition/`及生成模型/语言/身份/容器准入 | 随弹药迁移；区分原图、派生图与授权，不把像素转换视为自有版权 |
| `source/mods/tacz_adapter/src/main/java/dev/tacticaltacz/AmmoBridge.java` | 现在直接依赖TarkovAmmoItem并硬编码9×19；需要以弹药身份/口径的稳定接口接入，不能仅换包名 |
| 同目录 `PistolAdoption.java` 与 mixin 装填/脚本/HUD链 | 当前4手枪白名单；删枪后同步身份资格、供弹、退弹、开发发放和回归用例 |
| 默认包24个原ammo索引及渲染资源 | 先拆清弹药物品与弹壳/弹道显示的依赖；设计替代后再删原系列，记录旧ammo_id→新定义映射 |

建议长期边界：fork负责枪械运行和内容，适配层连接通用库存/战斗；通用库存/战斗不依赖TaCZ。自研时替换枪械运行端和适配实现，复用经过来源审计的内容数据与行为验收场景。当前仅为架构建议，未新增接口或改依赖。

每批迁移必须留下旧路径/ID、新路径/ID、数据转换规则、旧存档处理、验证依据。命名空间、旧ID兼容方式仍待实施设计；不允许无记录删注册项。

## 描述与重量盘点

伤害并不全在文案中：`src/main/java/com/tacz/guns/client/tooltip/ClientGunTooltip.java`动态计算damage与armorIgnore，单删语言字符串会留下错误展示。逐枪index中的tooltip键与display.damage_style也需一起核对，保留作者/许可文本。

EFT `base_properties.Weight`是基础物品值，不保证含全部必需部件；例如M4A1当前来源值0.38不能直接解释为完整枪重。下一步需选定空枪配置，累计必需组件并明确弹药/附件是否另计，记录组成与来源后才接入负重。

## 可重建与已验证范围

在NewMod工作区运行：`python3 TACZ-1.21.1/tools/inventory_newmod.py`。脚本只生成此目录的盘点产物，不改枪包。依赖配套NewMod资料，独立克隆fork时需提供这些来源；缺少输入会失败，不默默使用网络替换。

已检查54枪全集与旧选择一致、EFT候选ID有效、3322文件哈希可复核、86弹药ID无重复、生成可重复。没有做运行构建、物理删除或新枪实机验收。
