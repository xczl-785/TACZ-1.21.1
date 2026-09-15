# 第二项：adapter 吸收交付

2026-09-15。工程验证记录见 [机器证据](adapter-integration-evidence.json)。前两项待用户联合验收；第三项没有实施。未启动 Minecraft 客户端/服务端，未触碰日常世界。

源码提交 `556149d37403fcf5a401a792380f3e94281f83ec`；NewMod 初始接入 `7bb5d87`，默认配置缓存修复后的最终接入 `e45dd1f896adb4342781c88716c690d34a850372`；最终版本 `1.1.8-hotfix-r6-newmod.556149d3`。两份 Jar 的完整 SHA-512 位于机器证据及主线锁。

## 最终组合与边界

一个 tacz 包承载原生引擎、四个 weapon 内部模块、原 adapter 接入和 ADAR/Radian/M4A1 三枪。NewMod 的 `source/integration-tests/tacz-runtime` 是不发布 Jar、没有 Mod 入口的验收宿主。公共 foundation/tactical/character/combat 继续独立，tarkov_content 只被 TaCZ 开发工具引用，日常宿主保留它；无公共玩法代码嵌入 tacz。

正式包排除开发工具；`-development.jar` 是包含开发工具的完整 TaCZ 包，日常宿主默认选择它。两包不能同时安装。旧 `tactical_tacz_adapter` 及四个 weapon Mod 均不再加载，但既有物品、组件、协议、资源 ID 保留。

## 迁移清单

- [adapter 386 文件](adapter-migration.json)：逐文件旧新路径、前后 SHA-256、迁移/整合/退役。
- [190 个制作输入](adapter-authoring-migration.json)：原始模型、材质、配置来源与快照；ADAR 旧文档数据仅作为历史快照保留，生产转换器读取实验室输入。
- [15 个 TaCZ 自有调用点文件](adapter-source-integration.json)：以 `588db63a` 为前驱精确记录；历史弹药与第一项账本没有重写。
- [公共依赖锁](../../modules/tacz_adapter/public-dependency-lock.json)：NewMod `3ef90e9` 的公共制品。foundation 继续用第一项锁；开发辅助 Jar 仅供编译，不嵌入发布包。

三枪模型、动画、执行配置及资源原字节保留。重新生成 183 份资源，0 内容差异、4 序列化差异。公共模块生产源码未改；旧 NewMod adapter 生产目录删除。

## 12 个 Mixin 的归宿

| 旧注入 | TaCZ 内部调用与保持的行为 |
| --- | --- |
| ContentAmmoMixin | TarkovAmmoItem 实现 IAmmo，保留固定物品身份与口径匹配 |
| ReloadMixin | AbstractGunItem 的 canReload/dropAllAmmo 开头；精确弹种返还、拆分堆叠及库存溢出落地，膛内计数沿用旧逻辑 |
| ScriptAmmoMixin | ModernKineticGunScriptAPI；消费/余量/扣弹门槛前置；速度和弹数在原属性修饰完成后覆盖 |
| AimResourceMixin | LivingEntityAim 开头；服务端人物瞄准资源门槛 |
| ClientAimResourceMixin | LocalPlayerAim 开头；客户端资源投影门槛 |
| AssemblyShootMixin | LivingEntityShoot 私有 shoot 开头；在枪械计时和状态变更前阻止缺件射击 |
| ClientAssemblyShootMixin | LocalPlayerShoot 开头；同一门槛及原提示 |
| AmmoHudMixin | GunHudOverlay.handleInventoryAmmo 开头；采用弹药读取战术库存余量 |
| TargetGeometryMixin | EntityUtil.getHitResult 开头；战斗模块声明接管时返回其结果，包括空命中 |
| AssemblyPresentationMixin | 第一人称变换头/末尾及原射击晃动入口；保留组装呈现顺序与提前返回行为 |
| AssemblyCameraMixin | CameraSetupEvent 原俯仰/水平后坐力、倍率和模型 FOV 参数点 |
| BulletMixin | EntityKineticBullet；命中报价→原 Pre/目标处理→一次提交→成功后续弹→返回清理；保留原 spawn 扩展、余寿命与伤害比例 |

没有保留 adapter Mixin。原 Z 改装事务、组装交易、防重放、会话校验、库存交换、AmmoBridge 实现除订阅归属外原文保持；初始化在引擎原注册结束后执行一次。护甲绕过标签按原资源叠加语义合并三条伤害 ID，避免取消旧包后丢失 resolved_bullet。

## 验证与证据上限

实验室构建、内部模块 69 项 JUnit、adapter 2 项 JUnit、12 项制作工具测试、纯 Java 边界、运行准备；4 项旧新方法体/事件顺序对照检查；NewMod 公共库存/人物/战斗测试任务通过（未改源码，复用有效缓存）。86 弹、15 原生枪、85 配件及口径效果继续由原保护脚本检查。最终制品/运行锁的结果另见机器证据。

方法体对照能证明本次搬迁没有改写已有事务与提交算法，不证明游戏中的事件订阅、渲染或网络已实机通过。以下场景由用户验收，不能把编译或缓存单测当作接受。

## 联合验收

1. 使用 NewMod 根目录原 `启动开发客户端.command`。它调用 `:tacz_runtime:runClient`，仍用 `source/runs/tactical-client`；没有创建/覆盖存档。进入原日常世界。
2. 有开发命令权限时用 `/devitems`，按 adar、radian、m4a1 来源领取三枪和配件；tacz 来源检查原生枪、弹药与配件。物品显示与旧包一致，无缺纹理/重复目录。
3. 三枪逐一持枪、开机瞄/已装瞄具、射击、换弹；检查后坐力、准星、手部、枪口与抛壳。模型与动画没有重做；比较是否出现本次新增退化。
4. 对组装枪按 Z：拆装、替换配件，取消/关闭后物品不丢失不复制；拆必需枪管禁止开火，装回恢复；切手、距离变化或重复请求不得绕过会话与库存事务。
5. 原生枪按 Z 回归原配件安装/拆卸。胸挂/口袋提供同口径不同弹种，检查供弹与 HUD、背包不直接供弹、余量正确。拆卸涉及弹匣的组装子树时，按旧逻辑返还实际弹种，膛内状态不混算。没有新增“退弹键”；固定口径临时接口仅保留原适用调用点。
6. 检查人物不能瞄准状态下的门槛、裸身/护甲目标命中与重复伤害、跳弹效果。保存退出后重进，三枪装配、库存、弹种与余弹仍正确。

开发命令详情保留在模块主题文档及 `development-commands.json`。已有隔离 smoke 入口移到 `:tacz_runtime:runAdapterSmoke/runClientSmoke/runAdarAudit`，本次没有执行。正式环境用 `-PwithDevelopment=false`；不含开发目录是预期行为。

## 回退

第二项必须整体回退：NewMod 回到 `3ef90e9f79c3544b5c2b5b5a8b863df66797c11f`，恢复原 adapter、运行设置、脚本和第一项 vendor 锁；实验室回到 `588db63a22279cb1b766cc8bbf6092295f0f618b`。旧第一项版本化 Jar 已保留。先保全后续未提交工作，再由所有者决定回退提交或另建基线检出，不能只替换 Jar 后混用新宿主。存档不在这些提交中，本次没有迁移或修改存档。

## 公共开发辅助制品来源

在 NewMod 基线 `3ef90e9`、Java 21 下执行 `:tactical:jar :character:jar :combat:jar :tarkov_content:jar :tactical:developmentClasses :tarkov_content:developmentClasses`。正式公共 Jar 直接取对应 `build/libs`；两个 development 辅助 Jar 仅把对应模块 `build/classes/java/development` 和 `build/resources/development` 下文件按相对路径 ZIP 打包，不含 Mod 元数据。它们只提供开发 Java 编译符号；实际验收宿主加载 NewMod 对应开发 source set。文件哈希固定在公共锁，更新公共代码须重新构建并更新锁，不能只改版本文字。

## 主控复核返修：默认配置缓存

首轮主控未通过：最初证据命令使用 `--no-configuration-cache`，新宿主在默认缓存下的执行闭包引用 `rootProject`，导致校验失败。已将候选文件与版本/哈希在配置期捕获，执行期只读取值；保留仓库默认缓存策略。相邻 verifyRuntime 使用预先捕获的文件集合和源 Mod ID，不调用 Project。开发 `verifyRuntime + prepareClientRun + verifyModuleBoundaries`、正式 `verifyRuntime + prepareProductionClientRun` 各在默认配置下首次保存、再次复用通过，四份日志已归档。仅 NewMod 宿主修复，实验室源码和两个固定 Jar 未变；没有启动游戏。

## 主控最终工程复核

2026-09-15：配置缓存返修 e45dd1f 后，主控独立重跑默认开发 verifyRuntime/prepareClientRun/verifyModuleBoundaries、正式 verifyRuntime/prepareProductionClientRun，各首次存储与再次复用均通过。开发948类、正式907类唯一归属及双包哈希检查通过。此前已独立复核4项方法体/事件顺序对照、公共模块与firearms无源码差异、初始化和旧注入迁移边界。其余测试依据实施归档证据，未全部重复执行。第二项工程复核通过；第一、二项仍待用户联合实机验收，未启动游戏，不进入第三项。
