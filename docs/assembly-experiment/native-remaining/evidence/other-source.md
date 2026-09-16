# 其余五枪作者源证据

日期：2026-09-17。范围仅为 `AI AWP`、`M700`、`M870`、`P90`、`UZI` 的 `weapon-sources/native_<gun>/`。每把枪保留原始骨架、UV、动画及持枪/子弹姿态骨；每个原 cube 只归于一个实体部件、容量变体或 presentation-only。未调用 `produce.build`，未写运行 resources、共享 producer、global index 或语言文件；未启动游戏。

| 枪 | 默认实体数 | 高模 cube | 独立编辑源 | 供弹与容量源 |
| --- | ---: | ---: | ---: | --- |
| AI AWP | 7 | 521 | 7 BBModel + PNG | 3 个 `sniper_extended_mag` 变体 |
| M700 | 6 | 353 | 6 BBModel + PNG | 3 个 `sniper_extended_mag` 变体 |
| M870 | 7 | 273 | 7 BBModel + PNG | 默认内部管 + 3 个 `tube_extension` 源 |
| P90 | 7 | 300 | 7 BBModel + PNG | 原枪无 `extended_mag`，无伪造变体 |
| UZI | 10 | 333 | 10 BBModel + PNG | 3 个 `light_extended_mag` 变体 |

M870 的默认储弹管为 `magazine/group5/group7/group8` 共 17 cubes，三档 `mag_extended_1/2/3` 各 14 cubes，只延长默认管末端。其配置为 `feed=internal_tube`、`feedPath=['tube']`、`capacityPaths=[['tube','tube_extension']]`，没有 `magazinePath`；保留原生脚本的逐发、可中断装填。延长节的库存类别仅是可装配容量组件，显示和语义均标记为储弹管延长节，不能作为可拆弹匣。

AI AWP/M700 的 scope 路径仅引用原生只读候选；P90 的内置光学也保持只读。此批没有新建或编辑任何光学模型/PNG。所有口径取自 `GunAdoption`：AWP `338lapua`、M700 `762x51`、M870 `12/70`、P90 `57x28`、UZI `9x19`；弹匣兼容性只来自原枪允许附件标签与实际变体骨，而不是口径推断。

验证：`python3 -m py_compile tools/native_guns/audit_remaining_other.py`；五枪 `source-audit.json` 覆盖计数分别为 521/353/273/300/333，均无未归属或重复 cube。独立 BBModel JSON 与 PNG 解析检查通过。此为静态作者源证据，不构成生产构建、运行时或实机验收。

后续公共生产校验发现 AWP 与 M870 的 `bullet_in_barrel` 同时依赖独立的枪管和枪机分支；旧 `boneRequirements` 是 OR 语义，不能表达此条件。作者配置改用 `boneAllRequirements` 记录该 AND 门禁，生产配置由公共生产层同步；模型、动画和作者源均未改动。

M700 曾错误地按连续 cube 索引区段命名三件，几何总量正确但物理语义错误。修正后以 Blockbench 临时渲染复核：`receiver` 为长护木主体（84 cubes），`barrel` 为长枪管加分离上方导轨段（80 cubes），`stock` 为后部枪托/握把壳（53 cubes）。六个默认物理件及三个弹匣变体重新实际加载、编译和回写对照，几何、pivot、rotation、UV 与纹理引用一致；原项目恢复至指定 UUID。

最终原生模型构造复核后，`alwaysVisibleBones` 只保留真实存在的原骨：P90 不含任何 `mag_standard` 或 `mag_extended_*` 骨，故列表为空；M870 不含 `mag_standard`，仅保留三档管延长骨。作者脚本已改为从实际骨架过滤该列表，避免再登记无效门禁。
