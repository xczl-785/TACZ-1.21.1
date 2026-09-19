# Weapon Assembly

> 当前是单一 `tacz` 包的内部模块；本文的“独立 Mod”、旧 `:weapon_*` 命令及未连接实枪状态属于迁入前历史。可执行命令与加载边界以[内部模块入口](../README.md)为准；机制说明仍须与当前源码核对。


独立 Mod：`weapon_assembly`。用途：让宿主用同一套纯 Java 规则预览、验证和计算枪械装配；本批由所有者明确选定算法，复杂模型夹具已提供，伴随 [界面模块](../weapon_assembly_ui/README.md) 已实现隔离组装入口。任务归 RouteLedger Todo `c493a990-e354-4983-9c0a-9ff8cd6b9eba`；本页是技术接手入口，不代替任务状态。

## 本批能力

- 不可变配件目录和装配树，区分型号 ID 与每个配件的 UUID。
- 按完整槽路径安装、替换、拆卸；失败返回原树及结构化原因。
- 递归允许安装项、双向物品冲突、占用槽冲突、重复实例与规模限制校验。
- 结构合法与部件齐全分开：缺必需件可保留、继续编辑，`complete()` 返回 false 和具体缺失路径。
- 按实际装入的每个实例计算重量、人机、后坐力、精度、瞄准距离、初速修正、热量/冷却/损耗系数及 EED/臂力参考值。
- 严格 JSON 目录读取、带 schema 的实例快照导入导出。重复键、未知字段、非法关系及超限输入拒绝。

没有注册枪或配件 Item，没有界面、网络包、命令、射击、弹药或世界存储。Glock 四件数据、人工边界数据及[复杂模型夹具](model-fixtures/README.md)仅用于开发测试，不进入正式 Jar。

## 运行与产物

在 `source/` 执行：

```sh
./gradlew :weapon_assembly:build
```

`test` 用普通 JVM 驱动算法，不启动 Minecraft。`compilePureJava` 以空第三方 classpath 编译全部 `api`，证明算法仅依赖 JDK 21。JSON 层使用 Minecraft 1.21.1 已提供的 Gson 2.10.1；普通 JVM 消费者需要自行提供它。

- 分发：`build/libs/weapon_assembly-0.0.0-dev.jar`。
- 源码：同目录 `*-sources.jar`。
- 测试报告：`build/reports/tests/test/index.html`。
- 根工程 `build/check/verifyDistribution/verifyModuleBoundaries` 已接入此模块。
- 日常开发客户端仍保留原加载组合；后续接入界面时再把此 Mod 纳入相应开发宿主。

## 最短调用路径

```java
var catalog = AssemblyJson.readCatalog(catalogJson);
var engine = new AssemblyEngine(catalog);
var gun = AssemblyNode.leaf(gunInstanceId, gunDefinitionId);
var source = AssemblyNode.leaf(partInstanceId, partDefinitionId);
var plan = engine.install(gun, List.of("mod_reciever"), source);
if (plan.success()) {
    var preview = plan.after();
    var completeness = engine.validate(preview);
    // 宿主检查真实库存/租约后，才能把计算方案提交到真实物品。
}
```

公开接口和 JSON 合同见[算法合同](docs/算法合同.md)；测试场景与边界见[验证记录](docs/验证记录.md)。后续如何独立测试见[测试策略](docs/测试策略.md)。界面实施入口见[界面接入方案](docs/界面接入方案.md)，原型行为见[gunsmith 原型梳理](docs/gunsmith原型梳理.md)。

## 职责与依赖

| 所有者 | 职责 |
| --- | --- |
| weapon_assembly/api | Java 装配快照、兼容/完整性、纯拆装计划与武器属性；不依赖 NewMod 其他模块 |
| weapon_assembly/io | JSON 与算法对象转换；不自动读写磁盘、不挂数据包监听器 |
| foundation（已有） | 真实 ItemStack 的 AssemblyState、组件保存、通用安装树；当前未修改 |
| tactical（已有） | 实际扣除与返还、空间规划、租约及服务端交换；当前未修改 |
| 后续适配层 | 把真实物品投影到算法，复核 before 与实时状态，再通过现有交换入口提交 |
| 后续界面/模型 | 展示规则返回的目录、错误、属性和装配结果，不拥有权威库存 |

foundation 已有 `AssemblyPlanner/AssemblyState`，但依赖 Minecraft，且承担真实组件与通用装备行为。新模块拥有武器专用的纯算法，JSON 快照是可重建的计算输入/输出，不能另存为第二份权威物品资产。未来 bridge 必须保持 UUID，保留真实组件，并统一两者规则的适用范围，不能一边按本算法允许、一边绕过宿主规则提交。

本次选择独立 Mod 是所有者裁决。将来 TaCZ fork 可以通过适配器消费它；目前没有把 TaCZ 代码搬入此模块，也没有建立反向依赖。是否扩展 TaCZ 渲染/射击仍留在接入批次分析。

新增 `FiringReadiness` 只检查合法结构与声明的射击关键路径；真实物品投影、TaCZ 开火拦截和枪管验证入口见[独立武器能力](../weapon_runtime/README.md)。

## 完整目标与后续归属

完整目标是：少量实体配件能搜刮、拆装、保存，并通过模型与实际射击呈现效果。算法是这条流程的第一段，不是临时网页或游戏内假库存。

| 结果段 | Todo / 验收 | Deferred / 待裁决 | Constraint |
| --- | --- | --- | --- |
| 本批：独立算法 | 目录、树、拆装、规则、属性、快照；自动测试和 Jar 检查 | 自动最优配装、全量内容、弹药动态状态留后续；本批无额外待裁决项 | 纯函数、实例守恒、失败不改树，不启动界面验收 |
| 后续：交互与模型 | 所有者提供参考后，选一把枪把展示及操作接入算法；逐项实机验收 | 拆装可发生的场景、操作时间、模型挂点及适配位置待选；未自动启动 | 界面只提交意图，模型参数不污染装配兼容规则 |
| 后续：真实玩法接入 | 接库存、持有、保存、射击效果；验证不丢不复制、真实效果一致 | TaCZ fork 的接入方式、断线/多人一致性以及是否引入实体弹匣另行确定 | 不建立第二份资产所有者，不把结构完整直接等同可射击 |

上述后续由本页承接并在对应批次选入时登记 RouteLedger；不恢复项目其他保留问题。现有待办的所有者实机门槛也不因本次算法通过而关闭。
