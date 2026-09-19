# TaCZ 制作工具入口

在实验室根目录运行。旧三枪 `weapon_pipeline.py`、`build_adar.py`、`build_weapon.py` 已随生产源退役；历史命令不再可执行。

| 内容 | 当前入口 |
| --- | --- |
| M4 关系、安装点、可编辑模型及预览 | `python3 tools/native_m4a1/generate.py`；[作者合同](../weapon-sources/native_m4a1/README.md) |
| 其他十四枪 | [公共生产流程](../../../tools/native_guns/README.md)，使用其 produce/batch/validate 命令 |
| 公共非瞄具 | [源说明](../weapon-sources/native_attachments/README.md)、[转换工具](../../../tools/native_attachments/README.md) |
| 28 件瞄具与枪械接入 | [全量光学流程](../../../docs/assembly-experiment/optics-batch/README.md)；`python3 tools/native_optics/batch.py --check-only` 只检查现有输出 |
| 中性几何检查与校准 | [外置检查工作台](../docs/外置枪械检查工作台.md)；不证明镜片、原生动画或游戏验收 |

编辑作者源后重建并检查生成差异，再构建与更新锁。不要把生成资源或历史报告反过来当作者输入；辅助工具的参数以现存脚本为准。
