# Release 0.3.9 发布检查清单

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
- 检查中英文切换、手机布局、可重复滚动动画、FAQ 动画、图标比例与导航高亮。

## GitHub Wiki

- 将 `wiki/Home.md`、`wiki/Home-zh-CN.md` 与 `wiki/_Sidebar.md` 发布到独立的 `QCloudy-Addition.wiki.git` 仓库。
- 确认双语页面都显示 Release 0.3.9、两个 Minecraft 目标、相对 2.5.3 的累计变化，以及实验编辑器警告。

## 源码仓库

- 先检查 `git status --short`，只暂存项目文件；不要包含本地地图、PSD、解包资源、缓存、运行目录或参考 JAR。
- Release 修改应与无关工作分开提交。
- 使用普通分支 push 同步源码；Tag 与 Release 资产发布是另外的操作。
