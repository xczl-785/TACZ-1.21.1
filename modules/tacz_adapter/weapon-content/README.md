# 枪械内容（同一个 TaCZ adapter 包）

`resources/` 是三把枪及其同包共享材质的生成资源，随 `tacz_adapter` 的 main resources 一起打包，Jar 内路径不变。不要人工编辑这里的 JSON 或 PNG。

输入归属、生成/检查/更新命令见 [制作流程](../docs/枪械导入流程.md)。生产内容以 `weapon-authoring/` 配置和其 `import.json` 指向的来源为准。`src/main/resources/` 保留接入层元数据、mixin 配置和公共接入资源；`src/main/java/` 保留通用引擎接入代码。

这里不是新 Mod，也不是可热加载内容插件。启动时读取通用注册索引，注册物品与模型类型；新增配置后必须重新生成和构建。

当前包含 `newmod_adar`、`newmod_radian`、`newmod_m4a1`、共享材质 `firearm_materials` 和总注册索引。材质不得依赖 UI 模块的资源；TaCZ 声效/枪口效果等真实引擎引用仍保留。详见 [资源归属补充结果](../docs/三枪资源归属补充结果.md)。
