# 版本与产物命名规则

QCloudy_Addition 将模组核心版本、Minecraft 目标版本、发布通道和 Alpha 迭代号分开管理。

## 产物格式

`QCloudy_Addition-<核心版本>+<Minecraft 版本>-<通道>[-<Alpha 迭代号>].jar`

- Alpha 带独立的正整数迭代号：`QCloudy_Addition-0.2.9+26.1.2-Alpha-30.jar`。
- Beta 不带独立的 Beta 号：`QCloudy_Addition-0.2.10+26.1.2-Beta.jar`。
- Release 不带独立的 Release 号：`QCloudy_Addition-0.3.9+26.1.2-Release.jar`。
- Sources 使用相同名称，并在 `.jar` 前增加 `-sources`。

## 构建属性

`gradle.properties` 是唯一版本来源：

```properties
release_channel=Beta
mod_version=0.4.9
alpha_iteration=37
```

`release_channel` 只接受 `Alpha`、`Beta` 或 `Release`。只有 Alpha 构建必须填写 `alpha_iteration`；Beta 和 Release 的产物名称会忽略这个值。

Fabric 内部元数据使用便于版本比较的格式：

- Alpha：`0.2.9-alpha.30+26.1.2`
- Beta：`0.2.10-beta+26.1.2`
- Release：`0.3.9+26.1.2`

Gradle 会根据这些属性同时生成产物名称和内部元数据版本。不要再手动重命名已经构建的 JAR。
