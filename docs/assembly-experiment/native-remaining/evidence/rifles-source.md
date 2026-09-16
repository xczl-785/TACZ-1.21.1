# 五把步枪与霰弹枪作者源证据

日期：2026-09-17。范围仅为 `QBZ-191`、`SCAR-H`、`MK14`、`SKS Tactical`、`AA-12` 的 `weapon-sources/native_<gun>/`，以及作者脚本 `tools/native_guns/audit_remaining_rifles.py`。脚本只准备配置、可编辑 BBModel/PNG、枪专属扩容弹匣和来源审计；未调用 `produce.build`，未写运行资源、global index、语言或共享 producer，未启动游戏。

| 枪 | 默认实体数 | 原高模 cube | 编辑源 | 供弹与原生容量 |
| --- | ---: | ---: | --- | --- |
| QBZ-191 | 11 | 903 | 11 BBModel + PNG | 30 / 40 / 50 / 75，三个 `mag_extended` 原骨变体 |
| SCAR-H | 12 | 921 | 12 BBModel + PNG | 20 / 25 / 30 / 40，三个原骨变体 |
| MK14 | 13 | 801 | 13 BBModel + PNG | 10 / 15 / 18 / 20，三个原骨变体 |
| SKS Tactical | 10 | 601 | 10 BBModel + PNG | 10 / 15 / 20 / 25，三个原骨变体 |
| AA-12 | 10 | 527 | 10 BBModel + PNG | 8 / 16 / 24 / 32，三个原骨变体 |

口径均直接取 `GunAdoption`：QBZ-191 为 `58x42`，SCAR-H 与 MK14 为 `762x51`，SKS Tactical 为 `762x39`，AA-12 为 `12/70`。扩容弹匣的资格只来自每枪原生允许附件标签与确实存在的 `mag_extended_1/2/3` 骨，不从口径推断。

QBZ-191、SCAR-H 的原生竖起/折叠机瞄骨由 `sourceBoneVariants` 保留，安装既有光学候选时才切换到原来的折叠分支。五枪的 `nativeProfile.sightAlternatives` 已将无光学机瞄写成同时满足前、后准星的 AND 条件：QBZ/SCAR-H/SKS/AA-12 分别要求同一总成下的 `front_sight` 与 `rear_sight`；MK14 要求枪管和后照门。各枪都保留原始骨、父级、pivot、UV、动画引用和动态手/子弹 presentation-only 骨；生产配置的 `requiredPaths` 只含射击、枪机、拉机柄或导气等实际必要件，不强制枪托、枪口、瞄具安装座或弹匣。

所有带原生 stock 类型的枪都启用 `authoredStockAssets`，并继续使用共享的已编辑枪托来源。所有非光学候选由既有共享 editable manifest 覆盖。AA-12 的 `muzzle_duckbill_sg` 原标签曾指向缺失 index；主线程已补最小原生 index 和 16-cube supplemental 编辑源。本枪配置明确引用 supplemental manifest、`supplemental.json` 与 `supplementalAttachmentLibraries`，没有静默删掉该候选。没有新增任何光学编辑源或 PNG。

验证已执行：`python3 -m py_compile tools/native_guns/audit_remaining_rifles.py`；五枪 `source-audit.json` 的覆盖分别为 903、921、801、601、527，每项等于唯一归属数且没有遗漏/重复；全部默认与弹匣 BBModel 解码并逐项读取 PNG 成功；按原生 tag 展开后五枪所有非光学候选都有共享或 supplemental editable 来源。另核对五个无光学 `sightAlternatives` 都包含两条实际装配路径。仍须在统一重生成后做“拆前/拆后前后准星”的运行配置测试：少任一前/后准星即不得瞄准，同时保留光学替代。此证据不构成生产构建、运行时、FPS 或实机验收。

最终 rules 骨名审计：逐枪由原 display 指向的原始高模读取骨名，再与 `alwaysVisibleBones`、`boneRequirements`、`boneAllRequirements` 求包含关系。五枪均无悬空引用；MK14 删除不存在的 `bolt` 门禁，AA-12 删除不存在的 `bullet_in_barrel` 门禁。二者对应实体叶仍由安装状态自动控制，并未删除实体、动画或模型。
