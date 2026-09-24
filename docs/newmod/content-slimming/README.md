# R03 / R06 / R08 / R10 内容退出交付

本批基线 `deb3fb91a9e4853f9a15a80da536e7530d38c22c`；对应主仓《TaCZ剩余内容盘点》所有者裁决。前三项与 R10 分开提交。此文不表示 Minecraft 实机验收。

## R03 / R06 / R08

删除固定靶、靶车、持枪展示架的注册、专属行为与资源；共享 bullet_ignore 标签、子弹、普通命中/击杀提示与火药配方保留。旧世界中这些已删除注册项无法继续使用；本次不修改世界或旧 NBT，旧 GunLevelExp 标签不再读取。

删除枪械等级/经验 API、升级消息、Toast、等级 tooltip。删除原生改装屏、组件、按键、原生及 adapter refit 协议、专属开发冒烟测试。公共 firearms 工作台仍负责按键、provider、物理装配与事务，保留 NativeAssemblyView/Icons/Models、附件属性投影和激光后端。旧 Refit 自定义键停止生效，使用公共装配键。未实现槽位右键入口。

共享附件属性文案虽沿用 gui.tacz.gun_refit.property_diagrams 键名，仍被 modifier 的属性展示使用，保留；不为了名字相似删共享能力。原枪模型内无害的 refit 定位骨骼随原资产保留，旧界面运行时路径及缓存已删除。

逐文件来源、删除前后校验见 [前三项源码清单](r03-r06-r08-source-audit.json)。

## 保留 / 暂缓

R05 过热本批保留；未来自有枪械/公共枪械平台必须具备过热能力。参考 dev 基线 `d5ca6a1278083e342a4ab499b245dc7d8dd04048` 和本批前 `deb3fb91a9e4853f9a15a80da536e7530d38c22c`，不改历史。

15 把正式枪和 146 个正式物理定义、普通弹道、Lua、抛壳、光学与公共平台归属不变。B3-01/B5-05 待验收、B6-01 待复验以及无 TaCZ 完整评测均不由本批自动关闭。

## R10 内置资源直接加载

先新增 BuiltinGunPack 并通过目录与 Jar 文件系统测试，再把 GunPackLoader 换成只读取 NeoForge 当前 tacz Mod 文件的嵌套包路径。客户端/服务端各按 AddPackFindersEvent 的 PackType 注册必需内置包，避免原全局单例仅按物理客户端/服务端选择一种类型。内置包仍为 BOTTOM 优先级；Minecraft 自身资源包/数据包机制保留。

删除 gameDir/tacz 目录/ZIP 扫描、外部导出注册 API、GetJarResources、旧包转换、转换提示、overwrite/convert 命令和唯一用途的预加载配置；保留 reload、网络同步、解析、Lua 及共享资源。缺失内置 assets/data 直接报错，不回退磁盘旧包；不删除用户磁盘任何包。旧 tacz-pre.toml 不再注册或读取，不主动删除。

离线测试逐字节读取全部合法内置资源，并核对枚举；隔离空目录、带冲突目录包/ZIP 的目录均不参与资源来源且文件未被改动；Jar 文件系统重复打开验证重载。默认包原有 lang/tr-TR.json 是非法 ResourceLocation，沿用 Minecraft 原有忽略规则，本批不改原资产。NeoForge 单元测试使用真实 ModList 的 mod 文件路径，分别注册 CLIENT_RESOURCES / SERVER_DATA，合并顶层资源后核对恰好 15 个正式枪索引及共享动画/Lua/附件数据。

## 所有者实机清单

完全退出旧客户端再从主仓启动脚本进入：确认靶子/靶车/展示架与等级提示已消失；公共装配键打开公共工作台，装卸配件、库存去向、预览/图标、瞄具激光及普通开火命中正常。确认重载后仍可用 15 把正式枪，磁盘旧外部枪包不再生效。旧世界若持有退休物件会失去相应注册项；本次未打开或改写世界。

离线检查与制品交付不代表上述实机已通过。上一批 slim2 的实机通过也不能代替本批。
