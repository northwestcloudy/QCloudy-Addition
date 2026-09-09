# QCloudy Dungeon 快速查看与市场隐私说明

本文说明 QCA 的 Dungeon Player Quick View 与 Shard Planner 市场请求；完整客户端/规则边界仍以 `COMPLIANCE_zh_CN.md` 为准。

## 什么时候发出请求

只有权威确认客户端位于 Hypixel SkyBlock、Dungeon Quick View 开启，并且收到 Dungeon Finder 宣布新玩家加入 dungeon group 的精确消息时，QCA 才请求 Dungeon 数据；每次只查询刚加入的玩家。为了在排队前取得正确楼层，它只会读取玩家已经打开、由取消发布控件证明属于本机的 Party Finder 条目；不会请求或检查其他队伍档案、轮询 Party Finder、点击菜单或建立玩家历史。若另行确认且默认关闭的入队总开关与这份自己的 F1–F7/M1–M7 楼层规则至少一项开启，QCA 会通过 Hypixel 官方 Mod API 先请求一次新鲜 PartyInfo；只有确认 FAIL 后才再请求一次最终快照，因此每名新成员最多两次 PartyInfo 请求。Entrance 与只靠计分板识别的楼层绝不能开始自动操作判定。Shard Planner 只在玩家明确加载价格时请求有界价格快照。

## 模组会发送什么

目标固定为 `https://api.qcloudy.net`。请求包含新加入玩家的 Minecraft 名称，以及自己的 Party Finder 条目或本地计分板可识别时的发布/排队楼层 ID。服务器与正常网络基础设施可以看到连接 IP 和 QCA User-Agent。模组不会发送 Minecraft 会话 Token、Microsoft 凭据、Hypixel API Key、当前服务器地址、模组列表、聊天记录、坐标、背包上传、Cookie 或遥测/设备标识。官方 PartyInfo 请求只存在于 Minecraft/Hypixel Mod API 连接中，其 UUID/角色成员表不会上传给 QCloudy。

Profile 卡继续保留 `CLICK HERE TO KICK THE PLAYER OUT`；玩家真实点击后，Minecraft 聊天点击事件会向服务器发送 `/party kick <已校验玩家名>`。另外，玩家明确确认的入队设置可在确认 FAIL 后发送 `party kick <已校验新成员>`。F1–F7 与 M1–M7 每层分别设置最低本层完成次数、重复职业、最快时间上限、平均 Secrets 下限、历史最高 Magical Power 下限、Wither Blade、Terminator、Golden Dragon 与 Ender Dragon；规则和自动操作总开关全部默认关闭，Entrance 无规则。创建发布时已在队伍里的玩家与普通/手动加入玩家均可信，只作为 DUPE 职业上下文，不会被本功能查询或踢出。PASS 只输出 Profile；UNKNOWN 输出 Profile 与原因并绝不踢人；FAIL 先完整输出全部确认失败项与同时存在的具体证据错误，随后仅在新鲜官方 PartyInfo 仍证明本机是队长、目标 UUID 仍在队内，且自己的发布条目、楼层、规则、会话/世界与单次动作均未变化时，才在同一客户端判定回合发送指令。该指令只发给 Minecraft 服务器，不发给 QCloudy；QCA 也不会声称执行成功。

## 处理与保留时间

源码中的 QCloudy 服务使用服务器端应用 Key 解析新成员名称，读取对应 Hypixel player 与 SkyBlock Profiles，选择已选 Profile（否则选择最近可见 Profile），只投影聊天卡与规则证据所需字段，然后返回有界响应。名称/UUID 缓存 72 小时，无效名称负缓存 15 分钟；Dungeon player 与 Profiles 新鲜期为两分钟，仅在上游技术故障时可使用最多十分钟的旧值。私密、缺失、过旧、身份/楼层不符、Profile 不确定，以及背包/宠物覆盖不完整全部保持 UNKNOWN，不转换为 0；网络/传输失败也不能授权指令。

规则证据会绑定解析后的 UUID、所选 Profile、请求/返回楼层、新鲜度与来源覆盖。版本 2 使用该 selected Profile 的 `member.dungeons.secrets`，除以同一 Profile 明确的 F1–F7/M1–M7 完成次数；Entrance、聚合总数、账号 Achievement 与 blood-mob 补偿均排除。数据缺失/异常或 0 次完成时保持 UNKNOWN，确认 Mage 新人则跳过该规则。Alpha 9 后端源码不会随 Mod 构建自动部署，完整流程也没有在已登录 Hypixel 的实服验证。版本 1 响应会让平均 Secrets 保持 UNKNOWN；没有受支持证据时只能展示/UNKNOWN。

可运行模组合并相同的进行中请求，只在进程内保留成功结果 60 秒；会话变化时取消未完成工作，不把远程玩家快照写入 `config/qcloudy_addition.json`。

Bazaar 快照约每 60 秒刷新，技术旧值上限十分钟；active AH 约每两分钟刷新，旧值上限十五分钟。去重后的 ended-auction 市场样本大约保留 30 天；它们是市场成交，不是 QCA 玩家查询历史。SQLite 只保存这些市场样本，不保存 Dungeon Quick View。生产模板关闭 Nginx 与 Uvicorn access log，避免把路径中的玩家名保存成浏览历史。

## 安全边界

Hypixel 应用 Key 只存在服务器环境中，绝不会打进 JAR 或返回客户端。服务只开放固定转换路由，不是任意 Hypixel 代理。模组强制 HTTPS、拒绝跳转和来源变化，并限制请求时间与响应大小。运维日志可以保留有界技术状态，但不得包含玩家查询、API Key、请求正文或完整上游响应。自动操作保护与本地 PartyInfo 不会放宽这些 HTTP 边界，也不会增加 QCloudy 遥测路径。
