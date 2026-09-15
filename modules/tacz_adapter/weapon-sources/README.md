# 生产来源

- ADAR：`adar/` 保存已接受的派生几何与纹理快照。`provenance.json` 记录原 UI 文件路径和逐文件 SHA-256。UI 历史副本保留作开发样例；生产生成器只读这里，不运行 UI 导入器。
- ADAR 原始模型、数据库和接入历史仍在工程 `docs/参考资料/adar-20260913-data/`；`weapon-authoring/adar/import.json` 显式引用原始物品数据。
- 雷电、导入 M4A1：原始组件包、安装矩阵、预设、名称、原始数据库和材质图片仍完整保留在 `docs/参考资料/radian-20260914-data/`、`m4a1-20260915-data/`。各自 `weapon-authoring/<枪>/import.json` 是生产引用入口，避免再复制两份大源包。

不要在原稿上试改。修改制作参数应进入 `weapon-authoring/`；需要替换已接受的来源时保存新修订并更新引用/来源哈希。历史 `import-report.json` 已归档至 `weapon-reports/archive/`，新报告只写显式 `--reports` 目录。

ADAR 作者材质现在引用 `newmod_adar:textures/materials/*`。`import.json.textureCopies` 将这里保留原始路径与哈希的四张 PNG 按字节复制进生成内容，再从生成目录渲染图标；不缩放、不重编码、不改 UV 或颜色参数。UI 原纹理继续留给 UI 的真实消费者，不因枪械拥有副本而删除。M4A1 的 `firearm_materials` 也是本内容包的资源命名空间，不是另一个 Mod；生成器从其原始材质输入生成，随 `weapon-content/resources/` 打包。
