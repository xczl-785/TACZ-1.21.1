# 原生附件公共编辑源

`extract.py` 将审阅过的独立附件几何转换为可编辑 cube 源，输出 `modules/tacz_adapter/weapon-sources/native_attachments/editable/`。不读取 M4 挂点，不改变枪械兼容，不覆盖已有编辑模型。UV PNG 按字节复制，原有 `_n` / `_s` 辅助贴图一同留存；每件记录 index、display、data、几何、贴图来源哈希。当前 6 件枪口原生没有 LOD 引用，保留全几何回退，不猜造低模。

- `encode(geo, texture, definitionId)` 接收 `minecraft:geometry[0]` 和纹理路径，返回编辑源元数据与 bbmodel。调用者可先裁剪几何，但必须保留所选 cube 的全部祖先节点，并单独保存原始 cube 索引。
- `load_part(row, root)` 读回原生骨架、cube、UV 尺寸、纹理与 cube 数量；保留源空骨架和运动父级。
- `import_assets.py` 只将公共编辑源编译到 `tacz_assembly` 隔离路径，`standalone.json` 仅提供公共资源目录。后续组装枪必须显式引用，绝不全局替换原生枪包。

```sh
PYTHONDONTWRITEBYTECODE=1 python3 tools/native_attachments/extract.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/native_attachments/import_assets.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/native_attachments/test_extract.py
```

原始模型仍只读。提取可反复运行而保留编辑内容；导入器根据当前编辑源重新生成资源。运行测试不启动游戏，不代表用户实机验收。
