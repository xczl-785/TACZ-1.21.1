# 枪械制作工具

统一入口：`weapon_pipeline.py`。完整输入要求、目录职责、新枪操作及实机检查见 [枪械导入流程](../docs/枪械导入流程.md)。Python 需要 Pillow、NumPy。

在 NewMod 根目录生成并检查全部三把枪：

```sh
python3 source/mods/tacz_adapter/tools/weapon_pipeline.py all \
  --output /tmp/newmod-weapons-review/resources \
  --reports /tmp/newmod-weapons-review/reports \
  --compare source/mods/tacz_adapter/weapon-content/resources
```

输出必须为空，报告与资源必须分开；不会覆盖生产目录。`all` 可换成 `adar`、`radian`、`m4a1`，单枪输出只注册该枪，不能整目录覆盖生产三枪包。对比有语义差异会以非零退出码退出，详情在 `comparison.json`；仅空白/JSON 排版或 PNG 编码差异另列。

- `build_adar.py`：旧 ADAR 来源兼容转换器，命令同样要求 `--output`、`--reports`。
- `build_weapon.py`：通用矩阵组件转换器；旧的作者目录参数仍可用，须增加上述输出参数。
- `build_presentation.py`：共同握持/定位生成阶段；正常操作由统一入口调用。
- `render_part_icon.py`：共同几何、UV、材质图标生成。
- `validate_weapon_resources.py --resources <资源根>`：独立验证；不传参数检查生产内容。材质必须在该资源根内存在，不从其他模块补缺。
- `audit_weapon_references.py --resources <资源根> --report <报告.json>`：列出所有资源 ID 的使用处和归属；核对同包材质及锁定 TaCZ 的声音/效果/默认动画/弹药索引文件，只读锁定包。统一生成入口自动产出 `references.json`。
- `preview_weapon.py`、`calibrate_weapon.py`：读取生产内容的离线预览与定位报告，不是游戏验收。

ADAR 第一人称制作配置在 `weapon-authoring/adar/presentation.json`，最终握持与定位还由 `handling.json`、`markers.json` 共同生成；分类声明在 `identities.json`，旧界面文案在 `lang/`。禁止手改生成资源。

对比覆盖枪械 namespace 以及材质库引用到的共享 namespace（例如 `firearm_materials`）。完整三枪当前为 183 文件；历史 175 文件统计漏计了 4 张同包共享材质，最新证据见 [资源归属补充结果](../docs/三枪资源归属补充结果.md)。
