# QCloudy_Addition 0.4.9 Beta

适用于 Minecraft 26.1.2 与 26.2 的纯客户端 Fabric 公开 Beta。本版本包含稳定版 Release 0.3.9 以来完成的全部变化。

> **Beta 提示：**这是公开测试用预发布版本。统一设置编辑与统一 HUD 编辑仍是默认关闭的实验性概念测试；请备份提供方配置，并在提供方原生编辑器中核对写入。

## 自 Release 0.3.9 以来的重点变化

- 加入默认关闭的免死中央提示，以及 Spirit Mask、Bonzo's Mask 与 Phoenix 三个独立冷却 HUD。
- 加入默认关闭的好友/白名单组队邀请自动接受、精确私信组队申请及本地快捷私信。
- 加入处理 Party Chat `!` 别名的快速组队指令，以及默认开启的本地双斜杠组队指令，覆盖 Warp、All Invite、转移队长、踢人、坐标、晋升、Stream、地牢与 Kuudra。
- 加入完整别名、触发者范围、冷却、任意十进制 Stream 人数，以及完整玩家名或唯一前缀补全。
- 加入只追踪稳定 Release 的更新提醒。Beta 0.4.9 可以提示更新的兼容稳定版，但不会下载、安装或替换 JAR。
- 补齐启动器/HMCL 的官网、源代码与问题反馈元数据，并在 Mod Menu 中加入官网、下载与源代码链接。

## 修复与界面改进

- 修复升级版 Bonzo's Mask 免死消息未启动冷却。
- 修复自动接受所依赖的 `/friend list` 多页同步，包括分离聊天组件与严格排除无关聊天。
- 修复通用目录无法滚到最底部。
- “兼容模组”现在默认收起。
- 为功能目录、功能设置、兼容性报告和组队白名单加入浏览器式可拖动滚动条。
- 无 HUD 的功能不再显示 HUD 外观设置；没有可编辑内容的卡片不再打开空页面。
- 删除废弃的 Firmament 重复背包功能让渡设置及相关失效配置/代码。

## 兼容性

- Minecraft 26.1.2：Fabric API `0.155.2+26.1.2` 或更新兼容版本。
- Minecraft 26.2：Fabric API `0.154.2+26.2` 或更新兼容版本。
- Fabric Loader 0.19.3 或更新版本，以及 Java 25。
- QCA 仍可独立运行且仅作用于客户端；Mod Menu 与已识别提供方模组均为可选。

## 文件

- `QCloudy_Addition-0.4.9+26.1.2-Beta.jar`
- `QCloudy_Addition-0.4.9+26.1.2-Beta-sources.jar`
- `QCloudy_Addition-0.4.9+26.2-Beta.jar`
- `QCloudy_Addition-0.4.9+26.2-Beta-sources.jar`

只安装一个与 Minecraft 版本匹配的可运行 JAR；不要把 `-sources.jar` 放进 `mods` 文件夹。

完整内容：[CHANGELOG_zh_CN.md](https://github.com/northwestcloudy/QCloudy-Addition/blob/main/CHANGELOG_zh_CN.md)
