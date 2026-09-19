# Glock 17 原生组装编辑源

仅 TaCZ 原版 Glock 17 接入，旧 ADAR/Radian 不属于本生产链。

- `production.json`：逐 cube 实体归属、目录/默认树、必需机械件、瞄准/附件路径、显示依赖、装备槽、物品身份及翻译配置。
- `editable/manifest.json` 与 `editable/components/`：六个可独立编辑的 Blockbench 模型及贴图。编辑它们后重建运行资源；不要改原枪包或生成目录作为事实源。
- 三种扩容弹匣复用 `../native_attachments/magazine_variants/` 的 Glock 专属源；5个枪口/激光引用公共运行资源，激光保留既有 LOD。
- 六个光学候选沿用原始模型与原生光学渲染，未新增光学编辑源。
- `build-report.json`：来源哈希、归属与运行几何证据；不是实机验收。

从仓库根运行：

```sh
PYTHONDONTWRITEBYTECODE=1 python3 tools/native_guns/produce.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/native_guns/validate.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/native_guns/test_produce.py
```

首次缺源时可用 `produce.py --append`；只追加缺失源，已有编辑不会覆盖。共用生产器通过 `--config` 接入下一把经过人工归属审查的枪，不按名字猜真实零件。

默认六件：枪身、套筒、枪管、前准星、后照门、17发弹匣。枪管保留两个不同原动画骨；装填弹药不是可拆物品。弹匣移除仍保留真实膛内弹状态，显示层只隐藏失去所属实体的展示几何。

57个原骨和动画引用保持不变；后继三枪批次已交付 Glock 高/低模264/165 cubes，见[低模后继证据](../../../../docs/assembly-experiment/native-batch-one/README.md)；最初264/264仅是首批快照。
