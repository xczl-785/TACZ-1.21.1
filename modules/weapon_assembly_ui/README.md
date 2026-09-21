# weapon_assembly_ui（已归位到 firearms）

2026-09-21：本模块的**源码、资源与测试已全部迁出**到 NewMod 的 firearms Mod，TaCZ Jar 不再包含这些类与资源。本目录现在只保留设计与验证文档。

## 现在的归属

| 内容 | 新位置 |
| --- | --- |
| 屏幕、相机、槽位/候选布局、网格视口、材质质量、后端接口 | `source/mods/firearms/src/main/java/dev/firearms/client/workbench` |
| 外观缓存、纹理寿命、失败抑制 | `source/mods/firearms/src/main/java/dev/firearms/client/presentation`（`AssemblyIconCache`、`AssemblyIconFailures`） |
| 纯栅格算法 | `source/mods/firearms/src/main/java/dev/firearms/presentation/AssemblyIconRaster` |
| 资源（语言、材质库、材质贴图） | `source/mods/firearms/src/main/resources/assets/weapon_assembly_ui`（namespace 未变） |
| 测试 | `source/mods/firearms/src/test` |

TaCZ 只在 `modules/tacz_adapter` 保留宿主与原生后端：`AssemblyGunClient`（屏幕入口与协议）、`NativeWorkbenchScene`（实现公共 `WorkbenchModelBackend`）、`NativeAssemblyIcons`（内容查找与动态纹理 namespace）、`AssemblyGunWorkbench`（服务端授权）、`NativeWorkbenchStack`（预览物品构造宿主）。

## 保留在这里的文档

- [组装台完整方案](docs/组装台完整方案.md)：组装台的完整产品边界与版本路线，仍然有效。
- [材质使用说明](材质使用说明.md)：材质库与贴图约定，仍然有效。
- [verification.json](docs/verification.json)：迁移当时的历史验证记录，只证明当时状态。

可执行入口以 [modules/README.md](../README.md) 为准。
