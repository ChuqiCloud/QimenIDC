# 版本号规则

主控版本号由产品版本和 build 号组成，格式为 `A.B.C_N`。

- `A.B.C` 是产品版本，每段从 `0` 开始，取值范围为 `0` 到 `9`。
- `_N` 是 build 号，从 `1` 开始，取值范围为 `1` 到 `9`。
- build 号递增到 `_9` 后，下一个版本将 build 号重置为 `_1`，并把产品版本的末段加一。
- 产品版本某段从 `9` 进位时，该段重置为 `0`，左侧一段加一。

例如：

- `1.3.5_9` 的下一个版本是 `1.3.6_1`。
- `1.3.9_9` 的下一个版本是 `1.4.0_1`。

发布时保持 `build.gradle` 的 JAR 版本、`application.yml` 中的 `info.application.version` 与 `info.application.build.version`、以及 `banner.txt` 一致。新增数据库升级时，在 `sql-update.yml` 中使用对应 build 号；已经发布的迁移号属于历史记录，不随产品版本递增而改写。

`SqlUpdateLoader.compareBuildVersion()` 按产品版本的数字段和 build 号进行数值比较，支持产品版本进位后的排序。
