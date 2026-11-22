# D 有效权限与缓存一致性问题记录

更新于 2026-11-22。下列问题均已在提交 `bfd3cfd` 中修复。

| 编号 | 问题 | 状态 | 处理结果 |
| --- | --- | --- | --- |
| D-01 | Structured 的 `snapshot()` 写完缓存后直接返回。若写入期间有并发授权变更并推进版本，本次调用可能返回旧权限。 | 已解决 | L1 和 Redis 写入尝试结束后再次读取数据库授权版本。版本变化时重试。[Structured 源码](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L89-L119) |
| D-02 | DDD 的 S03 类图中，`AuthorizationApplicationService` 的 `cache` 字段比其他字段向右缩进。 | 已解决 | 修正字段区的 Graphviz 对齐设置，同步更新图源和生成器，重生成 SVG 与 PNG。[DOT](../../../docs/design/S03-ddd-class.dot) · [SVG](../../../docs/design/S03-ddd-class.svg) · [PNG](../../../docs/design/S03-ddd-class.png) |

## D-01 的实现对照与验证

事务外，两套实现都先读取数据库授权版本，再按该版本查缓存或计算权限。写入前复查版本，写入后再次复查。写入后读到新版本时，本次调用进入下一轮，旧缓存条目仍对应旧版本的键。三次尝试仍无法取得稳定结果时，两套实现均返回 HTTP 503，Structured 使用 `AUTHORIZATION_UNAVAILABLE`，DDD 使用 `AUTHORIZATION_UNSTABLE`。[DDD 对应实现](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L71-L101)

事务内，两套实现都绕开缓存。Structured 直接从数据库计算快照。DDD 仍执行版本检查与重试，并可在外层只读事务中使用独立的 `READ_COMMITTED` 事务。[Structured 事务分支](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L82-L88) · [DDD 事务入口](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L62-L76) · [DDD 只读事务配置](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationReadTransaction.java#L8-L16)

修复时模拟 Redis 写入期间推进授权版本。修复前，一次推进会返回旧权限，连续推进也会提前返回。修复后，一次推进会触发重试并取得新快照，连续三次推进会返回 503。这两项回归验证以及 Structured 其余 14 项测试均通过。验证范围为模拟与单元测试。

## D-02 的验证

SVG 中 `repository`、`policy`、`reads`、`cache` 的文字起点横坐标一致，PNG 中也已目视确认四项左对齐。绘图器的 19 项测试通过。[图生成器](../../../tools/render-rbac1-design.py)
