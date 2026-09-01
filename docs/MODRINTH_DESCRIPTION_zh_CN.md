# QCloudy_Addition

**面向 Fabric 的中英双语、纯客户端 Hypixel SkyBlock 辅助模组。**

QCloudy_Addition 将地图、按内容显示的 HUD、钓鱼与狩猎提示、宠物信息、Century Cake 计时、Deployable 消失提醒，以及完整离线 Attribute Shard 指南与规划器整合进一个按功能分类的界面。

> **当前公开测试版：Beta 0.3.10，支持 Minecraft 26.1.2 与 26.2；最新稳定版仍为 Release 0.3.9。** 必须下载与 Minecraft 版本完全一致的 JAR；需要 Java 25 与 Fabric API。

## 实验性统一编辑器

> **注意：“统一设置编辑”和“统一 HUD 编辑”目前只是概念测试，默认关闭，尚不稳定。** 第三方模组更新可能随时改变其内部配置或 HUD 结构。请谨慎开启、提前备份配置，并在对应模组自己的设置/HUD 编辑器中核对每一项更改；提供方原生编辑器始终具有最终权威。

玩家明确开启并二次确认后，QCA 可以探测已安装 SkyHanni、Skyblocker、Firmament、BabyZombieAddons 与 Feesh 中能够安全识别的能力。设置扫描与 HUD 扫描相互独立；每次扫描或 Refresh 都要再次确认；未知分支会安全跳过；空分类自动隐藏；“兼容性缺失”只读页面只列出已经识别但无法安全管理的功能。这些模组全部为可选项，QCA 启动不依赖它们。

## 主要功能

> **源码预览边界：**Attribute Shard Lab 中由 QCloudy 托管的市场价格来源，以及完整的“玩家档案浏览”一节，属于仅面向 Minecraft 26.1.2、尚未公开的 `0.3.10-alpha1` 源码快照，不包含在公开 Beta 0.3.10 中。

### Attribute Shard Lab

- 离线收录 320 个 Bazaar Shard，并提供逐 ID 图标、品质色与语义游戏颜色。
- 详情包括效果、家族、Skill、生物种类、自然获取方法、捕捉/击杀要求、已审核的掉率信息，以及是否只能通过 Fusion 获得。
- 有序 Recipes 与 Uses、反向关系、可点击跳转、Special Fusion 数量、Chameleon 机制与候选路线。
- 多步 Fusion Tree、Materials Only 汇总、可编辑每小时获取速度、Ironman 规划、可拖动 Fusion Lines，以及按 Profile 保存的 Hunting Box 仓库。
- 价格路线读取 QCloudy 有界市场快照；打开 Planner 并加载价格时会进行一次有界异步 HTTPS 读取。获取成本使用立即买入，清算价值使用立即卖出；缺失价格保持未知。Ironman 与离线规划不依赖该服务。

### 玩家档案浏览

> **开发预览：**本节描述仅面向 Minecraft 26.1.2、尚未公开的 `0.3.10-alpha1` 源码快照；玩家档案浏览不包含在公开 Beta 0.3.10 中。

- `//pv [玩家名或 UUID]` 与 `/qpv [玩家名或 UUID]` 打开 QCA 风格只读 SkyBlock 档案界面；不填写参数时使用本机玩家。普通 `/pv` 保留给其他模组。
- 可切换该玩家的可见 Profile，并查看非 Dungeon 的概览、装备、饰品、宠物、背包/Storage、技能、Slayer、挖矿、Minion、生物图鉴、收藏、Crimson Isle、Rift、其他/Farming、Museum、Garden、市场/净值分类。
- 私密、缺失、部分数据和旧缓存都会明确标记。完整市场来源表示所需已发布组成均可用；部分数据仍可保留可用价格，而无法估值的物品保持未知，绝不变成 0。PV 只读取已发布市场快照，不会启动采集器。物品 NBT 在服务端转换，模组中没有 Hypixel API Key。

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
| 26.1.2 | 0.155.2+26.1.2 或更高兼容版本 | `QCloudy_Addition-0.3.10+26.1.2-Beta.jar` |
| 26.2 | 对应 26.2 的 Fabric API | `QCloudy_Addition-0.3.10+26.2-Beta.jar` |

还需要 Fabric Loader 0.19.3 或更新版本、Java 25。Mod Menu 可选。实例 `mods` 文件夹中只放一个可运行 JAR；不要把 `-sources.jar` 当成模组安装。

使用 `O`、Mod Menu、`/qca` 或 `/qc` 打开 QCA。`/qca`、`/qc`、`/qshard`、`/cake` 与 `/centurycakeeffect` 都是本地客户端命令。

## Release 更新提醒

Beta 0.3.10 是首个公开包含此检查器的版本。Release 更新提醒永久开启，不属于任何设置卡片。Alpha 构建绝不会访问更新地址；Beta 与 Release 构建会在第一次进入世界后异步检查，每个客户端进程最多向 QCloudy 稳定版 Release manifest 发送一次 HTTPS 请求。只有比本地基线更新、且包含当前 Minecraft 版本精确 JAR 的稳定 Release，才能触发一次 Toast 和一条本地聊天消息；Alpha 与 Beta 绝不会作为更新目标。消息只链接到 QCloudy 下载页与更新日志页。QCA 不会下载、安装、替换或启动模组文件。

请求不发送 Minecraft 用户名、UUID、服务器地址、Profile、模组列表、玩法数据、遥测标识、Token 或 Cookie。普通 HTTPS 仍会让网站服务器看到连接 IP 与 `QCloudy_Addition/<版本>` HTTP User-Agent。网络或校验失败会静默停止，本次客户端进程内不会重试。

## 纯客户端边界

QCA 读取客户端已经收到的 Tab/计分板/聊天/标题文字、已打开菜单、本地背包、已加载实体/方块，以及玩家打开相应 QCA 界面时明确发出的只读档案/市场查询。它不会自动移动、点击、战斗、钓鱼、捕捉、Fusion 或循环重连；也没有遥测、自动下载/安装更新器或隐藏区块请求。模组中没有 Hypixel API Key，也不会直接请求需要认证的 Hypixel Profile 路由；只使用固定的 `https://api.qcloudy.net` 转换数据源（禁止跳转、限制响应大小）和上面披露的稳定版 Release manifest。档案请求会让 QCloudy 服务器看到连接 IP、QCA User-Agent 和被查询玩家/Profile，但不会发送 Minecraft 会话凭据、服务器地址、模组列表、聊天、坐标、Cookie 或遥测标识。

永久可用的本地 `/th` 与 `/helia` 只会在玩家输入这些快捷命令时发送 `warp torrhus` 与 `chapter torrhus`；Century Cake 续效果操作只会在玩家点击对应聊天操作时发送 `visit northwestcloudy`。另外，玩家单独开启的组队/聊天工具可以在各自总开关、子开关、发送者范围、精确解析、玩家解析与冷却门控全部允许后，发送文档列出的 Party、私信、Stream、坐标、地牢与 Kuudra 指令。这些工具不会模拟点击、移动玩家或使用物品。

## 兼容性与免责声明

QCloudy_Addition 不依赖 SkyHanni、Skyblocker、Firmament、BabyZombieAddons、Feesh、JEI 或 Mod Menu。可选适配器使用能力探测而不是精确版本白名单，但这无法让未公开的第三方内部结构变得稳定；无法安全识别的结构会被跳过。

使用任何 Minecraft 模组均由玩家自行承担风险。QCloudy_Addition 是独立社区项目，与 Hypixel Studios、Mojang Studios 或 Microsoft 无隶属或背书关系。

- 官网：[qcloudy.net](https://qcloudy.net/)
- 下载：[qcloudy.net/download](https://qcloudy.net/download/)
- 源代码：[GitHub](https://github.com/northwestcloudy/QCloudy-Addition)
- 问题反馈：[GitHub Issues](https://github.com/northwestcloudy/QCloudy-Addition/issues)
- Wiki：[GitHub Wiki](https://github.com/northwestcloudy/QCloudy-Addition/wiki)
