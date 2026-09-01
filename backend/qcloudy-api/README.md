# QCloudy API v1

QCloudy Addition 的独立 FastAPI 后端。它集中缓存 Hypixel 玩家档案，并把 Bazaar、活动拍卖和最近成交转换为含明确语义的价格数据。Mod **不会**携带 Hypixel API Key；Key 只保存在这台服务器上。

English summary: a deployable, cache-first backend for QCA Player Viewer and transformed SkyBlock market data. It is not a general Hypixel API proxy.

## 安全与合规边界

- 玩家、Profiles、Museum、Garden 端点由服务端使用 `QCA_HYPIXEL_API_KEY`。
- Bazaar、活动 AH、最近结束 AH、物品资源是公开端点，请求中绝不发送 API Key；该边界有自动测试。
- 上游地址和方法写死在 `HypixelUpstream`，没有用户可控 URL，也没有任意代理接口。
- 用户名查询与玩家档案是跨用户共享缓存，避免每个 Mod 客户端重复访问 Hypixel。
- 只按用户主动使用 `/qpv` 或 `//pv` 时读取玩家数据；不持续轮询玩家状态或建立玩家历史追踪。
- 所有认证 Hypixel 请求共用每分钟预算与短时 burst；预算耗尽会快速失败，遇到 429 或明确的 `RateLimit-Remaining: 0` 会进入有界退避断路，而不是继续重试消耗 Key。
- API Key 不写入仓库、日志或客户端。生产环境通过权限为 `0600` 的 systemd 环境文件注入。
- 部署模板关闭 Nginx/Uvicorn access log，并丢弃该 Nginx vhost 会附带完整 URI 的 request-scoped error log，避免把包含玩家名/UUID 的 PV 路径保存成访问历史；技术错误日志不得包含完整请求路径、API Key、请求正文或完整上游玩家响应。

参考：[Hypixel 官方 API Reference](https://api.hypixel.net/) 与 [API Policy](https://developer.hypixel.net/policies)。生产公开服务应使用经过 Hypixel 审核的 Production application/key。

## 数据流

```text
QCA Mod ──HTTPS──> FastAPI
                    ├─ shared profile cache ──> Hypixel authenticated endpoints
                    ├─ BZ collector (60 s) ──> public Bazaar endpoint
                    ├─ AH collector (120 s) ─> all public active-auction pages
                    └─ ended collector (30 s) ─> public last-60-seconds endpoint
                                              └─ SQLite sales + coverage gaps
```

Redis 是可选的共享快照/缓存层；Redis 不可用时自动退回单进程内存缓存。SQLite 是成交样本与 coverage gap 的持久层。
进程内缓存同时受过期时间、LRU 条目数和 UTF-8 字节预算约束；过期项会主动清除，唯一查询不会在内存中永久累积。默认上限可通过 `QCA_CACHE_MEMORY_MAX_ENTRIES` 与 `QCA_CACHE_MEMORY_MAX_BYTES` 下调。

## v1 API

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/health` | 进程存活 |
| `GET` | `/ready` | SQLite、缓存层和市场采集状态 |
| `GET` | `/v1/pv/{target}?profileId=<32hex>` | 主 PV 快照；Museum/Garden 按需，Market 只读已发布快照做背包估值 |
| `GET` | `/v1/pv/{uuid}/{profileId}/museum` | 仅在 UI 打开 Museum 时加载 |
| `GET` | `/v1/pv/{uuid}/{profileId}/garden` | 仅在 UI 打开 Garden 时加载 |
| `POST` | `/v1/market/prices` | 最多 256 个 `{itemId, variantKey?}` 的批量价格 |
| `GET` | `/v1/market/bazaar/shards?side=instant_buy\|instant_sell` | Shard Planner 价格映射 |
| `GET` | `/v1/market/status` | 采集器状态 |

交互文档：`/docs`；ReDoc：`/redoc`；机器规范：`/openapi.json`。仓库中的 `openapi.json` 可用 `python scripts/export_openapi.py` 重新生成。

### PV 主响应契约

- `schemaVersion` 固定为 `1`。
- 顶层：`partial`、`identity`、`profiles`、`selectedProfileId`、`sources`、`sections`。
- source 状态：`fresh/stale/private/not_found/not_requested/error`。
- section 状态：`available/stale/private/not_found/not_loaded/error`。
- 所有时间是 Unix epoch milliseconds。
- 背包 Base64/gzip NBT 在后端安全解码为 `slot/count/itemId/displayName/rarity/extraAttributes/variantKey` 摘要；解析失败只返回大小和状态，不回传巨型 Base64。
- `sections.market` 从已发布的 BZ/AH/ended 快照计算 `pricedItems/unknownItems/instantSellNetWorth/estimatedNetWorth/perItem`；它绝不因一次 PV 请求触发上游市场采集，市场未 ready 时主 PV 仍成功并标记 `partial`。
- `misc` 包含 Farming 与尚未归类的 player/profile/member 数据，但全部经过同一递归深度、条目数、字符串长度与响应总量预算投影；不会复制无界原始对象。
- Museum 先验证 Profile 成员关系，再只选择所查询 UUID 对应的一个 member 并做有界投影；不会向一个玩家返回其他 co-op 成员的 Museum 数据。

错误固定为：

```json
{"schemaVersion":1,"error":{"code":"...","message":"...","retryable":false}}
```

### 价格语义

- `instantBuyPrice`：现在从 Bazaar 买入时支付的近似价格；来自 Hypixel `quick_status.buyPrice`。
- `instantSellPrice`：现在向 Bazaar 快速卖出时收到的近似价格；来自 `quick_status.sellPrice`。
- `lowestBin`：当前最低 BIN 挂单，不是已成交价。
- `robustListingPrice`：同一 variant 最低最多 5 个 BIN 的中位数，减少单个离群挂单影响。
- `sales24h/sales7d.median`：服务实际观察到的结束拍卖中位数；`coverageComplete=false` 时不可宣称覆盖完整。
- 非 BIN 的 `starting_bid` 不进入 LBIN。Variant key 由 NBT `ExtraAttributes` 的稳定子集生成；NBT 失败时明确标为 `fallback`。

## 缓存与采集周期

| 数据 | fresh / 抓取 | stale 技术兜底 | 备注 |
|---|---:|---:|---|
| 玩家名 → UUID | 72 h | 72 h | 不存在 15 min；不另设更长 stale 窗口 |
| Hypixel player | 1 h | 24 h | Key 端点 |
| 全部 SkyBlock profiles | 1 h | 24 h | Key 端点 |
| Museum | 6 h | 24 h | 按需加载 |
| Garden | 12 h | 24 h | 按需加载 |
| Hypixel item resources | 每 6 h 主动刷新 | 14 d 技术缓存 | 公共端点；仅静态物品元数据 |
| Bazaar | 60 s | 10 min | 公共端点 |
| 完整活动 AH | 120 s | 15 min | 公共端点；一致后原子发布 |
| Recently ended AH | 30 s | upstream 仅 60 s | `auction_id` 去重，SQLite 保留 30 d |

活动 AH 先读 page 0 的 `lastUpdated/totalPages/totalAuctions`，并发抓完所有页，每页必须一致，最后再读一次 page 0；版本、页数、总量或唯一 UUID 数量有任何变化，本轮整体丢弃，继续使用上一个快照。

## 本地开发

要求 Python 3.11+：

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements-dev.txt
cp .env.example .env
.venv/bin/pytest
.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8765
```

不要把真实 Key 写入 `.env.example` 或提交 `.env`。完整 systemd、Nginx 和上线步骤见 [`deploy/README.md`](deploy/README.md)。

## 已知限制

- Variant fingerprint 是第一版稳定子集，不等于完整的 SkyHanni/Firmament 估值规则；新型宠物、属性、染色、皮肤和特殊升级需要持续补测试夹具。
- 已成交历史从服务首次连续运行后才开始建立；在覆盖完整一个查询窗口前，`coverageComplete` 必须为 `false`。
- 当前部署只支持单个 Uvicorn worker。Redis 可以共享缓存和 collector 锁，但**不会**共享进程内的 Hypixel 认证令牌桶/429 断路状态；启用多 worker 或多实例前，必须先实现 Redis 原子认证限流，不能仅靠填写 `QCA_REDIS_URL` 水平扩容。
- 第一版 PV 已解码主要 inventory NBT；新增且尚未分类的字段可出现在 `misc` 的有界投影中，但深度、条目数、字符串和总响应预算都受限，不会把原始巨型数据直接发给 UI。
