# D 有效权限与缓存一致性问题记录

更新于 2026-11-22。D-01 和 D-02 已在提交 `bfd3cfd` 中修复。
结论以当前源码、公开接口和前端调用链为依据。双后端切换时，检查同一业务配置下原有功能能否继续使用，权限判定和页面结果是否正确。

## 问题索引

| 编号 | 问题 | 状态 | 处理结果 |
| --- | --- | --- | --- |
| D-01 | Structured 的 `snapshot()` 写完缓存后直接返回。授权版本在写入期间推进时，本次调用仍会返回旧权限。 | 已解决 | L1 和 Redis 写入尝试结束后再次读取数据库授权版本。版本变化时重试。[Structured 源码](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L93-L129) |
| D-02 | DDD 的 S03 类图中，`AuthorizationApplicationService` 的 `cache` 字段比其他字段向右缩进。 | 已解决 | 修正字段区的 Graphviz 对齐设置，同步更新图源和生成器，重生成 SVG 与 PNG。[DOT](../../../docs/design/S03-ddd-class.dot) · [SVG](../../../docs/design/S03-ddd-class.svg) · [PNG](../../../docs/design/S03-ddd-class.png) |
| D-03 | Structured 的 S03 时序图曾漏画缓存回填后的版本复查和重试。 | 已解决 | 同步场景定义和时序图，标出写后读取版本、版本变化时重试及重试耗尽返回 503。[场景维护源](../../../tools/render-rbac1-design.scenarios.py) · [PUML](../../../docs/design/S03-structured-sequence.puml) · [SVG](../../../docs/design/S03-structured-sequence.svg) · [PNG](../../../docs/design/S03-structured-sequence.png) |
| D-04 | Structured 事务外的 API 点判定曾缺少覆盖操作策略和权限快照的版本检查。 | 已解决 | 读取策略前和返回结果前检查授权版本，判定或 4xx 异常遇到版本变化时重做整次判定，三次不稳定返回 `503 AUTHORIZATION_UNAVAILABLE`。[Structured API 判定](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L240-L279) · [回归测试](../../../rbac-structured/src/test/java/com/xmu/rbac/structured/AuthorizationConsistencyTest.java) |
| D-05 | Structured 事务外的快照重试曾沿用进入循环前的用户启用状态。 | 已解决 | 每轮读取授权版本后重新读取并校验用户和系统。用户已停用时返回 `401`，事务内继续沿原路径现场计算。[状态检查与重试](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L81-L129) · [回归测试](../../../rbac-structured/src/test/java/com/xmu/rbac/structured/AuthorizationConsistencyTest.java) |
| D-06 | DDD 用户详情查询停用目标系统时，曾将目标状态当作操作者认证失败。 | 已解决 | 管理查询在校验操作者和部门范围后，遇到停用的目标系统返回 `200` 和空权限，与 Structured 一致。操作者认证失败仍返回 `401`，未知系统仍返回 `404`。[DDD 查询](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/DirectoryApplicationService.java#L353) · [Structured 查询](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java#L561-L571) · [回归测试](../../../rbac-ddd/src/test/java/com/xmu/rbac/ddd/application/AuthorizationSecurityRegressionTest.java#L121) |
| D-07 | DDD 的 API `explain` 曾将不同版本的判定和解释快照放进同一响应。 | 已解决 | 对目标状态、权限快照和判定结果一起检查版本。版本变化时重读，稳定停用后返回 `allowed=false` 和空快照。UI 解释和普通 `check` 仍按各自路径处理。[SIM 入口](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/interfaces/rest/AuthorizationController.java#L52-L89) · [解释版本检查](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L154-L190) · [回归测试](../../../rbac-ddd/src/test/java/com/xmu/rbac/ddd/application/AuthorizationSecurityRegressionTest.java#L266) |
| D-08 | 同一系统的 UI 权限和 API 权限同码时，页面曾混合显示两种来源。 | 已解决 | 两套后端和响应契约增加 `permissionKind`，前端按种类与编码归组。旧缓存若包含缺少种类的来源则重新计算。[响应契约](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L4034-L4061) · [Structured 来源](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L188-L192) · [DDD 来源](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/AuthorizationPolicy.java#L7) · [前端回归测试](../../../rbac-web/tests/components.spec.ts#L40) |
| D-09 | SIM 共享接口曾允许空串或超长的权限码、操作 ID 进入业务判定。 | 已解决 | 两套实现均校验已提供字段的长度为 1–128。原有分支仍按 UI/API 类型要求对应字段，非法输入返回 `400 VALIDATION_ERROR`。[输入契约](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L4577-L4601) · [Structured 校验](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Inputs.java#L197) · [DDD 校验](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/interfaces/rest/AuthorizationController.java#L35) |
| D-10 | 前端保存成功后的权限刷新曾复用写入前已发出的同会话请求。 | 已解决 | 保存成功后发起一轮新权限读取，较早的同会话请求以最新刷新结果为准，避免过期成功覆盖权限和菜单，也避免过期失败误导路由。普通刷新继续合并同一会话中尚未完成的最新请求。[刷新顺序](../../../rbac-web/src/stores/auth.ts#L50-L67) · [写后刷新](../../../rbac-web/src/views/workspace.ts#L56-L81) · [回归测试](../../../rbac-web/tests/auth.spec.ts#L58) |
| D-11 | S04 时序图曾采用独立网关链路，前端分工表曾指向旧鉴权页。 | 已解决 | 两版 S04 主时序图更新为当前权限后端的内嵌 `CourseProxyFilter` 链路。独立网关类图标明历史用途，前端分工指出 `/authorization` 的 `AuthorizationView`。[设计图索引](../../../docs/design/index.adoc) · [Structured 时序图](../../../docs/design/S04-structured-sequence.svg) · [DDD 时序图](../../../docs/design/S04-ddd-sequence.svg) · [前端分工](../../../docs/oral-review/frontend.adoc) |
| D-12 | 中心超管在 Structured 中缺少某个已登记操作的显式权限时，点判定曾拒绝，影响管理系统列表加载。 | 已解决 | Structured 在校验用户、系统和已登记操作处于启用状态后，允许持有中心内置 `SUPER_ADMIN` 角色的用户执行中心操作。[Structured 判定](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L256-L279) · [DDD 判定](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/AuthorizationPolicy.java#L96-L105) · [回归测试](../../../rbac-structured/src/test/java/com/xmu/rbac/structured/AuthorizationConsistencyTest.java#L70) |

## 本轮范围复核

- **默认管理员代查。** `root` 的中心超级管理员身份通过两版的具体权限检查。种子 `RBAC_ADMIN` 同时持有 `rbac:authz:invoke` 和 `rbac:user:view`，两版都能代查其管理范围内的用户。两版检查范围的差异只影响另行配置了模拟权限、缺少目录查看权限的自定义普通角色。[Structured 校验](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L281-L285) · [DDD 校验](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L104-L113) · [种子权限](../../../rbac-contract/src/main/resources/db/migration/V2__rbac1_seed.sql#L648-L657)
- **课程接口的身份角色要求。** 教师接口快照中全部 16 处 `x-required-role` 只使用 `teacher`、`student`、`admin`，`copyCourse` 要求 `teacher`。两版都支持这些身份角色。Structured 额外支持普通内部角色编码，只有实际配置了这个扩展用法的操作才涉及切换差异。[课程接入约定](../../../docs/implementation-contract.adoc#L134-L135) · [教师接口快照](../../../rbac-contract/src/main/resources/catalog/course-openapi.source.yaml#L1211-L1212) · [DDD 身份角色匹配](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/AuthorizationPolicy.java#L105-L108)

## D-01 的实现对照与验证

事务外，两套实现都先读取数据库授权版本，再按该版本查缓存或计算权限。写入前复查版本，写入后再次复查。写入后读到新版本时，本次调用进入下一轮，旧缓存条目仍对应旧版本的键。三次尝试仍无法取得稳定结果时，两套实现均返回 HTTP 503，Structured 使用 `AUTHORIZATION_UNAVAILABLE`，DDD 使用 `AUTHORIZATION_UNSTABLE`。[DDD 对应实现](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L71-L101)

事务内，两套实现都绕开缓存。Structured 直接从数据库计算快照。DDD 仍执行版本检查与重试，并可在外层只读事务中使用独立的 `READ_COMMITTED` 事务。[Structured 事务分支](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L88-L92) · [DDD 事务入口](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L62-L76) · [DDD 只读事务配置](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationReadTransaction.java#L8-L16)

修复时模拟 Redis 写入期间推进授权版本。修复前，一次推进会返回旧权限，连续推进也会提前返回。修复后，一次推进会触发重试并取得新快照，连续三次推进会返回 503。这两项回归验证以及 Structured 其余 14 项测试均通过。验证范围为模拟与单元测试。

## D-02 的验证

SVG 中 `repository`、`policy`、`reads`、`cache` 的文字起点横坐标一致，PNG 中也已目视确认四项左对齐。绘图器的 19 项测试通过。[图生成器](../../../tools/render-rbac1-design.py)

## 并发修复与已有保护

本轮需求要求撤权在提交后的下一次访问生效。写入锁定全局授权版本行，变更和版本推进随同一事务提交。新请求重新校验用户、系统、令牌并读取提交后的事实。D-04、D-05 复核的是已经开始且与变更交叠的事务外点查询。[撤权要求](../../../docs/sources/requirements-final.md#L168) · [写入与版本推进](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ResourceService.java#L24-L35) · [令牌校验](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/TokenService.java#L137-L148)

- **D-04。** 中心的固定操作策略受 `CENTER_OPERATION_IMMUTABLE` 保护。Structured 的 SIM 和 S03 管理查询另有外层只读事务。事务外 API 点查询和代理判定现在都通过 `api()` 的版本复查。[中心策略约束](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ResourceService.java#L179-L191) · [SIM 事务](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ResourceService.java#L273-L275) · [代理内判定](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/CourseProxyConfiguration.java#L26-L35)
- **D-05。** 停用同时递增 `tokenVersion`，后续携带旧令牌的请求会被拒绝。本次补上的检查覆盖已通过入口校验、需要重试的事务外快照。[停用写入](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java#L294-L299) · [快照重试](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L88-L129)
- **实际课程链路。** 课程后端在进入业务方法前重新向中心查询 API 判定，再取 `/integration/authorities`。这两次请求会重新校验令牌和用户状态。课程后端的 API 查询与代理判定共用修复后的 Structured 判定入口。[课程后端复查](../../../course-integration/overlay/backend/src/main/java/cn/edu/xmu/projectmgt/integration/OperationGate.java#L44-L70) · [中心调用](../../../course-integration/overlay/backend/src/main/java/cn/edu/xmu/projectmgt/integration/CentralClient.java#L12-L23)
- **D-07。** DDD 在原有两次读取之外增加 API 解释的统一版本检查。稳定的错误继续返回原状态，授权版本变化期间出现的 4xx 会在下一轮重读。常规检查和 UI 解释也将读取期间发生的目标系统停用按 `SYSTEM_DISABLED` 判拒，保留调用者的登录会话。[模拟状态回归](../../../rbac-ddd/src/test/java/com/xmu/rbac/ddd/application/AuthorizationSecurityRegressionTest.java#L293) · [解释版本检查](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L166-L190)
- **D-10。** 会话编号仍保护换账号后的状态，页面修订号仍保护切换页面或业务范围后的反馈。保存成功触发的新读取覆盖了同一会话、同一页面的旧刷新交叠。[会话保护](../../../rbac-web/src/stores/auth.ts#L34-L67) · [页面修订号](../../../rbac-web/src/views/workspace.ts#L56-L81)

Structured 两项新增回归在修复前失败、修复后通过。后端定向测试共通过 58 项，前端 44 项测试和类型检查通过。此次验证使用模拟依赖，未运行真实 MySQL 和 Redis 联测。

## 全部修复后的回归

提交前 Java 测试共通过 156 项，覆盖 Structured、DDD、共享契约及代理。前端 46 项测试、类型检查和生产构建通过，验收工具 21 项及绘图器 19 项测试通过。OpenAPI 生成结果与接口约定一致。已目视检查本轮重生成的 S03、S04 和 SIM 时序图。新增回归覆盖权限变更中的暂时缺失、模拟期间停用目标系统及旧刷新迟到失败。本地 Docker 引擎未运行，未执行依赖 MySQL 和 Redis 的集成测试。
