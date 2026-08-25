# QCloudy_Addition

**面向 Fabric 的中英双语、纯客户端 Hypixel SkyBlock 辅助模组。**

QCloudy_Addition 将地图、按内容显示的 HUD、钓鱼与狩猎提示、宠物信息、Century Cake 计时、Deployable 消失提醒，以及完整离线 Attribute Shard 指南与规划器整合进一个按功能分类的界面。

> **最新稳定版：Release 0.3.9，支持 Minecraft 26.1.2 与 26.2。** 必须下载与 Minecraft 版本完全一致的 JAR；需要 Java 25 与 Fabric API。

## 实验性统一编辑器

> **注意：“统一设置编辑”和“统一 HUD 编辑”目前只是概念测试，默认关闭，尚不稳定。** 第三方模组更新可能随时改变其内部配置或 HUD 结构。请谨慎开启、提前备份配置，并在对应模组自己的设置/HUD 编辑器中核对每一项更改；提供方原生编辑器始终具有最终权威。

玩家明确开启并二次确认后，QCA 可以探测已安装 SkyHanni、Skyblocker、Firmament、BabyZombieAddons 与 Feesh 中能够安全识别的能力。设置扫描与 HUD 扫描相互独立；每次扫描或 Refresh 都要再次确认；未知分支会安全跳过；空分类自动隐藏；“兼容性缺失”只读页面只列出已经识别但无法安全管理的功能。这些模组全部为可选项，QCA 启动不依赖它们。

## 主要功能

### Attribute Shard Lab

- 离线收录 320 个 Bazaar Shard，并提供逐 ID 图标、品质色与语义游戏颜色。
- 详情包括效果、家族、Skill、生物种类、自然获取方法、捕捉/击杀要求、已审核的掉率信息，以及是否只能通过 Fusion 获得。
- 有序 Recipes 与 Uses、反向关系、可点击跳转、Special Fusion 数量、Chameleon 机制与候选路线。
- 多步 Fusion Tree、Materials Only 汇总、可编辑每小时获取速度、Ironman 规划、可拖动 Fusion Lines，以及按 Profile 保存的 Hunting Box 仓库。
- 价格路线只读取可选兼容提供方已经存在于客户端的价格缓存。QCA 运行时不会请求 Bazaar、Wiki 或价格服务器；没有合适提供方时，价格模式会明确不可用，离线规划仍正常工作。

### HUD、宠物与计时

- 装备宠物 HUD 显示收到的等级、品质色名称、已验证的宠物/皮肤头像、经验进度、距满级经验、皮肤与 Pet Item。
- 挖矿任务与粉末、Torrhus/Galatea 资源、Safari 进度、Crimson Isle 阵营任务等 HUD 只有在存在有效内容时才显示。
- `/cake` 或 `/centurycakeeffect` 打开 Century Cake 菜单；使用现实时间 48 小时计时，合并同时过期提醒，并提供必须由玩家点击的续效果操作。
- Power Orb 与 Flare 消失提醒；重新放置 Flare 会完整重置生命周期，距离、实体卸载和使用失败不会被当作消失。
- 本地玩家水钓或岩浆钓鱼确认上钩时播放一次可选 Ciallo 提示；默认关闭，每根鱼钩去重。

### 地图与视觉辅助

- Dwarven Mines 地图使用连续的大致 X/Z 投影，刻意忽略 Y 与计分板子区域；The Mist 上方桥梁不会再切层或让指针消失。
- 三层 Glacite 地图按高度选择。
- 可选 Fairy Soul 路标、Ender Dragon 轮廓、Beeheemoth 辅助、Lasso REEL、Warden 就绪、Tree Gift 等纯客户端提示。

## 安装

| Minecraft | 必需 Fabric API | 可运行文件 |
|---|---|---|
| 26.1.2 | 0.155.2+26.1.2 或更高兼容版本 | `QCloudy_Addition-0.3.9+26.1.2-Release.jar` |
| 26.2 | 对应 26.2 的 Fabric API | `QCloudy_Addition-0.3.9+26.2-Release.jar` |

还需要 Fabric Loader 0.19.3 或更新版本、Java 25。Mod Menu 可选。实例 `mods` 文件夹中只放一个可运行 JAR；不要把 `-sources.jar` 当成模组安装。

使用 `O`、Mod Menu、`/qca` 或 `/qc` 打开 QCA。`/qca`、`/qc`、`/qshard`、`/cake` 与 `/centurycakeeffect` 都是本地客户端命令。

## 纯客户端边界

QCA 只读取客户端已经收到的 Tab/计分板/聊天/标题文字、已打开菜单、本地背包、已加载实体与方块。它不会自动移动、点击、战斗、钓鱼、捕捉、Fusion 或循环重连；也没有遥测、远程更新器、隐藏区块请求或运行时 Hypixel API 依赖。

QCA 中仅有的服务器命令载荷为 `warp torrhus`、`chapter torrhus`，以及玩家直接点击 Century Cake 续效果文字后执行的 `visit northwestcloudy`；没有玩家直接操作就不会发送。

## 兼容性与免责声明

QCloudy_Addition 不依赖 SkyHanni、Skyblocker、Firmament、BabyZombieAddons、Feesh、JEI 或 Mod Menu。可选适配器使用能力探测而不是精确版本白名单，但这无法让未公开的第三方内部结构变得稳定；无法安全识别的结构会被跳过。

使用任何 Minecraft 模组均由玩家自行承担风险。QCloudy_Addition 是独立社区项目，与 Hypixel Studios、Mojang Studios 或 Microsoft 无隶属或背书关系。

- 官网：[qcloudy.net](https://qcloudy.net/)
- 下载：[qcloudy.net/download](https://qcloudy.net/download/)
- Wiki：[GitHub Wiki](https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/wiki)
- 源码与问题反馈：[GitHub](https://github.com/gprztb6nw4-dotcom/QCloudy-Addition)
