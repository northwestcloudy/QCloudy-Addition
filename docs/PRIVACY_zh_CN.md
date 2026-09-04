# QCloudy Dungeon 快速查看与市场隐私说明

本文说明 QCA 的 Dungeon Player Quick View 与 Shard Planner 市场请求；完整客户端/规则边界仍以 `COMPLIANCE_zh_CN.md` 为准。

## 什么时候发出请求

只有客户端位于 Hypixel、该功能开启，并且收到 Dungeon Finder 宣布新玩家加入 dungeon group 的精确消息时，QCA 才请求 Dungeon 数据；每次只查询刚加入的玩家。它不会浏览 Party Finder 列表、轮询队伍或建立玩家历史。Shard Planner 只在玩家明确加载价格时请求有界价格快照。

## 模组会发送什么

目标固定为 `https://api.qcloudy.net`。请求包含新加入玩家的 Minecraft 名称，以及本地计分板可识别时的当前排队楼层 ID。服务器与正常网络基础设施可以看到连接 IP 和 QCA User-Agent。模组不会发送 Minecraft 会话 Token、Microsoft 凭据、Hypixel API Key、当前服务器地址、模组列表、聊天记录、坐标、背包上传、Cookie 或遥测/设备标识。

玩家点击 `CLICK HERE TO KICK THE PLAYER OUT` 后，Minecraft 聊天点击事件会向服务器发送 `/party kick <已校验玩家名>`。任何查询结果都不能自动触发该命令。

## 处理与保留时间

QCloudy 服务使用服务器端应用 Key 解析新成员名称，读取对应 Hypixel player 与 SkyBlock Profiles，选择已选 Profile（否则选择最近可见 Profile），只投影聊天卡所需字段，然后返回有界响应。名称/UUID 缓存 72 小时，无效名称负缓存 15 分钟；Dungeon player 与 Profiles 新鲜期为两分钟，仅在上游技术故障时可使用最多十分钟的旧值。私密或缺失保持明确状态，不转换为 0。

可运行模组合并相同的进行中请求，只在进程内保留成功结果 60 秒；会话变化时取消未完成工作，不把远程玩家快照写入 `config/qcloudy_addition.json`。

Bazaar 快照约每 60 秒刷新，技术旧值上限十分钟；active AH 约每两分钟刷新，旧值上限十五分钟。去重后的 ended-auction 市场样本大约保留 30 天；它们是市场成交，不是 QCA 玩家查询历史。SQLite 只保存这些市场样本，不保存 Dungeon Quick View。生产模板关闭 Nginx 与 Uvicorn access log，避免把路径中的玩家名保存成浏览历史。

## 安全边界

Hypixel 应用 Key 只存在服务器环境中，绝不会打进 JAR 或返回客户端。服务只开放固定转换路由，不是任意 Hypixel 代理。模组强制 HTTPS、拒绝跳转和来源变化，并限制请求时间与响应大小。运维日志可以保留有界技术状态，但不得包含玩家查询、API Key、请求正文或完整上游响应。
