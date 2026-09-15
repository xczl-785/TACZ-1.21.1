# 枪械装配测试低模包

双击 `preview.html` 可离线查看 6 个装配场景，旋转并调节拆解距离。共 13 个原创长方体低模，不需要下载图片、联网或启动 Minecraft。`models/*.bbmodel` 是 Blockbench free 项目；`models/*.obj` 是通用静态几何备份。`overview.png` 是六场景总览；由 `render_overview.py`（需 Pillow）从同一几何生成。

## 这批覆盖什么

| 场景 | 用途 | 预期 |
| --- | --- | --- |
| zev_rmr | 枪体 → ZEV 套筒 → RMR；枪管、弹匣、前后照门为其他分支 | 合法、必需件齐全 |
| mos_acro | 枪体 → MOS 套筒 → AAM 底座 → ACRO | 合法、必需件齐全 |
| dual_rmr | ZEV 直接 RMR 与 Tiger Shark → RMR 底座 → 第二个 RMR 并存 | 合法、必需件齐全；同型号两个不同 UUID |
| mount_conflict | Tiger Shark 与 Aimtech 在不同枪体槽上同时出现 | PART_CONFLICT；不得应用到实际物品 |
| missing_barrel | MOS/ACRO 整个子组件仍在，但枪管缺失 | 结构合法、必需件不齐全 |
| wrong_optic | 将 RMR 放到只接受 ACRO 的 AAM 底座 | INCOMPATIBLE；不得应用到实际物品 |

双 RMR 是缓存规则允许的压力场景，不承诺符合真实机械空间或具有射击价值。模型位置是自行制定的测试坐标，不是 EFTForge 提供的三维数据；几何重叠不替代规则冲突。无效场景故意保留，用来验证红色提示、禁止提交和恢复编辑。

## 接入合同

- `catalog.json`：现有 `AssemblyJson.readCatalog` 的 schema 1 目录。
- `scenarios.json`：场景 ID、快照文件、预期合法性/完整性/错误码、模型放置位置与完整槽路径。
- `scenes/*.json`：现有快照格式，父先子。合法快照可 `readSnapshot`；无效快照会被该入口拒绝，测试可从 rows 建树后调用 `validate`。
- `manifest.json`：型号 ID → 模型文件、子槽挂点与原始立方体列表。
- `source.json`：本地源缓存 SHA-256、13 个原始型号 ID。保留 `mod_reciever` 原拼写。

统一局部坐标：+Y 上、+Z 枪口、+X 右；16 单位 = Minecraft 一格，仅为测试比例。每件挂接原点为 `[0,0,0]`，父件 slot 挂点给出子件局部原点；本批旋转全为零，世界变换为沿祖先链累加平移。Blockbench locator 名称与槽名一致。只为本批有模型的可装配边提供挂点；空候选槽仍在算法目录中保留，后续扩展必须同时补数据和挂点。

推荐界面用 `definitionId` 找模型、用 `instanceId` 跟踪每次出现，用完整槽路径发出操作意图。拆下 MOS 套筒时，应一次带走 AAM 和 ACRO 子树；用两个 RMR 验证不能按型号 ID 覆盖渲染对象。

## 格式边界

Blockbench free 项目与 OBJ 是创作/验证输入，不是已注册 Minecraft Item，也不是 TaCZ 枪包。Blockbench 内嵌 16×16 纯色诊断纹理。未提供精细材质、瞄准视差、动画、碰撞、握持姿态、MC 渲染适配或真实枪械精度。OBJ 不携带挂点，消费时必须同时读取 manifest。Blockbench 项目按 free JSON 结构生成并完成静态结构验证；本环境没有完成 Blockbench GUI 导入验收。离线 HTML 是独立的几何检查入口，不是待实现的游戏界面。

所有立方体由本目录脚本原创；不包含下载的 Tarkov 模型或贴图。真实数据切片用于测试，不等同全量内容许可或三维资源授权。

## 复现

在工作区根执行：

```sh
python3 source/mods/weapon_assembly/model-fixtures/generate.py EFTForge/docs/data/cache/items.json
python3 source/mods/weapon_assembly/model-fixtures/verify.py
```

生成器仅写本目录。不修改运行客户端、世界、源测试资源或正式发布内容。Java 核心的实际校验以主任务运行的驱动证据为准，下面脚本负责文件、源边映射、几何及身份的独立检查。
