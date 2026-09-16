# 公共供弹与可见性代码复核

日期：2026-09-17。只读审阅 `NativeAssemblyFeed`、`AssembledWeapon`、`AssemblyGunExchange`、`AbstractGunItem`、`NativeAssemblyVisualRules` 及对应单元/Minecraft 测试；未运行 Gradle、未修改代码、模型或运行资源。

结论：未发现会阻塞本轮的公共语义错误。

- `NativeAssemblyFeed` 将旧 `magazinePath` 映射为 detachable 的 container path；旧可拆弹匣在 `AssembledWeapon.hasMagazine` 保留原有 true/false 语义。`AbstractGunItem.canReload` 改为检查通用 feed container，故 M870 有储弹管才可装填，缺管会在访问玩家或弹药前拒绝。
- M870 使用 `INTERNAL_TUBE`，没有 detachable `hasMagazine` 语义。`feedPath=[tube]` 与 `capacityPaths=[tube,tube_extension]` 都会进入交换影响判断；拆管或换延长节时仅退还 `getCurrentAmmoCount` 对应弹药并清该计数，`hasBulletInBarrel` 不改。原生逐发装填脚本/回调没有被此代码覆盖。
- `AssemblyGunExchange` 只在 container 或 capacity 路径及其祖先的变更时退弹；枪托、枪管和其他非供弹路径不触发。保留旧件退款与弹药退款各一份，未见复制同一计数的第二条路径。
- `boneRequirements` 是任一已装定义的 OR；新 `boneAllRequirements` 是全部已装定义的 AND；同一骨同时配置两者时，结果是 OR 条件与 AND 条件同时成立。加载阶段会拒绝未知、重复、空依赖、缺失骨或与 always-visible 冲突的依赖。
- `NativeRemainingStateTest` 已覆盖十枪真实 ItemStack 预设、M870 非可拆语义、缺 tube 拒绝 reload、container/capacity 交换退弹而不改输入或膛弹、前后机瞄/光学替代、以及高低模型可见性。`NativeAssemblyFeedTest` 覆盖旧可拆路径、管式容量路径和非法/空 feed 路径；`NativeAssemblyVisualRulesTest` 覆盖单独 OR、单独 AND 和坏引用。

建议补两条低成本回归，但不构成当前代码阻塞：

1. 在 `NativeAssemblyVisualRulesTest` 为同一 bone 同时声明 `boneRequirements` 与 `boneAllRequirements`，断言 OR 命中但 AND 缺件时不可见、两者满足时可见，固定组合语义。
2. 在真实组装枪测试中补 detachable 枪“容器存在可进入 reload 检查、移除容器立即拒绝”的对照，避免未来 `hasFeedContainer` 重构只被 M870 缺管路径覆盖。

本复核不证明客户端原生动画、网络、世界状态或实机换弹手感。
