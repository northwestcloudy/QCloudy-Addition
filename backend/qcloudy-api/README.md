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
- Kick 不属于 API 行为。服务端只返回显示数据和有明确完整性边界的判定证据，不决定或执行踢人；客户端是否发送 `/party kick <player>` 仍取决于本地逐层规则、实时 Party 状态、权限和发送前复核。

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
- 单个响应包含：玩家 identity、Catacombs 等级/XP、五职业等级/XP、指定层数 runs/fastest、所选 Profile 的 Dungeon Secrets/F1–F7 与 M1–M7 run 平均、历史最高 Magical Power、四件护甲、Wither Blade/Terminator、Golden Dragon/Ender Dragon 与新鲜度。
- 顶层旧显示字段保持兼容；`identity` 只以新增字段的方式加入原始查询值 `queryName`。
- Catacombs 与职业 XP 保留精确数值；客户端仅将等级显示到一位小数，并把 XP 放入悬停。
- 护甲按 Helmet、Chestplate、Leggings、Boots 输出。后端从有限 NBT 摘要提供格式化名称与最多 80 行 lore，供客户端构造 Minecraft 原生 item hover。
- 旧 Profile 卡片的武器和宠物字段继续使用 `present` 配合 `complete`。只有完整数据才能把未找到的物品解释为 absent；解码、字段或来源不可用时客户端应显示 missing。
- 当前楼层最快时间取该层可用的 S+、S 或普通完成时间中的最小正值。Secrets 平均值使用同一 selected Profile 的 `member.dungeons.secrets`，除以该 Profile 明确的普通 F1–F7 与 Master M1–M7 完成次数之和；Entrance `0`、聚合 `total`、账号 Achievement 与 blood-mob 补偿均不参与。
- 私密、缺失与异常数据不会变成 0；API 使用统一错误响应，客户端仍可生成全字段 `Missing` 卡片。

### `requirementsEvidence` v2

`requirementsEvidence` 是独立于全局 `schemaVersion` 的附加契约。当前版本为 `requirementsEvidence.version = 2`，因此升级该证据结构不会把其他 v1 API 的全局 schema 改成 2。该版本已写入仓库并通过本地测试，**不会随 Mod JAR 自动部署**；生产 API 仍需单独更新并重启。部署过渡期客户端接受证据版本 1 和 2，但版本 1 的平均 Secrets 会被强制视为 UNKNOWN。缺少该字段或版本不受支持时，客户端只能显示旧 Profile，不能据此自动执行踢人。

根字段：

- `version`：证据契约版本，当前为 `2`。
- `fresh`、`fetchedAt`：三个来源是否全部 fresh，以及其中最早的抓取时间。
- `identity`：`queryName`、规范 UUID 和规范玩家名。
- `profile`：所选 Profile 的 `id`、选择方式 `SELECTED|LATEST_SAVE`、以及 `selectionCertain`。只有恰好一个 Profile 被标记为 selected 时才确定；没有 selected 时使用最近保存的 Profile、多个 selected 时只在这些 selected 中选择最近保存的一份，二者都仅供展示，判定证据为 `UNAVAILABLE`。
- `request`：`floor`、`responseFloor`、`floorMatches`，用于把证据绑定到实际请求楼层。
- `sources.identity|player|profile`：每个来源的 `status=fresh|stale`、布尔值 `fresh` 和 `fetchedAt`。

数值证据只使用：

- `KNOWN`：值已取得，且身份、Profile 选择和所有来源满足新鲜度要求。
- `UNAVAILABLE`：字段缺失、没有有效完成时间、没有请求楼层、楼层不匹配、Profile 选择不确定、Profile ID 缺失或任一来源 stale。`UNAVAILABLE` 绝不能转换成数值 0。

具体数值字段：

- `floorCompletions`：`state` 与 `value`，只表示请求楼层的完成次数。
- `fastestCompletion`：`state`、`valueMs`、`kind=ANY_COMPLETION`。它是该层 S+、S 或普通完成记录中的最小正值，不等同于只看 S+ PB。
- `magicalPower`：`state`、`value`、`kind=HIGHEST`。数据源是所选 Profile 的 `highest_magical_power`，表示历史最高值，不是实时当前 MP。
- `averageSecrets`：`state`、`value`、`numerator`、`denominator`、`scope=SELECTED_PROFILE_SECRETS_F1_F7_M1_M7_RUNS` 与 `complete`。分子是 selected Profile 的 `member.dungeons.secrets`，分母只加该 Profile 的 F1–F7/M1–M7 明确完成次数。任一父结构/计数/Secrets 缺失或异常时返回 `AVERAGE_SECRETS_UNAVAILABLE`；分母为 0 时返回 `NO_COMPLETED_DUNGEON_RUNS`，两者均为 `UNAVAILABLE`，不能转换成 0。

武器和宠物证据只使用：

- `PRESENT`：在 fresh、明确选择的 Profile 数据中找到目标。
- `ABSENT`：没有找到目标，并且相应数据源已被确认完整。
- `UNAVAILABLE`：stale、Profile 选择不确定、数据缺失、partial 或解析失败。它不能当作 `ABSENT`。

`weapons` 会分别返回 `witherBlade` 和 `terminator` 的状态，并附带 `complete`、`containersExpected`、`containersPresent`、`containersDecoded`、`containersFailed`、`containersMissing` 与 `truncated`。只有 `inv_contents`、`ender_chest_contents`、`backpack_contents`、`personal_vault_contents` 全部存在，所有返回容器均成功解析且没有截断时，未找到的武器才能成为 `ABSENT`。在 partial inventory 中已找到的武器仍可为 `PRESENT`，但未找到的另一把必须为 `UNAVAILABLE`。

`pets` 会分别返回 `goldenDragon` 和 `enderDragon` 的状态，并附带有效 `complete` 与原始列表完整性 `sourceComplete`。只有 fresh、明确选择的 Profile 返回了完整 pets list 时，未找到的宠物才能成为 `ABSENT`。

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
