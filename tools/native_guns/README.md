# 原生枪械批量作者流程

用途：维护已经接入的非 M4 原生枪械，不复制一套逐枪工具。M4 保留专用模型生成器，共用安装点校验规则。

## 作者输入

- `weapon-sources/native_<gun>/production.json`：零件归属、父槽位、兼容候选、默认装配及 TaCZ 原生绑定。不同枪械继续明确声明自己的例外，不按口径推断兼容。
- 同目录 `mounts.json`：每个定义的固定局部基准、子件安装原点与父槽位位置；与 M4 同一格式。不是动画骨骼旋转中心，不随网格包围盒、首候选或排序变化。
- 同目录 `editable/manifest.json` 与组件：独立模型和贴图，保留原生骨骼身份。

既有枪首次由 `adopt_mounts.py scar_l` 从已检查的预览/锚点提取安装点；已存在作者文件时拒绝覆盖。该命令仅用于一次性迁移，不能在修改模型后反复运行。日常生成只读作者输入，输出不再反向成为安装点来源。

## 按批执行

仓库根目录执行：

```sh
PYTHONDONTWRITEBYTECODE=1 python3 tools/native_guns/batch.py scar_l m16a1
PYTHONDONTWRITEBYTECODE=1 python3 tools/native_guns/batch.py --check-only scar_l m16a1
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/native_guns -p 'test_*.py'
bash gradlew prepareTaczWorkbench --offline
PYTHONDONTWRITEBYTECODE=1 python3 tools/native_guns/verify_workbench_runtime.py scar_l m16a1
```

批处理显式选择枪 ID，逐枪生成、校验并报告耗时，遇错即停。现有生成器会刷新共用枪托输出；不要与其他资源写入任务同时运行。检查会核对作者指纹、完整原生骨骼、几何/UV、附件和显示引用；工作台检查沿可达槽位安装再拆卸候选，检查默认方案不被污染。这不穷举所有配件组合，也不替代游戏内动画、光学、库存与帧率验收。

同批只做一次完整构建和宿主锁包更新。若仅修改作者资料且运行内容完全相同，可以先提交作者链，保留现有已接受测试包；不能把作者链提交号冒充宿主实际制品来源。

## 不变量

保留枪与零件 ID、默认装配和现有交互；不新增重复枪，不修改自有枪械项目，不改变 LOD 策略或美术资源。新增零件/改动安装关系必须同时显式更新关系与安装点，并先跑局部检查。当前工作是组织统一，不要求所有枪械拥有同样的零件名单。
