# Beta 0.4.9 发布清单

## 共同元数据

- 版本：`0.4.9`
- 通道：Beta / 预发布
- 标签：`v0.4.9-beta`
- Minecraft：26.1.2 与 26.2
- 加载器：Fabric
- 环境：仅客户端
- Java：25
- 稳定更新基线：Release 序号 `1`

## 验证

- 使用 Java 25 运行 `bash tools/build_all_versions.sh`。
- 两个目标的测试与构建都必须通过。
- 对精确的四个 0.4.9 Beta 归档运行 `jar --validate` 与 `unzip -t`。
- 确认可运行元数据声明 `0.4.9-beta+<minecraft>`、纯客户端、Java 25，以及精确目标/Fabric API 依赖。
- 确认 Mod Menu 包含官网、下载、源代码，顶层 contact 包含官网、源代码与问题反馈。
- 确认打包发布属性为 `Beta`、核心版本 `0.4.9`、精确 Minecraft 目标与基线序号 `1`。
- 最终构建后重新计算文件大小与 SHA-256。
- 自动验证不能代替登录 Hypixel、HMCL、完整提供方整合包和不同 GUI Scale 的实测。

## GitHub

- 将明确选择的纯 Mod 源码发布提交推送到 `main`。
- 在该提交创建标签 `v0.4.9-beta`。
- 创建 `QCloudy_Addition 0.4.9 Beta` GitHub **Pre-release**。
- 主正文使用 `docs/GITHUB_RELEASE_0.4.9_BETA.md`。
- 只附加两个可运行 JAR 与两个 Sources JAR。

## Modrinth

- 版本类型：Beta。
- 加载器：Fabric。
- 客户端必需；服务器不支持。
- 更新日志使用 `docs/MODRINTH_RELEASE_0.4.9_BETA.md`。
- 26.1.2 可运行 JAR 对应 Minecraft 26.1.2；26.2 可运行 JAR 对应 Minecraft 26.2。
- 对应 Fabric API 标为必需，Mod Menu 标为可选，提供方模组保持可选。

## 官网

- 与源码提交分开准备 Beta 0.4.9 下载/更新日志内容。
- 不替换或增加稳定 Release 0.3.9 manifest；Beta 不是游戏内更新目标。
