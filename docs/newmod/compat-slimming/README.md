# 首批可选兼容退出

2026-09-24；基线 `d5ca6a1278083e342a4ab499b245dc7d8dd04048`，实施分支 `codex/tacz-compat-slimming`。

本批只退出 JEI 配件查询、Cloth 图形配置、Carry On 黑名单、Controllable 输入/震动、KubeJS 专项接口。不是历史 V4 完成，也不改变枪械统一 27 项计数：B3-01 / B5-05 待验收，B6-01 待复验。

来源与逐文件裁决见主仓[调查](../../../../docs/进行中/TaCZ轻量化调查与无依赖评测.md)和[候选清单](../../../../implementation-evidence/tacz-slimming-compat-candidates-20260924.json)。原路径直接退役，没有替代 Mod ID、物品 ID 或空兼容壳；原许可证和署名保留。完整 diff 与本目录结果清单供后续追溯。

## 行为边界

- JEI 不再展示 TaCZ 配件查询；公共组装与配件计算保留。
- Cloth 配置页、缺依赖下载提示、Alt+T 入口退出。四类 ModConfigSpec、配置持久化和配置命令保留。
- Carry On 不再收到 TaCZ 黑名单。另装该 Mod 时靶子、靶车或展示架是否能搬运，以及搬运后的物品/内容保存，均待实机核验。
- Controllable 专用输入、Mixin 和震动退出，普通键鼠逻辑保留。M870 源与生成 display 中未消费的 `controllable` 元数据原样保留，不手改生成物。
- KubeJS 专项事件/API 退出；外部脚本不能继续调用旧入口。NeoForge 事件类型、取消语义、LuaJ 和枪包 Lua、配件属性计算保留。
- Iris、Sodium、Accelerated Rendering、Shoulder Surfing、Player Animator 以及平台 META-INF/services 保留。15 枪和靶子/靶车/展示架资源未裁剪。

## 集中实机清单（均未执行）

使用新建隔离客户端目录和世界，不复用日常世界。不自动晋升候选 Jar。

1. **带精简 TaCZ**：只加载正式或开发候选其中一个，确认 15 枪获取、库存/装备图标、组装预览与换件；键鼠开火、瞄准、切模式、换弹、检视、趴下、近战、缩放；M870 与使用 Lua 的枪械动作；配件属性与 NeoForge 事件取消；四类配置加载、修改与保存重进；靶子、靶车、展示架及渲染/玩家动画。
2. **附加 Mod 场景**：可用时分别加 JEI、KubeJS、Controllable、Cloth、Carry On；检查无残余插件/Mixin/类加载异常，已退出功能确实缺席。记录 KubeJS 旧脚本失效、Cloth 无配置页和 Carry On 搬运的预期变化。缺少附加 Mod 条件则保持未验。
3. **无 TaCZ 完整组合**：另一个新目录，显式包含公共七模块、自有枪和 raid，确认 Mod 列表无 TaCZ；M4A1/Radian 获取、预览和换件；背包、装备、仓库；弹药装填/混装、射击、伤害与护甲；角色体力；保存重进；raid 进入/退出、死亡/撤离与库存保留。现有 firearms 最小宿主不等于完整组合，raid 宿主需核显式 firearms sourceSet。

无 TaCZ 的公共七模块独立编译与所查类路径此前通过（见调查），本批未重跑或提升结论；客户端/服务端启动与完整玩法仍未验。无 TaCZ 与带精简 TaCZ 是两套验收，不可相互替代。

## Deferred

旧枪匠配方链、默认包共享资源裁剪、构建证据资源排除、公共供弹/伤害迁移和另建 Mod 仍归主仓调查，未处理。双方公共依赖锁、主仓 vendor 制品与运行锁不变。

新增测试曾通过生产 Gson 成功读取 M870，随后尝试将对象序列化回 JSON 时触发现有 `Vector3fSerializer.serialize` 对空 JsonArray 调用 `set(0, ...)` 的越界。该序列化器本批未修改；本批只验证加载，不用序列化往返代替加载验收。序列化输出需求另行复现/修复。
