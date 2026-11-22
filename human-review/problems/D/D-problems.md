# D 有效权限与缓存一致性问题记录

更新于 2026-11-22。D-01 和 D-02 已在提交 `bfd3cfd` 中修复。
结论以当前源码、公开接口和前端调用链为依据。双后端切换时，检查同一业务配置下原有功能能否继续使用，权限判定和页面结果是否正确。

## 问题索引

| 编号 | 问题 | 状态 | 处理结果 |
| --- | --- | --- | --- |
| D-01 | Structured 的 `snapshot()` 写完缓存后直接返回。授权版本在写入期间推进时，本次调用仍会返回旧权限。 | 已解决 | L1 和 Redis 写入尝试结束后再次读取数据库授权版本。版本变化时重试。[Structured 源码](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L89-L119) |
| D-02 | DDD 的 S03 类图中，`AuthorizationApplicationService` 的 `cache` 字段比其他字段向右缩进。 | 已解决 | 修正字段区的 Graphviz 对齐设置，同步更新图源和生成器，重生成 SVG 与 PNG。[DOT](../../../docs/design/S03-ddd-class.dot) · [SVG](../../../docs/design/S03-ddd-class.svg) · [PNG](../../../docs/design/S03-ddd-class.png) |
| D-03 | Structured 的 S03 顺序图仍显示回填缓存后直接返回，漏画写后查版本和版本变化后重试。 | 未解决 | 生成器中的场景定义和现有顺序图仍保留旧流程。[场景维护源](../../../tools/render-rbac1-design.scenarios.py#L124-L128) · [顺序图源](../../../docs/design/S03-structured-sequence.puml#L84-L96) |
| D-04 | Structured 的 S04 判定混用操作策略和权限快照的读取版本。 | 未解决 | `api()` 先读取操作策略。若在取权限快照前策略已经变更，最终仍用旧策略判断。内部快照的版本检查不会重读外层策略。[Structured API 判定](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L228-L248) · [DDD API 判定](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L131-L151) |
| D-05 | Structured 的快照重试复用旧的用户启用状态。 | 未解决 | 用户停用与已开始的查询交叠时，停用推进版本但保留角色指派。版本稳定的下一轮仍会根据该用户保留的指派返回权限。[状态检查与重试](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L82-L117) · [停用写入](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java#L279-L301) · [版本推进](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java#L34-L39) |
| D-06 | 切换到 DDD 后，在用户详情里查已停用系统的权限会让当前管理员退出登录。 | 未解决 | 管理目标下拉框允许选择停用系统。Structured 返回 `200` 和空权限，DDD 返回 `401 SYSTEM_DISABLED`，前端处理该 401 时会清会话并跳转登录。[Structured 查询](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java#L561-L571) · [DDD 查询](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L71-L89) · [系统查找](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/infrastructure/persistence/JpaAuthorizationRepositoryAdapter.java#L60-L70) · [前端 401](../../../rbac-web/src/api/http.ts#L19-L26) · [清除会话](../../../rbac-web/src/main.ts#L13-L23) · [停用系统仍可选](../../../rbac-web/src/layouts/AppLayout.vue#L102-L105) · [用户详情查权限](../../../rbac-web/src/views/UserWorkspace.vue#L75-L87) |
| D-07 | DDD 的 API 鉴权解释会混用两次授权读取的结果。 | 未解决 | 解释先取展示用 `effective`，再调用 `api()` 做判定。若授权在两次读取之间变更，`allowed` 使用后一次读取，来源仍来自前一次。[SIM 判定与解释](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/interfaces/rest/AuthorizationController.java#L52-L69) · [API 版本检查](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L131-L151) |
| D-08 | UI 与 API 可在同一系统中使用同一个权限码。两版来源项和 `EffectiveVO` 约定只提供 `permissionCode`，前端也只按编码分组，导致两条权限会显示混在一起的来源。 | 未解决 | 临时组件测试传入两条同码异种类的权限和两个不同角色来源，两个权限行各显示了两条来源。两版授权判定仍按权限种类区分。[权限唯一键](../../../rbac-contract/src/main/resources/db/migration/V1__rbac1_schema.sql#L64-L76) · [来源契约](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L4034-L4055) · [Structured 来源](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L176-L181) · [DDD 来源](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/AuthorizationPolicy.java#L7-L8) · [前端分组](../../../rbac-web/src/components/EffectivePermissions.vue#L9-L18) |
| D-09 | SIM 的共享接口未实施约定的权限码和操作 ID 长度校验。 | 未解决 | 直接调用 API 时，空串或超长字符串会进入业务判定。空 UI 权限码会按正常的未获授权处理。该问题属于共享接口输入校验。[输入契约](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L4571-L4595) · [Structured DTO](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Inputs.java#L197-L198) · [DDD DTO 与分支](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/interfaces/rest/AuthorizationController.java#L35-L53) |
| D-10 | 前端保存成功后的权限刷新会复用写入前已发出的同会话请求。 | 未解决 | 第一次刷新在提交前读到了权限，响应仍在路上。保存成功后，前端再次调用 `refresh()`，同一会话直接复用第一次的 Promise。旧结果返回时会写回权限和菜单，并把 `loadedAt` 更新为当下时间。页面因此可以在提示「已保存」时仍显示旧的菜单或按钮。[刷新合并](../../../rbac-web/src/stores/auth.ts#L50-L62) · [写后刷新](../../../rbac-web/src/views/workspace.ts#L56-L80) |
| D-11 | S04 旧图和部分说明采用独立网关链路，当前 Compose 配置由权限后端内嵌代理。前端分工表仍指向 `CatalogWorkspace`，实际鉴权页已由 `AuthorizationView` 承接。 | 未解决 | 图和前端审查表需标清所对应的版本与入口。[原 S04 图源](../../../docs/design/S04-structured-sequence.puml) · [当前代理配置](../../../docker-compose.yml#L31-L43) · [代理装配](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/CourseProxyConfiguration.java#L19-L45) · [前端分工](../../../docs/oral-review/frontend.adoc#L19) · [实际路由](../../../rbac-web/src/router/routes.ts#L15) |
| D-12 | 中心超级管理员切到 Structured 后，管理目标系统选择会受点查询结果影响。 | 未解决 | 管理员保留 `SUPER_ADMIN` 身份且缺少操作配置的具体权限时，DDD 点查询允许，Structured 点查询拒绝。前端先用 `listSystems` 的点查询决定是否加载管理系统列表，因此该差异影响可选系统。[DDD 中心管理员识别](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/AuthorizationPolicy.java#L26-L31) · [DDD 判定](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/AuthorizationPolicy.java#L92-L101) · [Structured 判定与管理入口](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L228-L255) · [前端加载管理系统](../../../rbac-web/src/layouts/AppLayout.vue#L26-L31) · [可修改角色权限](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java#L427-L445) |

## 本轮范围复核

- **默认管理员代查。** `root` 的中心超级管理员身份通过两版的具体权限检查。种子 `RBAC_ADMIN` 同时持有 `rbac:authz:invoke` 和 `rbac:user:view`，两版都能代查其管理范围内的用户。两版检查范围的差异只影响另行配置了模拟权限、缺少目录查看权限的自定义普通角色。[Structured 校验](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L251-L255) · [DDD 校验](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L104-L113) · [种子权限](../../../rbac-contract/src/main/resources/db/migration/V2__rbac1_seed.sql#L648-L657)
- **课程接口的身份角色要求。** 教师接口快照中全部 16 处 `x-required-role` 只使用 `teacher`、`student`、`admin`，`copyCourse` 要求 `teacher`。两版都支持这些身份角色。Structured 额外支持普通内部角色编码，只有实际配置了这个扩展用法的操作才涉及切换差异。[课程接入约定](../../../docs/implementation-contract.adoc#L134-L135) · [教师接口快照](../../../rbac-contract/src/main/resources/catalog/course-openapi.source.yaml#L1211-L1212) · [DDD 身份角色匹配](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/AuthorizationPolicy.java#L99-L101)

## D-01 的实现对照与验证

事务外，两套实现都先读取数据库授权版本，再按该版本查缓存或计算权限。写入前复查版本，写入后再次复查。写入后读到新版本时，本次调用进入下一轮，旧缓存条目仍对应旧版本的键。三次尝试仍无法取得稳定结果时，两套实现均返回 HTTP 503，Structured 使用 `AUTHORIZATION_UNAVAILABLE`，DDD 使用 `AUTHORIZATION_UNSTABLE`。[DDD 对应实现](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L71-L101)

事务内，两套实现都绕开缓存。Structured 直接从数据库计算快照。DDD 仍执行版本检查与重试，并可在外层只读事务中使用独立的 `READ_COMMITTED` 事务。[Structured 事务分支](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L82-L88) · [DDD 事务入口](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L62-L76) · [DDD 只读事务配置](../../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationReadTransaction.java#L8-L16)

修复时模拟 Redis 写入期间推进授权版本。修复前，一次推进会返回旧权限，连续推进也会提前返回。修复后，一次推进会触发重试并取得新快照，连续三次推进会返回 503。这两项回归验证以及 Structured 其余 14 项测试均通过。验证范围为模拟与单元测试。

## D-02 的验证

SVG 中 `repository`、`policy`、`reads`、`cache` 的文字起点横坐标一致，PNG 中也已目视确认四项左对齐。绘图器的 19 项测试通过。[图生成器](../../../tools/render-rbac1-design.py)

## D-04 的复核依据

Structured 保存操作策略时在同一写事务中推进授权版本。若判定期间策略发生变化，内部 `snapshot()` 即使拿到稳定的权限快照，外层仍会使用先前读到的策略。DDD 会重新读取操作策略，其既有单元测试覆盖判定期间策略收紧的场景。问题在于同一次 Structured 判定会用先前的操作策略和随后取得的权限快照，结果没有对应到同一次授权读取。[策略保存](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ResourceService.java#L173-L215) · [版本推进](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ResourceService.java#L31-L34) · [DDD 回归用例](../../../rbac-ddd/src/test/java/com/xmu/rbac/ddd/application/AuthorizationSecurityRegressionTest.java#L175-L213)
