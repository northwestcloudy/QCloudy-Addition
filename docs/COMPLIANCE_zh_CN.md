# 客户端与规则边界

## 运行时数据流

| 功能 | 客户端输入 | 本地输出 | 对外发送 |
|---|---|---|---|
| 矮人矿洞地图 | 仅玩家 X/Z/yaw；投影明确排除 Y 与子地点文字 | 固定示意 PNG 与实时大致位置箭头 HUD | 无 |
| 冰川隧道地图 | 玩家 X/Y/Z/yaw、解析到的地点 | 固定分层 PNG 与箭头 HUD | 无 |
| 挖矿任务/粉尘/HOTM 配置 | 已收到的 Tab 文本及玩家已打开菜单中的物品名/说明 | 文字与进度条 HUD、缓存的当前配置名 | 无 |
| 钓鱼上钩提示音 | 直接归属本地玩家的 Fishing Hook，或在真实使用钓竿后关联的新加载本地/owner 为空鱼钩；附近精确收到的 `!!!` ArmorStand | 每根鱼钩一次内置本地声音 | 无 |
| Crimson Isle 任务 | 已收到的 `Faction Quests:` Tab 文本 | 文字 HUD | 无 |
| Torrhus Chapter/资源/Contest/Benefactor | 已收到的计分板、Tab、聊天与已打开 HOTF/菜单文字 | 自动换行 HUD、本地档位计算、中央标题 | 无 |
| Tree Critter 计时 | 最近已加载实体中严格匹配 `Critter in: <时间>` 的显示名称 | 综合 Hunting HUD 中的一行 | 无 |
| Beeheemoth 辅助 | 已加载 Bee 类型/scale/UUID、本地玩家位置、已收到捕捉确认、附近已收到的 Bee 声音 | 可调原版轮廓/信标与本地声音音量缩放 | 无 |
| Lasso REEL 提示音 | 本地玩家手持 Lasso、已收到的绳索持有者关系、附近精确 `REEL` ArmorStand | 状态切换时一次本地音效 | 无 |
| Tree Gift 预警 | 本人奖励汇总 `SHOW_TEXT` 与同一经过归属证明的 Gift 区块精确行，包括被取消显示的聊天 | 中央标题与本地音效 | 无 |
| Safari Dashboard/Critterdex | 已收到的聊天、Tab/计分板、本地会话时钟 | 综合 HUD | 无 |
| Sparkling/Wumpa/行为辅助 | 已收到捕捉/生成聊天、可见自定义名称/实体运动、玩家本地移动与死亡 | 中央标题、本地音效、条件/阶段 HUD、可选原版轮廓 | 无 |
| Cold/篝火安全辅助 | 已收到的 Cold 文本与已加载区块中的篝火 Block Entity | 中央标题、本地音效、最近篝火信标 | 无 |
| Doomspiral 条件提示 | 本地背包内容 | 持有 4 个以上 Soothing Incense 时中央提示与本地音效 | 无 |
| Warden 可抓捕预警 | 已加载 Warden 类型/位置/姿态/客户端年龄及收到的本地玩家延迟 | 140 tick 就绪转换时一次中央大字与本地音效 | 无 |
| Fairy Souls | 官方 Wiki 固定坐标与已解析的当前岛屿 | 可选粉色信标 | 无 |
| Safari Critter 品质高亮 | 客户端可见的实体自定义名称与内置官方品质表 | 原版实体轮廓色 | 无 |
| Wumpa 路线预测 | 可见 Wumpa 位置/移动与本地方块碰撞射线 | 可选红线 | 无 |
| Snoozle 可撞墙覆盖 | 附近已加载的 Cobbled Deepslate/Tuff 方块状态 | 半透明暴露表面覆盖 | 无 |
| Floor Drop/Quest Item | 已加载的附近方块状态与本地背包 | 距离/物品 HUD 与中央标题 | 无 |
| Safari Belt | 已收到的物品 ID/说明和玩家已打开菜单中的物品 | Tooltip 与按账号/Profile 保存的本地配置缓存 | 无 |
| 末影龙高亮 | 已收到的末影龙实体和地点 | 原版轮廓渲染状态 | 无 |
| Power Orb 与 SOS 消失提醒 | 精确收到的本人 Power Orb 消失聊天、精确本地 Flare 物品 ID、收到的成功放置音效与本地单调时间 | 中央大字与独立配置的本地音效 | 无 |
| Century Cake 到期追踪 | 精确收到的 48 小时首次生效/刷新聊天行与本地系统时间 | 本地计时、效果界面、中央/聊天提醒与本地音效 | 只有玩家直接点击带下划线的续效果文字后才执行精确 `/visit northwestcloudy`；绝不自动执行 |
| 宠物 HUD | 已收到的聊天与 Tab 文本 | 文字 HUD | 无 |
| 聊天偷窥 | 玩家真实按住按键及客户端已收到的聊天历史 | 临时改变本地聊天渲染与滚轮目标 | 无 |
| 自动接受组队 | 已收到的组队邀请聊天，以及本地缓存的好友/白名单数据 | 可选的本地接受判定 | 开启后，发送者符合所选类别或白名单时发送 `party accept <发送者>` |
| 私信组队申请 | 精确收到的英文私信关键词 `!p`、`!party` 或 `!invite` | 可选的本地请求匹配 | 开启后发送 `party invite <发送者>` |
| 快速组队指令 | 精确收到、以已识别 `!` 别名开始的英文 Party Chat 行；本机玩家名、队伍成员名与需要时的本地坐标 | 可选别名解析、唯一前缀玩家名补全与本地冷却 | 总开关、子开关和触发人范围均允许时，发送下表对应的 Party/Stream/地牢/Kuudra 指令 |
| 组队指令 | 本机输入的已识别 `//` 指令；本机玩家名、队伍成员名与需要时的本地坐标 | 可选别名解析与唯一前缀玩家名补全 | 总开关与子开关允许时发送下表对应指令；未知 `//` 输入原样放行 |
| 快速私信 `!p` | 本机输入 `//invited <玩家>`、`//invited by <玩家>` 或 `//i <玩家>` | 本地玩家名校验 | 开启后发送 `msg <玩家> !p` |
| AOTE/AOTV 声音自定义 | 手持物品 ID 与客户端收到的附近声音事件 | 保留原声，或按设置的音量/音调替换为本地原版声音 | 无 |
| Attribute Shard Fusion Guide | 随模组打包的离线 320-Shard 效果/获取/Fusion JSON 与 320 张本地图标、已经在本地菜单/物品栏收到的可选原生 ItemStack、玩家真实搜索/点击/按键 | 本地详细信息/合成来源/可合成内容界面、Shard 专属离线图标、语义文字颜色与遵循材质包的已观察覆盖 | 无；`/qshard` 是纯客户端界面命令 |
| Shard Planner | 打包配方/速率、本地 Planner 设置、QCloudy API 的有界 Bazaar 快照、玩家亲自打开 Hunting Box 页面中可见的 Shard 数量/lore | 本地路线 Tree、候选、材料汇总、直接配方筛选、详情、Fusion Lines 与按 Profile 仓库 | 玩家明确加载 Planner 价格时，向 `api.qcloudy.net` 异步发送一次有界 Shard 价格 HTTPS 请求；不发送 `/hb`、不点击、不 Fusion |
| Dungeon 玩家快速查看 | 精确收到的 Dungeon Finder 新成员消息、本地排队楼层计分板文字与新成员名称 | 带有界数据和原生悬停的彩色本地聊天卡 | 向固定 `api.qcloudy.net` 发出一个有界异步 HTTPS 请求；模组不直接请求需认证的 Hypixel API。只有玩家真实点击带下划线操作才发送 `party kick <已校验新成员>` |
| 配置 | 可改绑本地按键、本地 `/qca`/`/qc` 与鼠标输入 | JSON 配置文件 | 无 |
| Release 更新提醒 | 构建内嵌的通道/版本/Minecraft/Release 基线元数据，以及公开稳定版 manifest | 确认存在更新且匹配当前版本时显示一次 Toast 与一条本地可点击聊天消息 | Alpha：无；Beta/Release：每个客户端进程最多向 `https://www.qcloudy.net/assets/data/release-manifest.json` 发送一次 HTTPS `GET`；绝不下载更新 |
| 统一模组控制 | 对已安装 SkyHanni、Skyblocker、Firmament、BabyZombieAddons、Feesh 运行时对象的按需只读能力扫描；固定本地元数据分类；扫描后玩家在 QCA 中的真实点击/拖动 | 扫描进度、独立设置/HUD 数量、提供方选择、原生设置、原生 HUD 位置/缩放/对齐变化 | 无；扫描/分类只读，后续编辑也只通过对应提供方自己的保存/update 路径写入本地客户端配置 |
| 兼容性缺失报告 | 最近一次完成的本地扫描快照中的已识别配置/HUD 结构 | 按提供方分组的本地“设置/HUD 编辑/分类”缺失报告 | 无；不会调用 setter 或保存路径 |
| 手动重连 | 上一次正常 `ConnectScreen` 目标与玩家在断线页的明确点击 | 打开新的原版连接界面 | 仅点击后向已记录目标发起一次正常服务器连接 |
| Torrhus 快捷命令 | 玩家主动输入本地 `/th` | 无 | 发送精确内容 `warp torrhus` |
| Helia 快捷命令 | 玩家主动输入本地 `/helia` | 无 | 发送精确内容 `chapter torrhus` |

## 命令与聊天

- 本地设置命令：`/qca`、`/qc`；若其他客户端命令已占用某个根名称，则跳过该别名。它们只打开 QCA 设置，不发送内容。
- 可选提供方集成按能力探测，而不是精确版本白名单。第三方设置与 HUD 分别受两个默认关闭的总开关控制。每一次扫描都必须经过第二次本地确认：首次开启但没有有效快照时，以及每一次 Refresh，都会先明确显示具体扫描范围。取消首次确认会保持总开关关闭；取消 Refresh 会保留已校验快照；重启后恢复为开启的开关不会静默扫描。两个界面共用通过校验的快照，但设置/HUD 数量独立显示；只列出已安装并实际产出可读能力的提供方，两个开关都关闭时停止扫描。分类先用原生/已验证规则，之后才使用固定本地分类器，置信度不足时保持未分类。过程中没有云端模型、提供方下载、HTTP、服务器查询或直接编辑配置文件；之后玩家主动编辑时也只调用所选已安装提供方自己的保存/update 机制。
- 本地 Shard 命令：`/qshard [英文查询]`；打开随模组打包的离线 Attribute Shard Fusion Guide，并可预填本地搜索。它不会发送聊天、服务器命令、数据包、菜单输入或网络请求。
- QCA 不再注册通用档案命令 `//pv` 或 `/qpv`。Dungeon Quick View 没有命令入口，只对 Dungeon Finder 的精确新成员行响应。它绝不自动踢人；`party kick <新成员>` 只存在于玩家真实点击红色下划线操作后的载荷中。
- 本地 Century Cake 命令：`/cake`、`/centurycakeeffect`；只打开本地蛋糕效果菜单。过期提醒中的带下划线链接在玩家点击后发送精确 `/visit northwestcloudy`；未点击时不会发送，计时器也不会自动触发该指令。
- 本地 Torrhus 快捷命令：`/th`；没有设置项且无法关闭。玩家明确输入时，QCA 发送精确内容 `warp torrhus`，等同手动输入 `/warp torrhus`；只有在其他客户端命令已经占用 `/th` 时才跳过注册。
- 本地 Helia 快捷命令：`/helia`；没有设置项。玩家明确输入时，QCA 发送精确内容 `chapter torrhus`，等同手动输入 `/chapter torrhus`；只有在其他客户端命令已经占用 `/helia` 时才跳过注册。
- **自动接受组队**默认关闭。开启后，收到来自本地配置中符合条件的发送者的组队邀请，会发送精确 `party accept <发送者>`。好友类别与白名单均是本地配置；白名单条目优先于类别选择。
- **私信组队申请**默认关闭。它只匹配精确英文私信关键词 `!p`、`!party`、`!invite`；匹配后发送精确 `party invite <发送者>`。同一发送者的重复匹配会在短暂本地冷却内去重。
- **快速私信 `!p`**默认关闭。本机输入 `//invited <玩家>`、`//invited by <玩家>`、`//i <玩家>` 均发送精确 `msg <玩家> !p`。
- **快速组队指令**默认关闭。它只处理 Party Chat 中收到的已识别 `!` 消息；公屏与公会聊天不满足条件。九个子开关默认开启，且每项可单独限制为“仅自己 / 仅其他玩家 / 所有人”。`!warp`/`!w` 共享五秒 `party warp` 冷却；`!allinvite`/`!all`/`!allinv` 共享两秒 `party settings allinvite` 冷却。
- **组队指令**默认开启，九个子开关也默认开启。它通过本机 `//` 指令处理同一批已识别别名；未知 `//` 不拦截。玩家参数可为不区分大小写的精确名称，或唯一的队伍成员名称前缀；前缀有歧义时不会发送指令。

| 别名组 | 精确服务器命令载荷 |
|---|---|
| `!warp`、`!w` / `//warp`、`//w` | `party warp` |
| `!allinvite`、`!all`、`!allinv` / 对应本机 `//` | `party settings allinvite` |
| `!pt`、`!ptme` / `//pt`、`//ptme` | `party transfer <消息发送者或本机玩家>` |
| `!pt <玩家>` / `//pt <玩家>` | `party transfer <玩家>` |
| `!k <玩家>` / `//k <玩家>` | `party kick <玩家>` |
| `!sc`、`!sendcoords`、`!c` / 对应本机 `//` | `pc x: <x>, y: <y>, z: <z>` |
| `!pp <玩家>` / `//pp <玩家>` | `party promote <玩家>` |
| `!stream`、`!st`、`!s` / 对应本机 `//` | `stream` |
| Stream 别名后接任意纯十进制数字 `<n>` | `stream open <n>` |
| Stream 别名后接 `c`、`close` 或 `off` | `stream close` |
| `!fe`、`!f0` / `//fe`、`//f0` | `joininstance CATACOMBS_ENTRANCE` |
| `!me`、`!m0` / `//me`、`//m0` | `joininstance MASTER_CATACOMBS_ENTRANCE` |
| `!f1` … `!f7` / 对应本机 `//` | `joininstance CATACOMBS_FLOOR_ONE` … `joininstance CATACOMBS_FLOOR_SEVEN` |
| `!m1` … `!m7` / 对应本机 `//` | `joininstance MASTER_CATACOMBS_FLOOR_ONE` … `joininstance MASTER_CATACOMBS_FLOOR_SEVEN` |
| `!t1` … `!t5` / 对应本机 `//` | `joininstance KUUDRA_NORMAL`、`KUUDRA_HOT`、`KUUDRA_BURNING`、`KUUDRA_FIERY`、`KUUDRA_INFERNAL` |

- `sendChat` 调用：**没有**。
- 自动生成的聊天内容：仅来自单独开启的“快速私信 `!p`”功能的精确 `msg <玩家> !p` 载荷；不会使用其他 `sendChat` 调用。

## 网络与自动化审计

QCA 不包含 Hypixel Mod API、WebSocket、遥测、坐标共享服务、自动下载/安装更新器、宏、模拟输入、自动点击/移动或方块交互。可运行模组中没有 Hypixel API Key，也不会直接请求需要认证的 Hypixel Profile 端点。QCloudy 有界 API 客户端与下述 Release 提醒客户端是 QCA 自有的两条网页访问路径；对外命令和聊天载荷仅限于上方逐项列出的快捷命令、点击动作、组队/聊天工具与玩家点击“重新连接”后的一次普通服务器连接。重连没有倒计时、重试循环、后台尝试或自动加入。

Dungeon/市场客户端只接受普通 HTTPS 端口的 `https://api.qcloudy.net`，禁止跳转，连接超时五秒、请求超时十五秒、响应上限 4 MiB。Dungeon 请求会让 QCloudy 服务器看到连接 IP、QCA User-Agent、新成员名称与可选排队楼层；它不会发送 Minecraft 会话凭据、Cookie、Hypixel API Key、服务器地址、模组列表、聊天记录、坐标或遥测标识。后端持有应用 Key，只提供固定转换端点而不是通用代理，会合并/缓存上游请求、限制解码 NBT 大小并保留私密/缺失状态。模组只在进程内缓存成功 Quick View 60 秒；远程玩家数据和价格历史不会写入 QCA 本地配置。

Release 更新提醒永久开启，没有功能卡片或配置项。Alpha 构建会在安排任何工作或打开 HTTP 连接前直接返回。Beta 或 Release 构建在第一次进入世界后安排一次检查，延迟五秒后异步向 `https://www.qcloudy.net/assets/data/release-manifest.json` 发送 HTTPS `GET`；整个客户端进程最多一次。连接超时为五秒、请求超时为十秒、禁止重定向、只接受 HTTP 200，并把响应限制为 128 KiB。失败只记日志，不向玩家报错，也没有重试循环。若确认结果等待展示时玩家已断线，则保留到下一次进入世界再显示。

只有同时满足以下条件的 manifest 才能通过：schema 版本受支持；`channel` 精确为 `Release`；`releaseSequence` 是大于零且高于本机构建内嵌 Release 基线的整数；版本为三段式且 Tag 精确为 `v<版本>`；唯一匹配项的文件名精确为 `QCloudy_Addition-<版本>+<当前 Minecraft>-Release.jar`；同时带小写 `sha256:<64 位十六进制>` 与允许列表内的 `northwestcloudy/QCloudy-Addition` 官方 GitHub Release 资产链接。Alpha、Beta、格式错误、只有 Sources、重复匹配、Minecraft 版本不符、序号未增加、重定向或不可信链接都不能触发提醒。有效结果只生成一次原版 Toast 和一条本地可点击聊天消息，链接仅指向 `https://qcloudy.net/download/` 与 `https://qcloudy.net/changelog/`；不会获取 JAR、安装、替换文件、启动代码或重启 Minecraft。

请求不包含 Minecraft 用户名、UUID、服务器地址、SkyBlock Profile、模组列表、玩法数据、遥测标识、访问 Token、Cookie 或认证头。它仍是一次普通对外 HTTPS 请求，因此目标服务器与网络基础设施可以看到连接 IP 与 `QCloudy_Addition/<版本>` HTTP User-Agent。响应内容不会作为账号/Profile记录持久保存。

钓鱼上钩提示音优先使用直接归属于本地玩家的已加载鱼钩。为了兼容 owner 关联可能缺失的 Hypixel 岩浆鱼钩，它只在玩家真实使用钓竿后开启有限 40 tick 窗口，排除抛竿前已经存在的全部鱼钩及明确属于其他玩家的鱼钩，然后才接受一根新加载、归属本地或 owner 为空的鱼钩。选中鱼钩仍存在时的第二次真实使用会被判定为收杆，不能重新打开播放门。此后只扫描该鱼钩周围四格并精确匹配收到的 `!!!` ArmorStand，每根鱼钩最多播放一次内置本地提示音。回调会原样放行真实使用动作，不会自动抛竿、收杆、点击、移动玩家，也不发送命令或额外数据包；空闲时不会运行较大范围扫描。

Shard Fusion Guide 与 Planner 都是只读本地资料。320 项目录、规范化 Wiki 效果/获取摘要、合成规则、速率基线、320 张 Shard 专属 PNG、物品模型与映射都在发布前生成并打包进 JAR；生成器使用 [Attributes 表格](https://hypixelskyblock.minecraft.wiki/w/Attributes)、[Attribute Fusion 规则](https://hypixelskyblock.minecraft.wiki/w/Attribute_Fusion)、[官方 Bazaar 产品列表](https://api.hypixel.net/v2/skyblock/bazaar) 与已审核 MIT 许可 SkyShards 数据。运行中的 QCA 没有访问这些来源或图标服务的代码路径。搜索、规划、切换焦点、打开详细信息/合成来源/可合成内容、使用历史、解析内置图标、渲染已经收到的原生 ItemStack 或拖动 Fusion Lines 节点，都不会点击容器、执行 Fusion、选择输出、向服务器发送 `/qshard` 或改变服务器状态。已经收到的原生玩家头继续走 Minecraft 正常渲染管线；QCA 不会额外发起纹理请求。

QCA 不会抓取 SkyHanni、Skyblocker 或 Firmament 的私有字段。Planner 只读取 QCloudy 有界 Shard 价格响应：Bazaar `buyPrice` 明确表示立即买入/获取成本，`sellPrice` 表示立即卖出/清算价值。完整市场来源表示所需已发布组成均可用；`partial` 表示至少一个组成已过时或不可用，但仍有部分可用值。没有可靠价格的物品保持 `unknown`；未知、畸形与非正数价格会被省略，不会改成 0。后端大约每分钟刷新 Bazaar；active AH 必须完成一次版本一致的原子周期；ended auctions 会去重并记录 coverage gap。Dungeon 查询不读取市场快照，也不启动采集器。仓库只在玩家亲自打开后读取精确可见 `Hunting Box` 菜单与 `Owned: N Shards` lore，不发送 `/hb`、不请求另一页、不点击槽位、不读取隐藏背包、不自动执行路线。

独立的统一设置适配器会使用经过能力检查的反射访问已安装模组的实时客户端配置，因为这五个提供方没有共同且稳定的跨模组设置 API。Feesh 设置写入使用其公开委托属性 setter 与 `Settings.save()`；Feesh Overlay 移动使用实时注册表和 `PersistentDataManager`。扫描和编辑均不会调用 Feesh 的 API、聊天、命令、分享或玩法路径。提供方版本号不作为白名单。提供方探测必须由玩家主动确认且按需发生：首次开启但没有有效快照时会先显示第二次确认，每一次 Refresh 也会再次显示；只有确认回调能够创建扫描任务，重启后恢复的开关或启动 tick 都不能绕过。刷新期间上一份快照继续有效，新结果只在校验后发布，两个开关都关闭时取消任务并清除快照。原生/已验证规则先运行，小型固定本地元数据分类器只处理余下内容；不确定功能保持未分类。扫描不会联系模型或服务，也不会写入。只有玩家之后真实编辑时，才可能调用已识别提供方数值 setter 和其原生保存/update 机制。该访问不用于价格、玩法状态、网络通信、隐藏服务器数据或自动化，QCA 也不会直接修改其他模组的配置文件。

Hunting HUD 与追踪器没有任何对外发送路径：不会发送命令/聊天、请求区块、修改计分板 Objective、选取目标、投掷工具/Capsule、移动玩家或交互 Floor Drop、篝火、Critter、墙体、Fairy Soul。进度记忆只是本地 JSON，以本地账号 UUID 和收到的 Profile 标签为键，只保存客户端曾经收到的 Chapter/资源/Safari Milestone/Benefactor 值，并在观察值改变时更新。Chapter 会分别限制 Tab、计分板、已打开菜单及短时收到的聊天块，不扫描任意缓存文字；Benefactor 同样只读取有限的 Tab/计分板/聊天/菜单文字，其到期时间只是对收到时长进行本地计算，不会引发服务器动作。Tree Critter 计时只读取已经加载的实体显示名称，不检测点击、不消耗 Pot，也不合成本地倒数。Beeheemoth 使用指定参考模组相同的 scale-9 已加载 Bee 特征；固定光柱只由本地距离、已收到捕捉确认或实体消失移除，并且只在本地缩放空间相关的 Bee 系声音。Lasso 提示只读取已收到的拴绳关系与附近精确显示文字，然后播放本地声音。Wumpa 组队前置集合由本人锚定捕捉确认和客户端收到的队友 Loot Share 捕捉文字更新；单独的本人 Critterdex 仍排除 Loot Share。生成消息与 8/8 完成共用每轮一个提醒标记，路线只跟踪已加载 Ravager 身体和本地碰撞。Snoozle 覆盖每秒只检查附近已加载方块，拒绝过大或单一材质组件，只渲染本地暴露表面。Warden 就绪只读取有限场地内客户端可见的实体年龄/姿态和本地连接延迟，不修改实体，也不会发出捕捉动作。Tree Gift 使用只发给本地玩家的精确汇总/hover作为归属证明，在收到的 Component/区块内缓冲精确行，并且只在该证明成立后保留 5 秒结束边框后生物窗口；被取消显示的消息仍属于客户端已经收到的数据，附近玩家单独公共行依旧无效。Fairy Soul 光柱只会在收到成功/已经找到确认并通过有限的最近坐标匹配后隐藏。篝火搜索只检查原版已经加载区块中的 Block Entity。Miria 结果只在 QCA 综合 HUD 中显示，侧栏注入与竞赛倒计时重复显示均已删除；`/th` 与 `/helia` 是上方单独记录、必须由玩家输入的快捷命令。

## Hypixel 规则说明

实现严格限制为被动客户端数据与渲染，这可以降低反作弊和交互风险，但不等于获得 Hypixel 官方批准。Hypixel 当前说明强调：所有模组均由玩家自行承担风险；提供明显优势或未明确列出的功能不保证允许。请在使用前阅读最新官方规则，并关闭任何自己不确定的功能：

实体轮廓、信标点位、墙体覆盖与运动预测是本模组规则风险最高的部分，因为它们会让世界信息更容易观察。它们虽然只做被动渲染，但“只渲染”不等于必然允许。因此 Wumpa 路线和 Fairy Soul 信标默认关闭；按用户要求默认开启的 Critter 品质轮廓、Cold 篝火信标和 Snoozle 墙体覆盖也各自提供总开关。

- [Hypixel Allowed Modifications](https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications)
- [Hypixel SkyBlock Rules](https://support.hypixel.net/hc/en-us/articles/4508088842898-Hypixel-SkyBlock-Rules)
