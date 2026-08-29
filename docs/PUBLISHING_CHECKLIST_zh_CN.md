# Release 0.3.9 发布检查清单

> 本文件保留 Release 0.3.9 的发布基线。Release 0.3.9 并未包含 Release 更新检查器。下方与检查器相关的条目是留给首个后续 Beta/Release 的前瞻要求，不能据此声称已经发布的 0.3.9 JAR 具备更新提醒。

## 统一字段

| 字段 | 内容 |
|---|---|
| 版本 | `0.3.9` |
| 通道 | Release / 稳定版 |
| Loader | Fabric |
| Minecraft | 26.1.2 与 26.2 |
| Java | 25 |
| 环境 | 仅客户端 |
| 许可证 | LGPL-3.0-or-later |
| 必需 | 对应版本 Fabric API |
| 可选 | Mod Menu；SkyHanni、Skyblocker、Firmament、BabyZombieAddons、Feesh |

建议标题：`QCloudy_Addition 0.3.9 Release`

建议摘要：`适用于 Fabric 的纯客户端 Hypixel SkyBlock 地图、按内容显示的 HUD、宠物、提醒、Century Cake 计时与离线 320-Shard Fusion Lab。`

## 必须显著展示的实验性警告

GitHub、Modrinth、Wiki 与官网发布内容顶部附近都要包含：

> “统一设置编辑”和“统一 HUD 编辑”是实验性概念测试，默认关闭，目前并不稳定。请备份提供方配置，并在对应模组自己的编辑器中核对改动。

## 全新验证

- 每次影响发布的修改后，都使用 Java 25 执行 `bash tools/build_all_versions.sh`。
- 自动测试及两个 Minecraft 目标必须全部通过。
- 对四个归档执行 `jar --validate` 与 `unzip -t`。
- 检查两个可运行 JAR 中的 `fabric.mod.json`：版本必须为 `0.3.9`，Minecraft 范围和 Fabric API 依赖必须正确。
- 检查打包后的 `fabric.mod.json` 是否包含 `contact.homepage`、`contact.sources`、`contact.issues`，以及 Website、Downloads、Source 三项 `custom.modmenu.links`；确认 HMCL 能从 `contact.homepage` 显示“官方页面”，Mod Menu 能显示全部项目链接。
- 对首个后续合格 Beta/Release，校验打包后的 Release 检查构建元数据：Alpha 必须在安排任务/联网前退出；Beta 与 Release 每个客户端进程最多请求一次 manifest。Beta 内嵌当前已经发布的稳定版 `releaseSequence`；新 Release 内嵌即将为该 Release 发布的同一个新序号，从而绝不会把自身提示为更新。
- 对该后续合格构建，使用以下测试数据验证检查器：更新的 Release、相同/更低序号、Beta/Alpha 通道、畸形 JSON、Minecraft 版本不符、只有 Sources、SHA-256 无效、不可信 URL、重复匹配资产、重定向、非 200、超时与超大响应。只有有效、更新且唯一精确匹配的 Release 才能显示一次 Toast 与本地聊天消息。
- 对该后续合格构建，确认 Toast/聊天链接精确为 `https://qcloudy.net/download/` 与 `https://qcloudy.net/changelog/`，且任何路径都不会下载、安装、替换或启动 JAR。
- 核对 320 个 Shard 的目录、模型和纹理集合完全一致，且不包含 Rainbug。
- 对最终复制到 `release/` 的文件重新计算 SHA-256，并写入双语验证报告和官网 manifest。
- 完成独立启动冒烟测试；在把实验编辑器称为稳定前，还必须在已登录环境用五个提供方和多种 GUI Scale 回归。不要把本地自动测试表述成实服兼容证明。

## 发布文件

- `release/QCloudy_Addition-0.3.9+26.1.2-Release.jar`
- `release/QCloudy_Addition-0.3.9+26.1.2-Release-sources.jar`
- `release/QCloudy_Addition-0.3.9+26.2-Release.jar`
- `release/QCloudy_Addition-0.3.9+26.2-Release-sources.jar`

只有可运行 JAR 应放入玩家的 `mods` 文件夹；Sources JAR 只是可选开发者附件。

## GitHub Release

- Tag：`v0.3.9`
- 标题：`QCloudy_Addition 0.3.9 Release`
- 正文：`docs/GITHUB_RELEASE_0.3.9.md`，可附加或链接中文版本。
- 附加上面的四个文件。
- 不要标记为 Pre-release。
- 发布后重新下载两个可运行文件，并与 `docs/VALIDATION.md` 中的哈希核对。

## Modrinth

- 项目描述：`docs/MODRINTH_DESCRIPTION.md`。
- 版本日志：`docs/MODRINTH_RELEASE_0.3.9.md`。
- Version type：**Release**。
- 在同一个版本下上传两个可运行 JAR，分别标注对应 Minecraft 版本。
- 对应 Fabric API 标为必需，Mod Menu 标为可选；所有提供方模组均保持可选。
- 客户端标为 required，服务端标为 unsupported。

## 官网

- 部署包：`release/QCloudy_Addition_Website-0.3.9-20260825.zip`（仅用于官网部署，不要作为模组文件附加）。
- 将最终 `website/` 包内的内容上传到现有站点根目录；不要把 ZIP 本身当网页公开。
- 确认 `/`、`/download/`、`/features/`、`/compliance/` 与 `/changelog/` 可直接访问并可刷新。
- 下载页只显示 Release 0.3.9，并使用准确的 GitHub Release 资产链接、大小与 SHA-256。
- 在首个合格 Beta 前，先获取并校验现有线上 `/assets/data/release-manifest.json`，再把当前已经发布的稳定版 `releaseSequence` 内嵌进 Beta；发布 Beta 时不得覆盖或增加稳定版 manifest。发布新 Release 前，应先选定下一个大于零且严格递增的序号，让所有 Release JAR 内嵌同一序号，发布对应 GitHub 资产，最后再部署使用该精确序号、`channel: "Release"`、精确 `v<版本>` Tag 且每个支持 Minecraft 版本只有一个精确可运行资产的 manifest。
- 确认普通 Release manifest 请求是 QCA 自己唯一的运行时网页请求；披露必须说明不发送标识、遥测、模组列表、玩法数据、Token、Cookie，不自动下载，同时承认普通 HTTPS 会暴露 IP 与 User-Agent。
- 检查中英文切换、手机布局、可重复滚动动画、FAQ 动画、图标比例与导航高亮。

## GitHub Wiki

- 将 `wiki/Home.md`、`wiki/Home-zh-CN.md` 与 `wiki/_Sidebar.md` 发布到独立的 `QCloudy-Addition.wiki.git` 仓库。
- 确认双语页面都显示 Release 0.3.9、两个 Minecraft 目标、相对 2.5.3 的累计变化，以及实验编辑器警告。

## 源码仓库

- 先检查 `git status --short`，只暂存项目文件；不要包含本地地图、PSD、解包资源、缓存、运行目录或参考 JAR。
- Release 修改应与无关工作分开提交。
- 使用普通分支 push 同步源码；Tag 与 Release 资产发布是另外的操作。
