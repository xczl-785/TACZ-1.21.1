# UMP45 原生组装编辑源

本批按原生 TaCZ UMP45 审计结果拆为 8 个默认实体：下机匣与扳机组件、上机匣（含原厂机械瞄具）、枪管、枪机、拉机柄、原厂枪托组件、导轨套件、标准 25 发弹匣。保护耳、机匣附件和装饰细节不自动视为独立可拆配件。

- `production.json` 是默认树、模型所有权、兼容槽位、显示规则及注册片段的输入。
- `source-audit.json` 记录原始资源哈希、全部 69 骨骼、473 cube 归属、动画、挂点和源附件兼容标签。
- `editable/components/*/model.bbmodel` 与同目录 `texture.png` 为 8 件独立编辑源；只转换副本，不改原始资产。
- 3 个扩容弹匣直接复用公共 UMP45 专属编辑源，容量 32/40/48；其他非瞄具附件复用公共库。16 个既有光学候选只引用原模型，没有新增光学编辑源。

默认编辑源共 423 cubes，公共扩容变体 33 cubes，手部/弹药展示 17 cubes；合计覆盖全部 473 cubes，无重复所有权。原模型 UV 为 128×128，沿用原纹理。初次提取逐 cube 验证编辑源往返后的顶点与原资源相符，纹理哈希一致。

机械必需路径为 `upper`、`upper/barrel`、`upper/bolt`、`upper/charging_handle`；供弹槽位 `magazine`。机械瞄准依赖 `upper` 存在，光学路径为 `upper/rails/scope`。原厂机瞄并未通过原生 `sight/sight_folded` 切换，安装光学时维持原样；后续是否精拆归机械瞄具姿态阶段，不能附带扩大光学生产范围。

枪托完整保留原生 `ump45_stock_extended`/`stock`/`hinge` 层级及展开姿态，不增加不存在的折叠机制。原生没有 `additional_magazine` 骨骼；换弹展示使用 `magzine_and_bullet` 子树及 `bullet` 动画。弹匣拆卸额外关闭两处弹药展示；枪膛展示保留原 ammo gate，并经枪管存在要求和枪机祖先存在要求同时约束。

复核与首次提取命令：`PYTHONDONTWRITEBYTECODE=1 python3 tools/native_guns/audit_ump45.py --append-editable`。已登记编辑源不会重写。运行资源由公共 `tools/native_guns/produce.py` 统一生成，本文不代表已接入或实机验收。
