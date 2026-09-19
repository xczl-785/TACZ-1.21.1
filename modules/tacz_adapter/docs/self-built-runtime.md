# 当前运行依赖

原独立 adapter 已取消；构建与安装请从[工程入口](../../../docs/newmod/README.md)进入。

实验室只发布一个 tacz Mod。正式包包括原 adapter 正式代码和十五把正式枪；开发验收包在同一包内增加开发工具。NewMod 宿主只选择其中一个完整 Jar，同时加载独立公共玩法 Mod，不发布宿主 Jar。

编译依赖以[公共锁](../public-dependency-lock.json)及[foundation 锁](../../public-dependency-lock.json)为准。NewMod 运行制品锁位于 `source/integration-tests/tacz-runtime/dependency-lock.json`。本目录原 dependency-lock.json 已退役，历史记录中的旧版本只供追溯。

供弹仍从战术库存消费，保留精确弹种身份、弹匣与膛内计数。旧独立生存装填不恢复；没有新增退弹键。原默认口径临时返还只在原有适用调用点生效。
