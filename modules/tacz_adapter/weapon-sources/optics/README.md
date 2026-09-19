# 光学编辑源

当前已扩展到默认包全部28个光学附件定义，并按原兼容关系接入15把保留枪中的14把，共222组关系；M870保持无新增光学。原两件样本的M4实机接受有效，其余视觉验收未完成。当前清单、操作命令、特殊类与验收表统一见[全量交付入口](../../../../docs/assembly-experiment/optics-batch/README.md)。

`catalog.json` 列出来源ID、新ID和独立源目录。每件的 `editable/components/*/model.bbmodel` 与相邻 `texture.png` 为完整方块编辑源，保留外壳、镜口、分划、观察点和空骨骼；辅助贴图有则保留。`display.json` 管光学模式，`data.json` 管附件属性，`optic.json` 管身份、名称、来源哈希和LOD。外观沿用原件，本批没有重新设计美术。

单件重新编译：`PYTHONDONTWRITEBYTECODE=1 python3 tools/native_optics/produce.py <目录名>`。整体重建通过 `tools/native_optics/batch.py`；仅编译单件不会重建枪械预览和作者报告。

## 编辑约束

- 当前仅接受cube及逐面UV；不能把任意triangle mesh放进这个回读入口。旋转UV需先烘焙。
- 保留映射骨骼的名称、父子、pivot和rotation。观察点/骨架变更需扩展并验证作者合同；不能仅移动Blockbench空骨骼后强行导入。
- 保留本件实际使用的ocular/ocular_sight/ocular_scope、division及编号scope_view；不要套用“必须恰有四个节点”的旧假设。P90外壳在default_sight。
- 纹理编辑须同步外部texture.png，不能只改内嵌图片。改变外壳后独立审查LOD；已有LOD不会自动随高模变形。
- 完整原生附件index供模型和ADS共用；禁止把光学当作普通外观覆盖。静态预览只提取外壳，避免远处分划污染取景。
- 原始资源及来源哈希保留；本地制作与实机接受不改变来源许可证。

历史两件样本证据见[样本验收](../../../../docs/assembly-experiment/optics-samples/README.md)。跨引擎指导见[制作指南](../../../../../NewMod/docs/进行中/瞄具制作与批量接入指南.md)，本批不开发自有枪械光学。
