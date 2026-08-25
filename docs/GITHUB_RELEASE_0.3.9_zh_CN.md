# QCloudy_Addition 0.3.9 Release

适用于 Minecraft 26.1.2 与 26.2 的稳定纯客户端 Fabric 版本。本次 Release 汇总上一个稳定版 2.5.3 之后完成的全部公开变化。

> **实验性警告：**统一设置编辑与统一 HUD 编辑仍是概念测试，默认关闭且尚不稳定。第三方模组更新可能使已识别映射失效。请先备份配置、谨慎使用，并在对应模组原生设置/HUD 编辑器中核对结果。

## 自 Release 2.5.3 以来的重点变化

- 加入完整离线 320-Shard 指南与规划器：详情、自然/Fusion 获取方式、配方、用途、候选路线、多步 Fusion Tree、Materials Only、Ironman、可选提供方缓存价格路线、每小时获取速度、Fusion Lines 与本地观察的 Hunting Box 仓库。
- 加入每种 Shard 对应图标、符合游戏/Wiki 语义的颜色、可点击导航、紧凑配方布局、窄屏自适应与搜索焦点修复。
- 加入默认关闭的 Ciallo 钓鱼上钩提示音，支持独立音量、水钓/岩浆钓鱼及每根鱼钩只播放一次。
- 加入 Power Orb 与 Flare 消失提醒。Power Orb 使用本人精确消失聊天；Warning、Alert、SOS Flare 使用已确认的三分钟生命周期，重新放置会完整重置计时。
- 加入全部 20 种 Century Cake 的真实世界 48 小时追踪、`/cake`、`/centurycakeeffect`、合并过期提醒与仅在直接点击后执行的续效果操作。
- 加入可选且相互独立的统一设置/HUD 能力发现，支持 SkyHanni、Skyblocker、Firmament、BabyZombieAddons 与 Feesh 的安全识别分支，并提供二次确认、进度、Refresh 和兼容性缺失页面。
- Dwarven Mines 指针改为连续、近似、只使用 X/Z 的投影，忽略 Y 和记分板小区域跳转。
- 修复本人 Tree Gift 生物提醒、Park Jungle 错误挖矿 HUD、Golden/Jade Dragon 等级、钓鱼重复播放、Century Cake 首次/刷新解析、SOS 替换计时，以及多处 UI 重叠与焦点问题。

## 删除或替换

- 删除槽位锁定、Storage Overlay 与菜单中键转换。
- 删除 `/aca` 与 `/ca`；保留 `/qca` 与 `/qc`。
- 删除旧 Dwarven 分区/Y 层跳转，以及不完整的 Flare 聊天、距离和实体卸载式猜测。
- 用逐项能力校验替换精确提供方版本白名单；未知分支会失败关闭。

## 兼容性

- Minecraft 26.1.2：Fabric API `0.155.2+26.1.2` 或更新的兼容构建。
- Minecraft 26.2：Fabric API `0.154.2+26.2` 或更新的兼容构建。
- Fabric Loader 0.19.3 或更高版本，Java 25。
- QCA 仍可独立运行且仅作用于客户端；其他 SkyBlock 模组和 Mod Menu 都是可选项。

## 文件

- `QCloudy_Addition-0.3.9+26.1.2-Release.jar`
- `QCloudy_Addition-0.3.9+26.1.2-Release-sources.jar`
- `QCloudy_Addition-0.3.9+26.2-Release.jar`
- `QCloudy_Addition-0.3.9+26.2-Release-sources.jar`

只安装与你的 Minecraft 版本完全对应的一份可运行 JAR；不要把 `-sources.jar` 当作模组安装。

完整累计变化见：[CHANGELOG_zh_CN.md](https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/blob/main/CHANGELOG_zh_CN.md)
