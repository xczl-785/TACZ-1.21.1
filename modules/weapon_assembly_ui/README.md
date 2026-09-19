# Weapon Assembly UI

`weapon_assembly_ui` 是 TaCZ 内部的通用枪械组装界面模块。它提供 `WorkbenchScreen`、视口与内存会话，但不拥有真实库存和生产枪械资源。

正式入口由 `tacz_adapter` 的客户端宿主调用：

```text
AssemblyGunClient -> WorkbenchScreen
```

旧 ADAR 独立演示客户端、自动打开宿主、演示贴图及旧作者工具已经移除。当前枪械模型、材质和装配数据从 [`tacz_adapter/weapon-sources`](../tacz_adapter/weapon-sources/README.md) 进入生产链。

## 边界

- `WorkbenchScreen` 只通过 `WorkbenchAccess` 读取状态并提交用户操作。
- `AssemblySession` 是内存会话实现，不写入 `ItemStack`、背包或磁盘。
- `AssemblyViewport` 消费宿主传入的几何、材质与模型后端。
- `src/test/resources/adar-regression` 只保留 JVM 回归所需的最小 ADAR 几何与材质夹具，不进入正式 Jar。

## 验证

在 TaCZ 仓库根目录运行：

```sh
bash gradlew weapon_assembly_uiTest verifyWeaponModules --offline
```

完整客户端验收使用 NewMod 的正式开发客户端入口，由所有者执行；本模块不再提供独立 Minecraft 客户端入口。
