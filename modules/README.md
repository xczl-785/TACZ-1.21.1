# TaCZ 内部 weapon 模块

2026-09-15。来自 NewMod `94c31d5921a1a94edc79212e50bf397f53737dd0` 的四个模块已迁入这里。下级历史文档中“独立 Mod”和旧 `:weapon_*` 命令均由本文替代；设计、来源、算法与夹具资料保留。2026-09-21：`weapon_assembly_ui` 的源码、资源与测试已归位到 NewMod 的 firearms Mod。2026-09-22：`weapon_assembly` 与 `weapon_models` 的规则、呈现数学及其测试同样归位到 firearms，本仓库只保留三者的设计、算法与夹具资料。

| 目录 | 当前职责与边界 | 验证命令（实验室根目录，JDK 21） |
| --- | --- | --- |
| weapon_assembly | 只保留规则设计、算法合同与模型夹具资料；实现与测试已归位 firearms | `python3 tools/verify_weapon_modules.py` |
| weapon_models | 只保留呈现设计与验证资料；实现与测试已归位 firearms | `python3 tools/verify_weapon_modules.py` |
| weapon_runtime | 通过 foundation 公共 API 读取实体组装状态，计算枪械能力；TaCZ 唯一入口注册 `weapon_runtime:profile` | `python3 tools/verify_weapon_modules.py` |

工作台界面（屏幕、相机、槽位/候选布局、网格视口、材质质量、外观缓存、预览物品构造）现由 firearms 发布，见 [weapon_assembly_ui/README.md](weapon_assembly_ui/README.md)；TaCZ 只在 `tacz_adapter` 保留宿主与原生后端。

组装规则、统计与呈现数学同样由 firearms 发布（`dev.firearms.assembly`、`dev.firearms.presentation`）。它们的单元测试随实现一起归位，不再由本仓库的测试源集承载。

`build-logic/weapon-modules.gradle` 只把 `weapon_runtime` 编译进 tacz Jar；`weapon_assembly_ui`、`weapon_assembly`、`weapon_models` 已不参与编译。没有独立 Mod、没有嵌套 Jar。保留原 Java 包名、组件 ID 与资源命名空间。`model-fixtures` 与文档不进入正式包。EFTForge 归属说明随组装规则归位到 firearms 的 `META-INF/licenses/EFTForge-MIT.txt`，本仓库 Jar 不再携带该署名。

## 外部依赖

`public-dependency-lock.json` 逐条固定 NewMod foundation 与 firearms 的来源提交、制品和 SHA-512；`modules/tacz_adapter/public-dependency-lock.json` 以同一结构固定其余公共模块。它们以普通外部依赖参与编译和本仓库开发运行，**不嵌入 TaCZ**。LDLib 仍是独立依赖。NewMod 主线使用自己的工程输出，TaCZ Jar 不带其副本。后续公共 API 变化必须先用 `tools/promote_newmod_artifacts.py` 从干净工作区重建对应模块，再更新锁并进行联合验证，不能依靠任意本机 build 目录。

原子库存交换、供弹、命中、Z 改装接入已归本仓库内部 `tacz_adapter`；NewMod 不再加载独立 adapter。这里不持有第二套真实物品状态。三枪 `manifest/markers/catalog/scene` 快照随 `WeaponPresentationTest` 归位到 firearms 的测试资源，只用于不启动游戏的数学回归，旧三枪生产内容已退出；夹具经 V3 收敛为标记/层级数据，见[状态](../status.md)。

## 开发入口与制作资料

- 完整验收使用 NewMod `启动开发客户端.command`；本轮不自动启动它。
- 旧独立 ADAR 组装演示客户端及其作者链已移除；组装界面由正式 `tacz_adapter` 宿主调用。
- 当前生产从[十五枪作者源](tacz_adapter/weapon-sources/README.md)进入。
- 下级历史 README、审计报告的旧路径是原始来源，当前可执行入口以本文为准。

来源、逐文件迁移和验收记录见 [第一项交付](../docs/assembly-experiment/weapon合并交付.md) 与 [迁移清单](../docs/assembly-experiment/weapon-module-migration.json)。
