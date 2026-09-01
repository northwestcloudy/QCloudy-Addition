# 版本与产物命名规则

QCloudy_Addition 将模组核心版本、Minecraft 目标版本、发布通道和 Alpha 迭代号分开管理。每次交付产物时，都必须明确并核对发布通道与完整版本号。

## 通道判断

- 只有用户在当前任务中明确要求 Beta 或 Release，才使用对应通道。
- 没有明确要求 Beta 或 Release 时，默认产出 Alpha；不能继承上一次任务的通道。
- Alpha 是不公开的开发产物，默认只构建、测试、打包并交付 Minecraft 26.1.2。除非用户明确要求，否则不得处理 26.2 Alpha。
- Beta 是公开测试通道，但不是稳定更新候选；Release 是游戏内更新检查追踪的稳定通道。

## Alpha 序号重置

Alpha 序号只属于当前核心版本周期，不是跨版本永久累加的数字。当前面的核心版本因下一次 Beta 或 Release 周期而迭代时，Alpha 必须从 `alpha1` 重新开始；同一周期内再依次增加为 `alpha2`、`alpha3`，直到下一次核心版本迭代。

项目所有者给出的版本示例：

- Beta 更新：`0.3.9` → `0.3.10`，对应开发周期从 `0.3.10-alpha1` 开始。
- Release 更新：`0.3.9` → `0.4.9`，对应开发周期从 `0.4.9-alpha1` 开始。

## 产物格式

- Alpha：`QCloudy_Addition-0.3.10-alpha1+26.1.2.jar`。
- Beta：`QCloudy_Addition-0.3.10+26.1.2-Beta.jar`。
- Release：`QCloudy_Addition-0.3.9+26.1.2-Release.jar`。
- Sources 使用相同版本，并在 `.jar` 前增加 `-sources`。

## 构建属性

`gradle.properties` 是当前产出的唯一版本来源：

```properties
minecraft_version=26.1.2
release_channel=Alpha
mod_version=0.3.10
alpha_iteration=1
```

`release_channel` 只接受 `Alpha`、`Beta` 或 `Release`。只有 Alpha 构建必须填写 `alpha_iteration`；Beta 和 Release 的产物名称会忽略这个值。

Fabric 内部元数据使用便于版本比较的格式：

- Alpha：`0.3.10-alpha1+26.1.2`
- Beta：`0.3.10-beta+26.1.2`
- Release：`0.3.9+26.1.2`

Gradle 会根据这些属性同时生成产物名称和内部元数据版本。不要再手动重命名已经构建的 JAR。
