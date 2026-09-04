# QCloudy API v1

QCloudy Addition 的独立 FastAPI 后端，为 Dungeon Player Quick View 提供一个有界响应，并继续提供 Shard Planner 使用的市场快照。Mod **不会**携带 Hypixel API Key；Key 只保存在服务器上。

English summary: a cache-first backend for QCA's Dungeon newcomer quick view and transformed SkyBlock market data. It is not a general Hypixel API proxy.

## 安全与合规边界

- Dungeon Quick View 只在客户端收到精确的 Dungeon Finder 新成员加入消息后查询该玩家；后端不会浏览 Party Finder 列表，也不会轮询玩家历史。
- 玩家与 SkyBlock Profiles 由服务端使用 `QCA_HYPIXEL_API_KEY`。Bazaar、活动 AH、最近结束 AH 与物品资源使用 Hypixel 公共端点，绝不携带 Key。
- 上游地址和方法固定在 `HypixelUpstream`，没有用户可控 URL 或任意代理接口。
- 所有认证请求共用每分钟预算与短时 burst；预算耗尽或收到 429 时进入有界退避，不持续消耗 Key。
- API Key 不写入仓库、日志或客户端。生产环境应通过权限为 `0600` 的 systemd 环境文件注入。
- 部署模板关闭会记录完整玩家路径的访问日志。技术日志不得包含 API Key、请求正文或完整上游玩家响应。
- Kick 不属于 API 行为。客户端仅在玩家点击聊天卡底部操作时发送 `/party kick <player>`，服务端不会自动决定或执行踢人。

参考：[Hypixel 官方 API Reference](https://api.hypixel.net/) 与 [API Policy](https://developer.hypixel.net/policies)。公开生产服务应使用经过 Hypixel 审核的 Production application/key。

## 数据流

```text
Dungeon Finder newcomer message
  └─ QCA Mod ── one bounded HTTPS request ──> FastAPI
                                               ├─ shared short player/Profile cache
                                               └─ authenticated Hypixel endpoints

Shard Planner ── bounded HTTPS request ───────> published Bazaar snapshot
Market collectors ────────────────────────────> public Bazaar/AH endpoints
```

Redis 是可选的共享缓存层；Redis 不可用时退回有条目数和字节预算的单进程内存缓存。SQLite 只持久化市场成交样本和 coverage gap，不保存 Dungeon Quick View 卡片或玩家历史。

## v1 API

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/health` | 进程存活 |
| `GET` | `/ready` | SQLite、缓存层与市场采集状态 |
| `GET` | `/v1/dungeons/quick-view/{target}?floor=F7` | 新成员 Dungeon Quick View 的完整有界响应；floor 可省略或为 `E/F1-F7/M1-M7` |
| `POST` | `/v1/market/prices` | 最多 256 个 `{itemId, variantKey?}` 的批量价格 |
| `GET` | `/v1/market/bazaar/shards?side=instant_buy|instant_sell` | Shard Planner 价格映射 |
| `GET` | `/v1/market/status` | 市场采集器状态 |

旧的 `/v1/pv/*` 与 `/v1/market/tooltip-prices` 已删除。交互文档：`/docs`；ReDoc：`/redoc`；机器规范：`/openapi.json`。仓库规范可用 `python scripts/export_openapi.py` 重新生成。

## Dungeon Quick View 契约

- `schemaVersion` 固定为 `1`。
- 单个响应包含：玩家 identity、Catacombs 等级/XP、五职业等级/XP、指定层数 runs/fastest、Secrets 总数/全地牢 run 平均、Magical Power、四件护甲、Wither Blade/Terminator、Golden Dragon/Ender Dragon 与新鲜度。
- Catacombs 与职业 XP 保留精确数值；客户端仅将等级显示到一位小数，并把 XP 放入悬停。
- 护甲按 Helmet、Chestplate、Leggings、Boots 输出。后端从有限 NBT 摘要提供格式化名称与最多 80 行 lore，供客户端构造 Minecraft 原生 item hover。
- 武器和宠物使用 `present/absent/missing` 三态。只有数据源完整时才把未找到的物品标记为 absent；解码或字段不可用时显示 missing。
- 当前楼层最快时间取该层可用的 S+、S 或普通完成时间中的最小正值。Secrets 平均值使用总 Secrets 除以普通与 Master 所有楼层完成次数之和，排除聚合 `total` 字段。
- 私密、缺失与异常数据不会变成 0；API 使用统一错误响应，客户端仍可生成全字段 `Missing` 卡片。

统一错误格式：

```json
{"schemaVersion":1,"error":{"code":"...","message":"...","retryable":false}}
```

## 缓存与市场

| 数据 | fresh / 抓取 | stale 技术兜底 | 备注 |
|---|---:|---:|---|
| 玩家名 → UUID | 72 h | 72 h | 不存在 15 min |
| Dungeon player | 2 min | 10 min | 只在上游技术失败时使用旧值 |
| Dungeon SkyBlock Profiles | 2 min | 10 min | 与 player 并行加载 |
| Hypixel item resources | 每 6 h 主动刷新 | 14 d | 公共端点 |
| Bazaar | 60 s | 10 min | 公共端点 |
| 完整活动 AH | 120 s | 15 min | 版本一致后原子发布 |
| Recently ended AH | 30 s | upstream 仅 60 s | SQLite 去重并保留 30 d |

客户端还会合并相同的进行中 Quick View 请求，并仅缓存成功响应 60 秒。一次 Dungeon Quick View 不会启动或加速任何市场采集器。

## 本地开发

要求 Python 3.11+：

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements-dev.txt
cp .env.example .env
.venv/bin/pytest
.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8765
```

不要把真实 Key 写入 `.env.example` 或提交 `.env`。部署说明见 [`deploy/README.md`](deploy/README.md)。当前只支持一个 Uvicorn worker；Redis 尚未共享认证令牌桶/429 断路状态，多 worker 前必须实现 Redis 原子认证限流。
