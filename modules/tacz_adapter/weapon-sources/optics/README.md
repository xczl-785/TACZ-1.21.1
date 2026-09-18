# 光学编辑样本

当前仅 M4 显式接入两个额外候选，保留原来的 T2、ELCAN 和默认装配：

| 样本 | 新附件 ID | 原生显示行为 |
| --- | --- | --- |
| T2 红点 | `tacz_fork_tarkov:sight_t2_sample` | sight=true；原生配置 zoom=[2.5]、fov=35 |
| ELCAN 倍率镜 | `tacz_fork_tarkov:scope_elcan_sample` | scope=true；zoom=[4.25,1.25]、views=[2,2] |

每件目录的 `editable/components/*/model.bbmodel` 和相邻 `texture.png` 是完整高模编辑源，包含外壳、镜片、分划及定位空骨骼。副本来自仍保留的原生瞄具，保留原许可与来源指纹；此次验证完整制作链，不宣称是新设计的瞄具外观。辅助 `_n`、`_s` 贴图也独立保存。`display.json` 管光学参数，`data.json` 管原生附件属性，`optic.json` 管独立身份、名称、来源及 LOD。低模保留在 `lod/`，不从分划或模板遮罩几何猜测减面。

单件重新编译：`PYTHONDONTWRITEBYTECODE=1 python3 tools/native_optics/produce.py t2_sample`（或 `elcan_sample`）。完成枪械预览、图标和作者指纹的正式入口仍是 `python3 tools/native_m4a1/generate.py`，它读取 M4 的 `optics.json` 显式选择附件；兼容槽位由 `assembly.json`、安装基准由 `mounts.json` 维护。

完整模型通过新的原生附件 index/display/data 接入，使瞄准定位和镜片渲染读取同一个模型。不能把光学放进仅覆盖非光学外观的 `native_attachment_overrides.json`。工作台的静态几何与图标只投影外壳，排除位于远处的分划和遮罩，避免其污染模型包围范围；实际材质模式仍走原生附件渲染。

校验：`python3 -m unittest discover -s tools/native_optics -p 'test_*.py'`，以及 M4 资源检查和注册物品的安装/替换/拆卸测试。2026-09-19 两件均实际 Blockbench 加载、编译并读回：T2 119 方块、ELCAN 149 方块，往返最大位置误差 0、UV 一致。没有启动游戏，不能据此判断镜片最终观感。

下一步由所有者装到 M4 比较原版/样本：透镜是否挡住世界或出现实色面、红点是否局限在镜内、ELCAN 放大区域与黑边是否正确、两档切换是否稳定、拆装是否恢复、工作台与第三人称外壳是否正常。接受后再决定美术调整与扩面；本轮不修改原瞄具，不扩展其他枪。
