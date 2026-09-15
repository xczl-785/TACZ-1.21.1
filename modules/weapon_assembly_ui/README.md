# Weapon Assembly UI

独立组装工作台，依赖 `weapon_assembly` 算法与 `foundation` 公共 LDLib2 控件。按 Gunsmith 原型保留部位标签、附近浮窗、悬停预览、点击安装、拆卸、撤销、恢复初始配置、相机旋转缩放；通过“进入子槽 / 返回上层”处理递归配件。

## 打开

双击工作区根目录的 **启动组装测试客户端.command**。加载后自动打开组装页，无需创建或进入世界。Esc 先关闭配件浮窗，再关闭页面返回主菜单；重新启动可恢复初始样本会话。

终端等价命令（在 `source/`）：

```sh
./gradlew :weapon_assembly_ui:runClient
```

运行目录是 `source/runs/weapon-assembly-client`，不使用原 `runs/tactical-client`。仅加载 foundation、LDLib2、算法、界面与平台依赖；不会加载 TaCZ、日常库存、战局世界。

## 试用路径

1. 点“机匣 → 进入子槽 → 枪管”，悬停配件看临时预览，点击安装；同型号的不同实例用短 ID 区分。
2. 拆下枪管，页面显示缺必需件，枪仍可继续编辑；右下撤销可恢复。
3. 在机匣子槽选择瞄具，在枪管子槽选择消焰器或导气块；返回上层可拆换整组机匣。
4. 拆下/替换的配件及其子件保留在样本会话，可以在兼容槽的候选中重新安装。撤销和恢复初始配置同时恢复整份供货集合。
5. 拖空白处旋转、滚轮缩放；复位视角只改变相机。

默认样本为用户提供的 ADAR 2-15：1 项本体、10 项配件。几何从 `model-white.bbmodel` 导入，现默认使用自行生成的简易材质，可用“白模对照”按钮切换。6 种材质按 32 个具名区域分配；换材质和 Blockbench 编辑入口见 [材质使用说明](材质使用说明.md)。原白模隐藏的瞄具镜片继续隐藏，共 26 个可见网格、7,260 个三角面。每个配件型号额外提供两件独立实例，刚拆下的配件排在候选前面。

兼容关系和属性来自 [EFTForge 提取数据](../../../../docs/参考资料/adar-20260913-data/README.md)。开发目录只提供有模型的 11 个型号，所有源槽位和必需标志保留；完整兼容和冲突列表保存在参考数据中。初始空弹匣整枪重量 3.584 kg、人机 60.5、垂直后坐力 79.68。

## 职责与数据边界

- `client/WorkbenchScreen` 只使用 `WorkbenchAccess` 提供的状态与回调；控件/主题/窗口/滚动区复用 foundation 的公共 API。
- `session/AssemblySession` 是明确的内存样本宿主；预览不提交，提交重新调用同一个 AssemblyEngine，历史保存整份树与供应配件。它不写 ItemStack、背包或磁盘。
- `client/AssemblyViewport` 使用 Minecraft 原生三角顶点缓冲、深度测试和裁切绘制模型；相机投影与子件挂点共享数学。`ModelGeometry` schema 3 保存具名三角网格、UV 与区域，兼容 schema 1 盒体及 schema 2 白模。`AssemblyMaterials` 独立解析材质库和区域绑定。模型与挂点由宿主传入，无需另一套兼容算法。镜头以初始完整模型定中心和尺寸，拆装时不跳动；部位按钮放在两侧。
- `src/development` 才读取模型夹具并自动打开页面；正式 Jar 不含开发入口或样本目录。
- 后续真实宿主实现 `WorkbenchAccess`，通过服务器库存交换提交；不能把样本会话当权威库存。透明/PBR 效果、动画、任意格式通用导入、真实库存/射击均留后续。

## 白模导入

生成产物已经放入 `src/development/resources/assembly-adar`，日常启动不依赖外部素材目录或 Blockbench MCP。重新导入会重置手工调整的分区和 UV，请先备份。需要重建时在工作区根运行：

```sh
python3 source/mods/weapon_assembly_ui/tools/import_adar.py /Users/zhengpanpan/Downloads/tempProgram/adar-20260913
```

转换器只读原素材并验证 SHA-256。每个顶点减去该零件的艺术挂点，父槽坐标等于子件挂点减父件挂点；装回后恢复原素材的相对位置。挂点只用于显示，不是动画转轴，不参与兼容规则。

当前导入支持本批 free mesh 的三角/四边面、单层组、零位移旋转，遇到非零变换或嵌套组会报错。四边面排序与 Blockbench 原生结果逐网格匹配，证据见参考数据中的 `blockbench-native-check.json`。未来带骨骼变换的素材扩展转换入口，运行时继续消费独立网格合同。

## 工程检查

```sh
./gradlew :weapon_assembly_ui:build verifyModuleBoundaries verifyLocalization
./gradlew :weapon_assembly_ui:runUiAudit
```

第一条做普通 JVM 会话/几何测试与 Jar 隔离检查。第二条打开真实 Minecraft 客户端，驱动标准按钮和相机，在 `runs/weapon-assembly-audit` 留本轮报告及截图，之后自动退出；报告缺失或失败会使 Gradle 失败。该目录与人工测试入口也分开。

2026-09-13 工程检查通过：构建、11 项 JVM 测试、模块边界、本地化、正式 Jar 样本隔离；另通过 3 项 Python 外观导入测试和 Blockbench 原生保存往返校验。隔离客户端覆盖材质/白模切换、预览/安装/拆卸/撤销、三级子槽、机匣整组返还、旋转/缩放/GUI 缩放，以及 10 项配件逐项拆下并以同一实例装回。报告为 `source/runs/weapon-assembly-audit/assembly-ui-audit.txt`，截图位于其 `screenshots/` 子目录。

**2026-09-13：用户确认本次 Mod 修改验证完成并授权提交，包含当前模型、材质和交互改动。** 所有者负责正式界面、手感和完整玩法验收。工程检查只证明已覆盖的启动与操作链可运行。参考：[原型梳理](../weapon_assembly/docs/gunsmith原型梳理.md)、[接入方案](../weapon_assembly/docs/界面接入方案.md)。
