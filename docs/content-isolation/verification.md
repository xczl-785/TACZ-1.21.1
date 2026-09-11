# 验证记录

2026-09-12，基线 ff715d80。

- 盘点脚本成功：54枪/45留9排；源码24种原弹药；172配方中32个隔离；3322资产文件保留哈希。
- Java资源策略627案例通过；9枪索引+32配方拒绝，其余现有资源放行，包含全部原弹药定义。
- `git diff --exit-code -- src/main/resources` 通过：原资产无修改/删除。
- `git diff --check` 通过。
- 原工作目录仍为 dev 且干净；分支隔离工作全部在独立 worktree。
- 首次离线编译因缺少 dotenv 插件缓存未进入Java编译；在线重试 `bash gradlew compileJava` 成功（7m38s），上游已有22个废弃API警告。
- `bash gradlew processResources --offline` 成功（5s）；检查实际构建输出，3个退役原弹药箱配方缺席，54枪定义和24口径定义保留，等待加载层筛选。
- 未切换 NewMod 依赖、未启动游戏或进行实机验收；暂无45枪的自有弹药兼容结论。
