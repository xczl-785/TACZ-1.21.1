# TaCZ 内部 weapon 模块

2026-09-15。来自 NewMod `94c31d5921a1a94edc79212e50bf397f53737dd0` 的四个模块已迁入这里。下级历史文档中“独立 Mod”和旧 `:weapon_*` 命令均由本文替代；设计、来源、算法与夹具资料保留。

| 目录 | 当前职责与边界 | 验证命令（实验室根目录，JDK 21） |
| --- | --- | --- |
| weapon_assembly | 纯 Java 组装规则与 JSON 读写；不依赖 Minecraft、TaCZ、foundation | `bash gradlew compilePureJava weapon_assemblyTest --offline` |
| weapon_models | 几何、定位、材质和呈现数学；不依赖 TaCZ 或 Minecraft 渲染器 | `bash gradlew weapon_modelsTest --offline` |
| weapon_runtime | 通过 foundation 公共 API 读取实体组装状态，计算枪械能力；TaCZ 唯一入口注册 `weapon_runtime:profile` | `python3 tools/verify_weapon_modules.py` |
| weapon_assembly_ui | 通用工作台视图与会话；客户端类由 adapter 的客户端入口使用，公共初始化不加载 UI | `bash gradlew weapon_assembly_uiTest --offline` |

`build-logic/weapon-modules.gradle` 将四目录编译进同一个 tacz Jar。没有四个独立 Mod、没有四个嵌套 Jar。保留原 Java 包名、组件 ID、资源命名空间以及 `META-INF/licenses/EFTForge-MIT.txt` 署名。`src/test` 与 `model-fixtures` 不进入正式包。

## 外部依赖

`public-dependency-lock.json` 固定 NewMod foundation 的来源提交、制品和 SHA-512。它以普通外部依赖参与编译和本仓库开发运行，**不嵌入 TaCZ**。LDLib 仍是独立依赖。NewMod 主线使用自己的 foundation 工程输出，TaCZ Jar 不带其副本。后续公共 API 变化必须先构建对应 foundation，再更新这个锁并进行联合验证，不能依靠任意本机 build 目录。

原子库存交换、供弹、命中、Z 改装接入已归本仓库内部 `tacz_adapter`；NewMod 不再加载独立 adapter。这里不持有第二套真实物品状态。`weapon_models/src/test/fixtures` 是 NewMod 上述提交的三枪 `manifest/markers/catalog/scene` 快照，只用于不启动游戏的数学回归，旧三枪生产内容已退出；夹具经 V3 收敛为标记/层级数据，见[状态](../status.md)。

## 开发入口与制作资料

- 完整验收使用 NewMod `启动开发客户端.command`；本轮不自动启动它。
- 旧独立 ADAR 组装演示客户端及其作者链已移除；组装界面由正式 `tacz_adapter` 宿主调用。
- 当前生产从[十五枪作者源](tacz_adapter/weapon-sources/README.md)进入。
- 下级历史 README、审计报告的旧路径是原始来源，当前可执行入口以本文为准。

来源、逐文件迁移和验收记录见 [第一项交付](../docs/assembly-experiment/weapon合并交付.md) 与 [迁移清单](../docs/assembly-experiment/weapon-module-migration.json)。
