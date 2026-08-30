# QCloudy_Addition

QCloudy_Addition 是适用于 Minecraft 26.1.2 与 26.2 的纯客户端 Fabric 模组。它专注于更清晰的 SkyBlock 地图、简洁的目标 HUD、被动视觉辅助、宠物信息和背包质量优化。模组以英文为默认界面，并保留 Hypixel 发来的原始名称。当前开发构建为适配 Minecraft 26.1.2 的 Alpha 36；最新稳定版仍为适配 Minecraft 26.1.2 与 26.2 的 Release 0.3.9。

## 快速入口

- [功能总览](docs/FEATURES_zh_CN.md)
- [实现与数据流](docs/IMPLEMENTATION_zh_CN.md)
- [Modrinth 中文简介](docs/MODRINTH_DESCRIPTION_zh_CN.md)
- [当前 0.3.9 Release 更新日志](CHANGELOG_zh_CN.md)
- [版本与产物命名规则](docs/VERSIONING_zh_CN.md)
- [验收与验证](docs/VALIDATION_zh_CN.md)
- [合规说明](docs/COMPLIANCE_zh_CN.md)

默认语言为英文。按 `O`（可在“控制 → 按键绑定 → QCloudy_Addition”中改键）或输入 `/qca`、`/qc` 可打开客户端设置，并随时切换为简体中文。只有名称未被其他客户端命令占用时才会注册对应别名；这些命令只打开本地界面，不会发送给 Hypixel。

语言选项只翻译 QCA 自己的界面标签。Hypixel 地点、任务、宠物、皮肤、配件、物品以及玩家重命名的 HOTM 配置均保留客户端收到的原始名称；例如 `Terminator` 不会被改写成中文名称。

## 仅追踪 Release 的更新提醒

QCA 的更新提醒永久开启，刻意不放进任何设置卡片。Alpha 构建不会安排或发起更新请求；Beta 与 Release 构建会在第一次进入世界后异步执行检查，每个客户端进程最多向 `https://www.qcloudy.net/assets/data/release-manifest.json` 发送一次 HTTPS `GET`。只有通过完整校验、通道精确为 `Release`、稳定版序号高于本机构建基线，并且存在唯一一项与当前 Minecraft 版本完全匹配的可运行 Release JAR 时，才会判定存在更新。Beta、Alpha、格式错误、Minecraft 版本不匹配、只有 Sources、匹配项重复或链接不可信的结果都会安全忽略，绝不会成为更新目标。

确认存在新 Release 后，QCA 只显示一次原版 Toast 和一条本地可点击聊天消息，分别提供 `https://qcloudy.net/download/` 与 `https://qcloudy.net/changelog/`。它不会下载、替换或启动 JAR。请求不会包含 Minecraft 用户名、UUID、服务器地址、Profile、模组列表、玩法状态、遥测标识或认证 Token；与普通 HTTPS 请求相同，网站服务器仍可看到连接 IP 与 `QCloudy_Addition/<版本>` HTTP User-Agent。

## 统一 SkyBlock 模组控制——概念测试

> **谨慎使用：**“统一设置编辑”和“统一 HUD 编辑”目前都是概念测试，默认关闭且尚不稳定。第三方模组更新后，个别字段可能无法识别。请谨慎开启、提前备份配置，并在对应模组自己的设置/HUD 编辑器中核对每次写入；第三方模组原生编辑器始终具有最终权威。

QCA 可以作为统一的功能与 HUD 编辑入口，直接控制自己的功能，以及已安装的 **SkyHanni**、**Skyblocker**、**Firmament**、**BabyZombieAddons**、**Feesh** 中能够安全识别的内容。“统一设置编辑”和“统一 HUD 编辑”是两个相互独立且默认关闭的总开关。每一次提供方扫描都必须经过第二次确认：首次开启没有有效会话快照的编辑器，以及每一次点击 **Refresh**，都会先显示与设置或 HUD 范围对应的确认窗口。取消首次确认会保持总开关关闭；取消 Refresh 会保留上一份有效快照。重启后即使总开关仍为开启，也不会静默扫描。确认后才会打开可视化进度页面。设置页只报告可管理设置，HUD 页只报告可管理 HUD。普通打开设置菜单不会扫描，未安装的模组不会显示，两个开关都关闭时会取消待处理工作并释放会话快照。适配不使用精确版本白名单，因此提供方更新后，仍兼容的已识别分支可以继续工作，未知或变化分支则逐项跳过。Release 0.3.9 分别发布 Minecraft 26.1.2 与 26.2 构建。

分类首先采用提供方原生路径和已经验证的规则；只有仍未分类的功能才会交给小型、确定性的本地元数据分类器。它使用固定权重和置信度门槛，不下载模型、不联网，也无权判断两个功能是否等价或写入提供方数值。

多个兼容模组存在完全相同的功能时，QCA 只显示一张统一卡片。右键卡片后，第一项用于选择提供方，下面直接显示所选模组中能够安全编辑的原生设置。开启卡片会启用所选实现，并只关闭其他模组中完全等价的实现；用途不同的价格、利润、Tooltip 或追踪功能不会被错误合并。所有数值都写入对应模组的实时配置，并通过该模组自己的保存路径落盘；QCA 不会在模组未加载时直接改写它的配置文件。

原有 **编辑 HUD** 界面也会显示所选兼容提供方中已经启用的 HUD，并标注模组名称。拖动或缩放第三方 HUD 时只更新预览，松开鼠标才写回其原生位置/缩放。当前概念测试仅安全支持已校验的布尔、枚举、有边界数值、位置和缩放；自定义颜色对象、复合快捷键对象等提供方专属复杂编辑器暂时保留在对应模组自己的界面中。

一级分类顺序为：**通用、地图、物品与菜单、战斗、地牢、Slayer、挖矿、种地、砍树、钓鱼、狩猎、Rift、活动**，但没有任何 QCA 或已发现提供方功能的分类会直接隐藏。Safari 是狩猎下级组，Garden 是种地下级组，Crimson Isle/Kuudra 是战斗下级组；钓鱼上钩功能使用“咬钩提示”下级组，不再重复显示“钓鱼 → 钓鱼”。每个功能只有一个归属，只出现一次。

## 功能分类

### 通用

- **手动重连**：在连接失败和断线界面加入一个原版尺寸的“重新连接”按钮。正常连接尝试开始时就记录目标，所以首次加入失败后也能使用。只有玩家点击按钮才会重新连接；没有倒计时、循环、重试计数、命令或自动加入。

### 地图

- **矮人矿洞地图**：使用本次提供的单层 12 区域总览图和实时红色玩家箭头。整张背景采用同一套连续的大致 X/Z 映射；Y 和计分板子地点名称都被排除，因此位于 The Mist 上方的桥梁不会再让箭头跨区域跳动。地图点位保持英文。
- **冰川隧道分层地图**：低层、中层和高层图片使用完全相同的坐标边界；在 Y=126 与 Y=143 切层，切换后玩家箭头位置保持一致。地图点位固定使用英文原名，并在生成阶段自动检测、避让相邻标签，防止文字重叠。

### 挖矿

- **任务与粉尘追踪**：读取客户端已经收到的 `Commissions:` 与 `Powders:` Tab Widget。每个任务显示完整名称和独立进度条，不再使用省略号；进度条会大致与当前最宽的完整任务名右端齐平，不再横跨整个固定面板。普通字体和粗体都用实际渲染样式测宽，并额外保证完整进度数值不会穿出边框。进度默认以一位小数百分比显示，可在功能二级设置中切换为“当前数值/目标数值”。服务器直接提供的数值优先，否则仅根据已记录的任务目标换算；未来未知任务会安全回退为百分比而不会伪造数值。HUD 还会默认显示 `HOTM: <配置名>`，从山心配置/Loadouts 菜单中读取并缓存玩家当前选择的原始名称，也可在二级设置中关闭。适用于矮人矿洞、水晶矿洞、冰川隧道和 Mineshaft，并分别显示秘银、宝石和冰川粉尘。

### Crimson Isle

- **阵营任务追踪**：位于 Crimson Isle 时，只读取客户端已经收到的 `Faction Quests:` Tab Widget，完整显示任务原名、需求数量及服务器给出的 `✖`/`✔` 状态，不省略也不翻译。该功能独立分类、默认开启，并与不会同时出现的挖矿追踪共用 HUD 位置和外观。

### 砍树

- **Torrhus Chapter 与资源**：在同一个会自动换行且绝不省略的 HUD 中显示当前 Helia Chapter、完整任务名、进度、Forest Whispers、Desert Whispers、Forest Essence、Safari Essence、Sweep 与 Forest Fortune。Tab 与计分板按两个独立的有限来源解析，后面的 `SB Level` 分数不会再串成 Chapter 任务；同时支持真实的 `Helia's Chapters` 总览、章节详情物品栏和短时间内分行收到的聊天状态。已确认的绝对数值按 Minecraft 账号和客户端收到的 SkyBlock Profile 分开保存，重连后仍存在；旧配置中误存的非 Chapter 任务会在载入时修复，只有观察到更新数值时才改写。聊天中的明确获取提示才进行有限增量累加。Safari Essence 在 Critter Safari 内不重复显示。已完成数量、Chapter 总进度和下一项解锁默认关闭。
- **Tree Critter 计时**：默认开启且可单独关闭。读取离玩家最近的 Tree Protection Order 可见名称牌 `Critter in: 26m 47s`，把服务器实际倒计时加入综合 Hunting HUD；不自行按物品猜测倒数，因此可准确兼容 Fun-Sized（60m）、Family-Sized（30m）、Jumbo（15m）、Behemoth（立即出现）、Honeycomb Artifact 加速、Honey Serendipity 立即触发及未来服务器修正。
- **Miria Contest**：解析客户端收到的计分板/Tab 档位行（例如 `COMMON with 151` 与 `Uncommon requires +99`），只在综合 Hunting HUD 中显示下一档、准确差值与预计 Safari Ticket；不再向右侧计分板注入内容，也不重复显示计分板已有的竞赛倒计时。
- **Benefactor 与 Tree Gift**：将有限范围的 Tab/计分板、已经打开的 Forest/Desert Temple 菜单和玩家本人收到的准确捐赠消息合并为 Benefactor 状态；支持多日捐赠、剩余时间、寺庙对应效果、到期处理和账号/Profile 持久保存，新捐赠也不会被仍未刷新的旧菜单立刻覆盖。十种稀有 Tree Gift 奖励可分别开关：读取玩家本人精确奖励汇总的 hover，也读取同一个经过个人贡献与汇总证明的有限 Gift 区块内精确 BONUS 行；兼容被聊天压缩模组取消显示但客户端已经收到的原始消息。附近玩家单独出现的公开掉落行不会触发。

### 钓鱼

- **钓鱼上钩提示音**：默认关闭，用于提示 Hypixel 水钓与岩浆钓鱼的短暂收杆窗口。功能优先使用直接归属本地玩家的 Fishing Hook；对 owner 关联缺失的 Hypixel 岩浆鱼钩，则只在真实抛竿后的短窗口内进行安全关联，然后要求鱼钩附近出现精确可见的 `!!!` 标记。每根鱼钩只播放一次内置 Ciallo OGG；独立音量滑块为 0–100%，默认 64%。不会自动抛竿或收杆。其下级组名称为“咬钩提示”，不再出现重复的“钓鱼 → 钓鱼”。

### 狩猎

- **Beeheemoth 与 Lasso 提示**：只按参考模组使用的 scale-9 Bee 特征识别 Beeheemoth；原版轮廓默认开启，并接入统一 RGB/HSV 颜色选择器。黄色信标标在首次看见的生成位置，在玩家进入 10 格、收到自己捕捉 Beeheemoth 的确认，或实体消失时关闭。与该 scale-9 实体空间关联的 Bee 声音（包括短暂生成/捕捉窗口）拥有独立开关和音量，默认开启、64%；其他位置的普通 Bee 不受影响。独立的 Lasso `REEL` 提示音仅在本地玩家可见状态首次变为精确 `REEL` 时响一次，默认开启、64%。
- **Critter 行为辅助**：针对特殊捕捉机制显示中央提示，并在收到捕捉确认后进行短时间、有界去重。
- **Fairy Soul 点位**：这是唯一一个跨 Torrhus/Safari 的狩猎功能，两组坐标可分别开关，但功能卡只出现在“狩猎”。

### Safari

- **Safari Run Dashboard 与 Critterdex**：统计本轮 Shards、时间、Ticket Tier，并按官方 37 种 Critter 显示四个 Biome 的进度和当前 Biome 完整的已捕捉/缺失名称。
- **Cold、Doomspiral、Critter、Snoozle 与 Wumpa 辅助**：默认在 Cold 高于 80/90 时两次预警；超过第一档时立即扫描最近的已加载篝火并显示红色信标，Cold 开始下降时关闭；持有 4 个 Soothing Incense 时提示；按 Shard 品质色高亮真实 Critter 实体。Wumpa 的八项组队前置同时接受本人和队友 Loot Share 捕捉，生成后折叠为 `Wumpa：已生成`，路线改为跟踪真正的 Ravager 身体。独立 Snoozle 功能用默认绿色、可自定义 RGB 的半透明表面覆盖附近 `Cobbled Deepslate + Tuff` 可撞墙。Armor Stand 捕捉道具会被排除，避免再次描边支架身体。Wumpa 路线默认关闭，其余功能默认开启。
- **Sparkling、Floor Drop 与 Quest Item**：只依据收到的聊天、可见名称/实体、已加载的附近 String 方块和本地背包显示中央预警与 HUD；Sparkling 轮廓颜色可自定义。
- **Safari Belt 详情**：把本地观察到的 Cavern/Forest/Haunted/Icy 四项 Milestone 等级与物品实际说明中的属性增益嵌入 Safari Belt 提示；支持标题和 lore 分行的菜单格式，按账号/Profile 保存，只在收到更高的确认等级时更新。

“砍树”和“狩猎”是独立一级设置分类，Safari 只作为“狩猎”内的可折叠下级功能组；Fairy Soul 点位只放在“地图”中。每张功能卡只有唯一归属，不会跨分类重复。相关预警都使用屏幕中央标题；每种预警分别拥有默认开启、64% 音量的独立音效与 0–100% 滑条，“通用”另有总静音。综合 HUD 拥有独立保存的外观、缩放与位置。

### 战斗

- **末影龙高亮**：当计分板地点为 The End 或 Dragon's Nest 时，将末影龙加入原版轮廓渲染管线；轮廓色可选红、黄、青、绿、紫或白。
- **Power Orb 与 SOS 消失提醒**：四种 Power Orb 使用本人精确消失聊天行；Warning/Alert/SOS Flare 使用精确物品 ID，并由成功放置音效确认后开始本地三分钟生命周期。重新放置 Flare 会重置完整三分钟计时，并使旧到期时间失效。放置失败、实体卸载、距离和增益范围不会触发提醒。Power Orb、Flare、中央大字、音效和音量可分别设置；音效默认 64%。
- **Century Cake 效果过期提醒**：使用一个默认开启的总开关追踪全部 20 种蛋糕效果的真实世界 48 小时倒计时；同批过期会合并提醒，并显示中央大字和带下划线的聊天操作。`/cake` 与 `/centurycakeeffect` 打开本地计时菜单；只有点击聊天操作后才执行 `/visit northwestcloudy`。

### 宠物

- **当前宠物 HUD**：用召唤、收回和 Autopet 提示立即更新，再以客户端收到的 `Pet:` Tab Widget 校正。HUD 只用 QCA 内置且已验证的 Profile 构造普通 player head，不再写入合成 `petInfo`，因此其他模组无法把 HUD 头像替换成无关物品模型。动态皮肤家族的每一帧都会归回正确皮肤，包括 Baby Spinosaurus 已发布的全部变体。头像由 Minecraft 原生物品渲染器按整数 2× 清晰绘制；宠物、皮肤、经验和配件文本完整测量，粗体也不会溢出或省略。“当前等级经验”和“到满级进度”默认开启；满级只隐藏后者，不会隐藏宠物配件。通过 Pets 菜单、Tab 或已收到聊天确认的配件会按宠物保存在本地，重登后继续显示。皮肤名称和 Ancient Golden Dragon 装饰溢出等级默认开启。内置当前 87 种宠物配件资源，可选“图标＋名称”（默认）、“仅图标”或“仅名称”。

### 物品与菜单

- **Attribute Shard Fusion Guide**：受 JEI 信息结构启发、完全离线的 320 种当前 Bazaar Shard 浏览器。可按原始英文名称、Shard ID、属性/效果、品质、分类、家族、Skill、生物类型或获取文字搜索。**详细信息**显示 Wiki 已记录的完整效果和所有自然/Fusion 获取方式；**合成来源**显示能产出目标 Shard 的全部有序输入组合，其中也包括 Queen Bee 这类同时拥有自然来源的 Shard；**可合成内容**显示所选 Shard 能继续合成什么。配方卡保留输入顺序，显示数量、可选输出、普通/特殊产量和 Pure Reptile。Epic 使用 Minecraft 深紫色（`§5`），品质、属性、分类、生物类型与获取方式使用对应游戏语义颜色；鼠标悬停于可点击 Shard 文字时，文字会变深并添加下划线。目录与专属图标在发布前离线生成并随模组打包；客户端已经收到的原生 `ItemStack` 仍优先用于材质包显示。本地 `/qshard [英文查询]` 只打开本地界面，不发送聊天或服务器命令。QCA 运行时不访问 Wiki/API/图标服务，也不会自动执行 Fusion。
- **Shard Planner**：完整保留原 Guide，新增目标数量、完整多步 Fusion Tree、候选路线、Materials Only 汇总、输入/输出独立筛选、可编辑每小时获取速度、Shard 详情、可拖动 Fusion Lines，以及本地保存的 Hunting Box 仓库。Ironman 只使用狩猎速率；Normal 的“最快”可以比较狩猎与购买时间，“最便宜”必须存在兼容的可选 Skyblocker 客户端价格缓存。QCA 自己绝不下载 Bazaar 价格；SkyHanni/Firmament 当前没有稳定公开的跨模组价格 API，因此不会作为价格提供者。没有价格提供者时，价格路线会明确不可用，其余全部离线/速率功能仍正常工作。

### 聊天

- **聊天偷窥**：按住用户设置的按键或组合键，在不打开聊天界面的情况下临时显示完整高度的聊天历史。偷窥时鼠标滚轮默认翻聊天记录；二级设置可改为继续切换快捷栏。为避免按键冲突，偷窥键默认未绑定。

### HUD 外观

- 左键点击功能卡片即可启用或关闭，左侧蓝条是唯一的启用状态提示；右键仍会进入该功能的完整二级设置页，但卡片不再重复显示右键提示
- 每个 HUD 分别保存背景透明度/颜色、边框开关/宽度/颜色、标题颜色、粗体与文字阴影
- 所有可编辑颜色统一使用带色轮、亮度、R/G/B 滑条和预设色的颜色选择器；每一个背景颜色都额外提供“透明”选项
- 每个 HUD 分别以 50–200% 缩放；在编辑器中拖住边框或四角即可像桌面窗口一样改变大小
- 左下角“编辑 HUD”会打开只包含当前位置/状态下实际已加载 HUD 的编辑器；拖动改位，点右下角小齿轮进入专项设置
- 松开鼠标时立即保存该 HUD 的位置和缩放，重启游戏后继续沿用
- 界面打开动画默认开启，也可关闭
- 安装 Mod Menu 后，可从 Mod Menu 直接进入 QCA 设置

设置页采用受 BLC 信息层级启发、但没有复制其素材或界面代码的紧凑结构：顶部只保留“功能”，左侧只显示当前确实拥有功能的一级分类，并使用默认收起的下级组与可搜索功能卡片。没有 QCA 或已发现提供方功能的“地牢”等分类会完全隐藏，不再打开空页面；钓鱼功能使用“咬钩提示”下级组。HUD 位置继续从左下角“编辑 HUD”进入。功能卡片不重复绘制右上角开关和右下角右键提示，二级设置也不重复一级功能开关。侧栏没有“全部”分类。

物品与菜单工具包括 Attribute Shard Fusion Guide、物品时间戳、光标位置记忆、AOTE/AOTV 声音自定义和聊天偷窥。所有 QCA 热键都直接在原有二级设置行内进入等待输入，不再跳转到独立捕获菜单；支持键盘、鼠标 1–5/侧键以及 Ctrl、Shift、Alt、Cmd/Super 组合，等待输入时按 `Esc` 会像原版一样清空绑定。

**AOTE/AOTV 传送声音**不会默认静音。普通传送与 Etherwarp 分别默认保留原声，也可独立改成紫颂果传送、末影人传送、紫水晶清响、经验球、末地传送门填充或潜影贝传送；自定义音量使用 10–200% 滑条，音调使用 50–200% 滑条。HUD 透明度/缩放和光标记忆时间等跨度大的数值也统一使用 Windows 风格拖动条，松开即保存；少量离散档位仍保留按钮切换。

## 安装

1. 安装 Minecraft 26.1.2 与 Fabric API 0.155.2+26.1.2，或 Minecraft 26.2 与 Fabric API 0.154.2+26.2；Release 0.3.9 还需要 Fabric Loader 0.19.3 或更新版本及 Java 25。
2. 将文件名末尾与当前 Minecraft 版本完全一致的 `QCloudy_Addition-*.jar` 放入实例 `mods` 文件夹；Mod Menu 为可选依赖。
3. 启动游戏后按 `O` 或输入任一本地设置命令进行配置。

## 从源码构建

安装 JDK 25 后运行 `bash tools/build_all_versions.sh`。脚本会按 `gradle.properties` 中选择的通道构建：Alpha 会测试并生成 Minecraft 26.1.2 的可运行 JAR 与 Sources JAR；Beta 与 Release 会测试并在 `release/` 生成 Minecraft 26.1.2 和 26.2 的两组文件。项目已包含固定为 Gradle 9.6.1 的 Wrapper 与 Fabric Loom 1.17.17；参考模组不是构建或运行依赖。宠物 Profile 元数据由本地 NEU item-repo 快照离线生成并直接打包进 QCA；玩法、Shard、Wiki、图标与价格数据仍全部使用本地/离线来源，QCA 自己唯一的运行时网页请求是上面说明的有限 Release manifest 检查。QCA 不需要 Firmament。

## 安全边界

发布版不包含 `sendChat`、Hypixel Mod API 订阅、WebSocket、遥测、运行时 Shard 数据请求、宏、自动移动或区块请求代码。QCA 自己唯一的 HTTP 路径是永久开启、每进程最多一次的 Release manifest 检查；Alpha 会在安排请求前直接返回，并且该检查无权下载或安装更新。普通 HUD 只读取客户端已收到的数据；`/qshard`、`/cake` 与 `/centurycakeeffect` 只打开本地界面，不发送任何内容。永久可用的本地 `/th` 与 `/helia` 只会在玩家输入时分别发送准确内容 `warp torrhus` 与 `chapter torrhus`，等同手动输入 `/warp torrhus` 与 `/chapter torrhus`。Century Cake 提醒中的带下划线续效果文字只会在玩家实际点击后发送精确 `/visit northwestcloudy`。文档列出的可选组队/聊天工具还可以在各自总开关、子开关、发送者范围、解析和冷却条件全部满足后发送对应服务器指令；它们不会模拟点击、移动玩家或使用物品。

Hypixel 明确说明所有模组均由玩家自行承担使用风险，未明确列出的功能也不代表获得许可。使用前请阅读 [docs/COMPLIANCE_zh_CN.md](docs/COMPLIANCE_zh_CN.md) 和最新的 [Hypixel Allowed Modifications 说明](https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications)。

完整功能说明：[docs/FEATURES_zh_CN.md](docs/FEATURES_zh_CN.md)

功能实现与数据流：[docs/IMPLEMENTATION_zh_CN.md](docs/IMPLEMENTATION_zh_CN.md)

Modrinth 中文发布描述：[docs/MODRINTH_DESCRIPTION_zh_CN.md](docs/MODRINTH_DESCRIPTION_zh_CN.md)

当前 0.3.9 Release 变化：[CHANGELOG_zh_CN.md](CHANGELOG_zh_CN.md)

发布检查清单：[docs/PUBLISHING_CHECKLIST_zh_CN.md](docs/PUBLISHING_CHECKLIST_zh_CN.md)

更新日志：[CHANGELOG_zh_CN.md](CHANGELOG_zh_CN.md)

发布验收报告：[docs/VALIDATION_zh_CN.md](docs/VALIDATION_zh_CN.md)

2026-08-04 崩溃分析：[docs/CRASH_ANALYSIS_2026-08-04_zh_CN.md](docs/CRASH_ANALYSIS_2026-08-04_zh_CN.md)
