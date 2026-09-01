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
