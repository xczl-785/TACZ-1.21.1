# 枪械内容（同一个 TaCZ adapter 包）

`resources/` 是十五把正式配件枪的生成资源，随 `tacz_adapter` 的 main resources 一起打包。正式枪与本地物理零件统一使用 `tacz_fork_tarkov` 命名空间；不要人工编辑生成的 JSON 或 PNG。

各枪离线制作来源位于 `weapon-sources/native_<枪名>/`。运行时配置中的 `authoringSource` 只定位该来源，`developmentCategory` 统一为 `tacz_fork_tarkov`，用于开发物品目录分组。`src/main/resources/` 保留接入层元数据、mixin 配置和仍被复用的 TaCZ 引擎资源；`src/main/java/` 保留通用引擎接入代码。

这里不是新 Mod，也不是可热加载内容插件。启动时读取通用注册索引，注册物品与模型类型；新增配置后必须重新生成和构建。

当前总注册索引只发布十五把 `tacz_fork_tarkov:*` 配件枪。废弃的 `newmod_adar`、`newmod_radian`、`newmod_m4a1` 及其专属零件、作者输入和材质已退出生产内容；TaCZ 声效、枪口效果、原生骨骼和通用附件等仍被正式枪使用的引擎资源继续保留。
