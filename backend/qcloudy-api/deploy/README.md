# Production deployment (Debian + systemd + Nginx)

以下示例让 Uvicorn 只监听 `127.0.0.1:8765`，公网只能通过 HTTPS Nginx 访问。

## 1. 安装服务文件

先把发布包内容直接解压/复制到 `/opt/qcloudy-api`。该目录下应直接看到 `app/`、`requirements.txt` 与 `deploy/`，不能再多套一层 `backend/qcloudy-api`。全新 Debian 服务器还需要先安装 Python 虚拟环境组件，然后执行：

```bash
sudo apt-get update
sudo apt-get install -y python3-venv
sudo useradd --system --home /opt/qcloudy-api --shell /usr/sbin/nologin qcloudy-api
sudo mkdir -p /opt/qcloudy-api /var/lib/qcloudy-api
sudo chown -R qcloudy-api:qcloudy-api /opt/qcloudy-api /var/lib/qcloudy-api
sudo -u qcloudy-api python3 -m venv /opt/qcloudy-api/.venv
sudo -u qcloudy-api /opt/qcloudy-api/.venv/bin/pip install -r /opt/qcloudy-api/requirements.txt
```

随后创建 `/etc/qcloudy-api.env`：

```text
QCA_ENVIRONMENT=production
QCA_HYPIXEL_API_KEY=<server-side-production-key>
QCA_HYPIXEL_AUTHENTICATED_BUDGET_PER_MINUTE=100
QCA_HYPIXEL_AUTHENTICATED_BURST=10
QCA_HYPIXEL_AUTHENTICATED_429_BACKOFF_SECONDS=60
QCA_CACHE_MEMORY_MAX_ENTRIES=4096
QCA_CACHE_MEMORY_MAX_BYTES=134217728
QCA_SQLITE_PATH=/var/lib/qcloudy-api/qcloudy-api.sqlite3
QCA_REDIS_URL=
QCA_SCHEDULER_ENABLED=true
```

上面的受支持默认部署是单个 Uvicorn worker，并使用有界进程内缓存。若已经单独安装、保护并监控 Redis，可把空值替换为 `QCA_REDIS_URL=redis://127.0.0.1:6379/0` 来共享缓存和 collector 锁。Redis **不会**共享 `HypixelUpstream` 的认证令牌桶或 429 断路状态；在实现 Redis 原子认证限流之前，不得增加 Uvicorn workers 或横向运行多个服务实例。

```bash
sudo chown root:root /etc/qcloudy-api.env
sudo chmod 600 /etc/qcloudy-api.env
sudo cp /opt/qcloudy-api/deploy/qcloudy-api.service /etc/systemd/system/qcloudy-api.service
sudo systemctl daemon-reload
sudo systemctl enable --now qcloudy-api
sudo systemctl status qcloudy-api --no-pager
curl --fail http://127.0.0.1:8765/health
```

不要用开发用 Personal/Development Key 支撑公开 Mod 用户。公开上线前在 Hypixel Developer Dashboard 注册并获批 Production application；Key 只保存在服务器环境文件中。三个 `QCA_HYPIXEL_AUTHENTICATED_*` 值共同限制认证请求：每分钟总预算、瞬时 burst，以及收到 429 后的默认退避断路时间。它们是服务端安全上限，不应设置得高于已获批应用额度。

## 2. Nginx

把 `deploy/nginx-api.qcloudy.net.conf` 放进 Nginx 的 `http {}` include 目录。替换证书路径；也可以先在宝塔创建 `api.qcloudy.net` 站点并申请证书，再将 `location /` 合并进站点配置。

```bash
sudo nginx -t
sudo systemctl reload nginx
curl --fail https://api.qcloudy.net/health
curl --fail https://api.qcloudy.net/ready
```

防火墙不开放 8765。只开放 80/443 和受控管理端口。

## 3. 更新与回滚

### 2026-10-16 库存完整性补丁（宝塔文件面板）

修复 `wardrobe_equipped_slot`、`sacks_counts` 普通元数据被误当成 NBT 容器，导致 `weapons.complete=false` 的问题。只跳过形状符合预期的这两个元数据字段；真实容器缺失、坏 NBT、未知字段解析失败和截断仍会阻止 `ABSENT`。证据版本保持 2，Mod 无需重新构建。

本次唯一需要替换的生产文件是 `/opt/qcloudy-api/app/dungeon_service.py`。补丁 `release/qcloudy-api-inventory-coverage-fix-20261016.zip` 内只有 `app/dungeon_service.py`：

1. 在宝塔打开 `/opt/qcloudy-api/app`，复制现有 `dungeon_service.py` 为 `dungeon_service.py.before-inventory-fix-20261016.bak`；已有同名备份时另取名称，不覆盖旧备份。
2. 回到 `/opt/qcloudy-api`，上传补丁 ZIP 并解压到当前目录，仅覆盖 `app/dungeon_service.py`。确认没有生成 `app/app`，不选择清空目录。
3. 保留 `/etc/qcloudy-api.env` 中现有 Production Key 与限流设置、`.venv`、数据库和其他应用文件。本补丁没有依赖变化，也不需要修改 Nginx 或执行 `daemon-reload`。
4. 在宝塔终端依次执行下面的语法检查、重启和验证；语法检查失败时先恢复备份，不继续重启。

```bash
/opt/qcloudy-api/.venv/bin/python -m py_compile /opt/qcloudy-api/app/dungeon_service.py
sudo systemctl restart qcloudy-api
sudo systemctl is-active qcloudy-api
curl -sS -i https://api.qcloudy.net/ready
curl -sS -i 'https://api.qcloudy.net/v1/dungeons/quick-view/NorthwestCloudy?floor=F7'
```

进程应为 `active`，业务响应仍应为 evidence v2。四个必需容器都存在且成功解码、也没有其他容器失败或截断时，`weapons.complete` 应为 `true`，`containersFailed` 不再包含这两个普通元数据字段。若有真实容器问题，`complete=false` 仍是正确的保护结果；找到的武器仍为 `PRESENT`，未找到且覆盖完整才为 `ABSENT`。原始 player/profile 数据可能命中默认两分钟缓存，但新的完整性判定会立即应用于这些缓存数据。只有验证新的上游抓取及其时间时，才需要等待缓存刷新；Redis 原始缓存不会随重启清空。`/ready` 不验证 Hypixel Key。

若服务启动失败，查看 `sudo journalctl -u qcloudy-api -n 100 --no-pager` 的错误，恢复刚才的 `dungeon_service.py` 备份并重启。无需回退或重新生成 API Key。

### Alpha 9 平均 Secrets 补丁（宝塔文件面板）

本次后端改动只有一个生产文件：

```text
/opt/qcloudy-api/app/dungeon_service.py
```

仓库根目录生成的 `release/qcloudy-api-0.3.10-alpha9-evidence-v2-patch.zip` 内部已经保留 `app/dungeon_service.py` 这层路径。在宝塔中执行：

1. 打开 `/opt/qcloudy-api/app`，把现有 `dungeon_service.py` 下载留存，或复制成 `dungeon_service.py.alpha8-backup`。不要删除 `.venv`、其他 `app` 文件、`/etc/qcloudy-api.env` 或 `/var/lib/qcloudy-api`。
2. 回到 `/opt/qcloudy-api`，上传上述 ZIP。不要把 ZIP 上传到 `/opt/qcloudy-api/app`，否则会多套一层 `app/app`。
3. 在 `/opt/qcloudy-api` 右键 ZIP 并选择解压到当前目录。确认覆盖目标只有 `/opt/qcloudy-api/app/dungeon_service.py`；不要选择“清空目标目录”或删除原目录后再解压。
4. 打开宝塔“终端”，依次执行下面的停止、语法检查、启动与验证命令。只上传或解压不会让正在运行的 Python 进程自动读取新代码。

```bash
sudo systemctl stop qcloudy-api
/opt/qcloudy-api/.venv/bin/python -m py_compile /opt/qcloudy-api/app/dungeon_service.py
sudo systemctl start qcloudy-api
sudo systemctl is-active qcloudy-api
curl --fail https://api.qcloudy.net/ready
curl --fail 'https://api.qcloudy.net/v1/dungeons/quick-view/NorthwestCloudy?floor=F7'
```

最后一个响应必须能找到 `"requirementsEvidence":{"version":2`，并且 `averageSecrets.scope` 应为 `SELECTED_PROFILE_SECRETS_F1_F7_M1_M7_RUNS`。若仍是版本 1，说明服务没有加载新文件；若服务启动失败，先执行 `journalctl -u qcloudy-api -n 100 --no-pager` 查看错误，再把备份文件改回 `dungeon_service.py` 并重启。测试玩家恰好没有可用 runs/Secrets 时，`averageSecrets` 可以是 `UNAVAILABLE`，但 evidence 版本仍必须为 2。

不要把 `.env.example` 覆盖到 `/etc/qcloudy-api.env`，也不需要修改 Nginx、域名、HTTPS 证书、SQLite 或重装 `.venv` 依赖。本补丁没有依赖文件变化。

### 通用更新流程

```bash
sudo systemctl stop qcloudy-api
# 替换 /opt/qcloudy-api 中的应用文件，但保留 /etc/qcloudy-api.env 和 /var/lib/qcloudy-api
sudo -u qcloudy-api /opt/qcloudy-api/.venv/bin/pip install -r /opt/qcloudy-api/requirements.txt
sudo systemctl start qcloudy-api
curl --fail https://api.qcloudy.net/ready
```

先保存上一版本目录或镜像；代码回滚不应删除 SQLite。数据库当前只有向后兼容的 `CREATE TABLE IF NOT EXISTS`，未来 schema migration 必须单独备份并演练。

## 4. 运维检查

```bash
journalctl -u qcloudy-api -n 100 --no-pager
systemctl is-active qcloudy-api
curl --fail https://api.qcloudy.net/v1/market/status
```

监控至少应覆盖：HTTP 5xx、Hypixel 429、认证请求预算/退避断路、collector `lastSuccessAt`、AH snapshot age、ended coverage gap、SQLite 磁盘空间和 Redis 可用性。部署模板必须保持 Nginx `access_log off`、该 vhost 的 request-scoped `error_log` 丢弃策略与 Uvicorn `--no-access-log`，避免把路径里的玩家名/UUID 建成查询历史；保留的服务错误日志不得打印 Key、完整上游玩家响应、请求正文或 PV 请求路径。若改为保留 Nginx 错误日志，必须先在反向代理层完成 URI 去标识化，不能直接恢复默认 request line 日志。
