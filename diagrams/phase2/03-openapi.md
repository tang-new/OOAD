# OpenAPI 功能解读与完整操作索引

> 阅读基线：2026-11-19；Git HEAD：`560ed0d32aca4765b1a936a711bfdf0a8ad559b7`。本文来自当前源码、固定教师 API 和数据库迁移的静态分析。本次未运行业务测试或部署，接口存在不等于本轮验收通过。

## 先理解文件与计数

- 中心：[rbac-contract/src/main/resources/openapi/rbac-api.yaml](../../rbac-contract/src/main/resources/openapi/rbac-api.yaml)，67 个操作、49 个路径、63 个 schema。
- 教师：[rbac-contract/src/main/resources/catalog/course-openapi.source.yaml](../../rbac-contract/src/main/resources/catalog/course-openapi.source.yaml)，90 个操作，其中 80 个课程业务操作与 10 个提供方操作。
- 提供方操作计入中心目录。两个系统合计 147 个操作标识，课程目录包含一个公开登录。
- 中心目录包含 54 个 /api/v1 操作（含 health/info）、8 个统一身份鉴权操作、2 个审计操作、2 个私有投影和 1 个 JWKS。

## 功能分组

| 能力 | 操作数 | 实现功能 |
| --- | --- | --- |
| 统一身份与鉴权 | 8 | 统一登录、持久注销；查询当前系统角色和前端权限；分别判断 UI 权限与 API 操作资格。 |
| 集中审计服务 | 2 | 课程后端携带用户令牌与服务密钥写入 / 查询审计；requestId 幂等，操作者从可信身份取得。 |
| 本人资料、改密与首页 | 3 | 查看本人及有效权限、修改密码、查看用户/角色/系统基础计数。 |
| 受管系统 | 4 | 登记、重命名、启停系统；不同系统角色和资源隔离。无系统删除接口。 |
| 部门与角色准入 | 6 | 管理部门树，维护该部门允许指派的角色；替换准入时回收不合资格指派。 |
| 用户管理 | 7 | 分页检索、创建、编辑、启停、逻辑删除和独立部门调动；禁止通过普通 PATCH 偷改部门或状态。 |
| 角色、直接权限与继承 | 12 | 管理系统角色、启停与退役；配置直接权限；维护同系统多父 DAG；查询继承后的有效权限。 |
| 用户角色指派与有效权限 | 4 | 指派、撤销用户角色并查看结果；受部门准入、管理范围和操作者授权上界约束。 |
| 权限定义 | 3 | 按系统及 UI/API 种类检索、创建和启停权限；当前没有通用权限改名或删除端点。 |
| 菜单与按钮资源 | 5 | 维护 MENU/BUTTON 树、路由、顺序与权限码，返回本人可见菜单；父菜单不自动授予按钮权限。 |
| API 操作登记 | 3 | 登记 operationId、HTTP 方法、路径、权限/角色条件及访问策略；中心受保护路由不能被弱化。 |
| UI 与 API 映射 | 2 | 维护多对多覆盖关系并查询未映射项；映射用于追踪，不自动授予 API 权限。 |
| 鉴权与解释 | 2 | 查询某用户在某系统的 UI/API 许可，解释角色继承来源；调用资格不足是 403，正常拒绝可为 200 + allowed:false。 |
| 管理审计查询 | 1 | 查询中心和课程来源的审计事实；筛选、权限隔离和分页在业务/数据层完成。 |
| 私有服务投影 | 2 | 提供当前系统真实 API 能力与统一用户查询；仅可信后端可用，公开 Nginx 阻断。 |
| 公钥与运行状态 | 3 | JWKS 用于签名验证；health/info 用于运行观察，不表示业务验收完成。 |

## 怎样读一个操作

`method + path` 是真实 HTTP 路由；`operationId` 是稳定操作标识，网关以它向中心查询资格；`parameters` 是路径/查询等参数，`requestBody` 是请求体，`responses` 描述状态码及返回模型，`components.schemas` 是可复用结构。

`x-required-permission` 是本项目自定义元数据，不是 OpenAPI 自带鉴权算法。PUBLIC、@authenticated 和具体 API 权限码语义不同。`security: bearerAuth` 只声明 Bearer 方案，不能代替代码中的角色继承、管理范围和对象规则。

## 协议与实现核对要点

- `/api/v1` 普通成功响应使用 `{code:"OK", message, data, timestamp}`；Actuator health/info 是框架原生响应，不属于普通业务包装。
- `/auth/*`、私有投影和 JWKS 使用各自裸响应；退出 204 无响应体。错误采用稳定字符串 `code` 与中文 `message`。
- `GET /auth/me` 仅投影 student/teacher/admin 身份角色和 UI 权限；管理端 `/api/v1/me` 给出用户与有效授权详情，二者不能混用。
- UI 权限判断只返回 `{allowed}`；API 资格判断返回 `{operationId,allowed}`；普通无权可返回 200 + false，调用者身份不合法则是 401/403。
- 审计 POST 提交后返回 201，同 requestId 同体重试返回原记录；同键异体冲突；GET 是裸分页。均要求服务密钥与相应审计权限。
- `/integration/authorities` 和 `/identity/users/{userId}` 是内网可信服务接口；OpenAPI 写 Bearer 不等于只凭 Bearer 即可调用。
- 列表通常 page 从 1 开始，pageSize 默认 20、最大 100；固定小集合可返回数组；看每个操作定义。
- 权限中心 OAS 继承了教师描述中的“本系统不持久化密码/令牌状态”，那段“本系统”原指课程中心；当前中心实际使用 password_hash 与 sys_token_revocation。不能据复制描述否定代码。
- 不提供公司 CRUD、业务数据范围、RBAC2/3、热力图；当前权限定义没有完整 CRUD，系统也没有 DELETE API。

## 统一身份与鉴权（8）

统一登录、持久注销；查询当前系统角色和前端权限；分别判断 UI 权限与 API 操作资格。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| POST | `/auth/login` | `login` | PUBLIC | 登录 |
| POST | `/auth/logout` | `logout` | @authenticated | 退出登录 |
| GET | `/auth/me` | `getIdentityCurrentUser` | @authenticated | 当前用户、角色与权限 |
| GET | `/auth/departments` | `listIdentityDepartments` | TEC | 查询统一身份部门列表（逐层展开） |
| GET | `/auth/departments/{departmentId}/users` | `listIdentityDepartmentUsers` | TEC | 查询部门下的用户 |
| GET | `/auth/permissions/{permission}` | `checkIdentityPermission` | @authenticated | 校验当前用户是否具备指定前端权限 |
| GET | `/auth/api-permissions/{operationId}` | `getApiAuthority` | @authenticated | 判断当前用户能否调用指定 API 接口 |
| GET | `/auth/users/{userId}` | `lookupIdentityUser` | TEC | 查询统一身份用户 |

## 集中审计服务（2）

课程后端携带用户令牌与服务密钥写入 / 查询审计；requestId 幂等，操作者从可信身份取得。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/auth/audit-logs` | `queryIdentityAuditLogs` | audit:read | 查询统一身份审计日志 |
| POST | `/auth/audit-logs` | `writeIdentityAuditLog` | audit:write | 写入统一身份审计日志 |

## 本人资料、改密与首页（3）

查看本人及有效权限、修改密码、查看用户/角色/系统基础计数。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/me` | `getMe` | @authenticated | 本人UserVO及当前系统EffectiveVO组成 `{user,effective}` |
| POST | `/api/v1/auth/password` | `changePassword` | @authenticated | oldPassword,newPassword；204或包装null，更新tokenVersion |
| GET | `/api/v1/dashboard` | `getDashboard` | rbac:user:view | 仅基础计数 `{users,roles,systems}`，不做统计/热力图 |

## 受管系统（4）

登记、重命名、启停系统；不同系统角色和资源隔离。无系统删除接口。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/systems` | `listSystems` | rbac:system:view | SystemVO[]（有限注册系统） |
| POST | `/api/v1/systems` | `createSystem` | rbac:system:edit | {code,name} |
| PATCH | `/api/v1/systems/{id}` | `updateSystem` | rbac:system:edit | {name} |
| PUT | `/api/v1/systems/{id}/status` | `setSystemStatus` | rbac:system:edit | {status} |

## 部门与角色准入（6）

管理部门树，维护该部门允许指派的角色；替换准入时回收不合资格指派。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/departments/tree` | `getDepartmentTree` | rbac:department:view | DepartmentVO[] |
| POST | `/api/v1/departments` | `createDepartment` | rbac:department:edit | {parentId?,code,name} |
| PATCH | `/api/v1/departments/{id}` | `updateDepartment` | rbac:department:edit | {name} |
| DELETE | `/api/v1/departments/{id}` | `deleteDepartment` | rbac:department:edit | 有员工/子部门则409；无数据再删 |
| GET | `/api/v1/departments/{id}/assignable-roles` | `getDepartmentAssignableRoles` | rbac:department:view | ?systemCode |
| PUT | `/api/v1/departments/{id}/assignable-roles` | `replaceDepartmentAssignableRoles` | rbac:department:edit | {roleIds:Long[]}原子替换，回收不合资格指派 |

## 用户管理（7）

分页检索、创建、编辑、启停、逻辑删除和独立部门调动；禁止通过普通 PATCH 偷改部门或状态。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/users` | `listUsers` | rbac:user:view | ?keyword,departmentId,status,page,pageSize |
| POST | `/api/v1/users` | `createUser` | rbac:user:edit | {username,name,no?,phone?,departmentId,password} |
| GET | `/api/v1/users/{id}` | `getUser` | rbac:user:view | UserVO |
| PATCH | `/api/v1/users/{id}` | `updateUser` | rbac:user:edit | {name?,no?,phone?,version?}，禁止department/status |
| DELETE | `/api/v1/users/{id}` | `deleteUser` | rbac:user:edit | 逻辑删除，清理指派、版本、审计 |
| PUT | `/api/v1/users/{id}/status` | `setUserStatus` | rbac:user:edit | {status} |
| PUT | `/api/v1/users/{id}/department` | `changeUserDepartment` | rbac:user:edit | {departmentId,version?} |

## 角色、直接权限与继承（12）

管理系统角色、启停与退役；配置直接权限；维护同系统多父 DAG；查询继承后的有效权限。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/roles` | `listRoles` | rbac:role:view | ?systemCode必填,keyword,status,page,pageSize |
| POST | `/api/v1/roles` | `createRole` | rbac:role:edit | {systemCode,code,name,description?,identityRole?} |
| GET | `/api/v1/roles/{id}` | `getRole` | rbac:role:view | RoleVO |
| PATCH | `/api/v1/roles/{id}` | `updateRole` | rbac:role:edit | {name?,description?,version?}，禁止status |
| DELETE | `/api/v1/roles/{id}` | `deleteRole` | rbac:role:edit | 受控退役/清指派、边、权限、准入，保护最后有效超级管理员 |
| PUT | `/api/v1/roles/{id}/status` | `setRoleStatus` | rbac:role:status | {status} |
| GET | `/api/v1/roles/{id}/permissions` | `getRolePermissions` | rbac:role:view | PermissionVO[]（直接） |
| PUT | `/api/v1/roles/{id}/permissions` | `replaceRolePermissions` | rbac:role:edit | {permissionIds:Long[]}原子替换，仅同系统 |
| GET | `/api/v1/roles/{id}/effective-permissions` | `getRoleEffectivePermissions` | rbac:role:view | EffectiveVO（以该角色为直接角色） |
| GET | `/api/v1/systems/{code}/role-hierarchy` | `getRoleHierarchy` | rbac:role:view | HierarchyVO |
| POST | `/api/v1/roles/{seniorId}/inherits/{juniorId}` | `addRoleInheritance` | rbac:role:edit | 同系统有向边，重复409，环409 ROLE_CYCLE |
| DELETE | `/api/v1/roles/{seniorId}/inherits/{juniorId}` | `deleteRoleInheritance` | rbac:role:edit | 无边404，否则回收并失效 |

## 用户角色指派与有效权限（4）

指派、撤销用户角色并查看结果；受部门准入、管理范围和操作者授权上界约束。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/users/{id}/role-assignments` | `listUserRoleAssignments` | rbac:user:view | ?systemCode |
| POST | `/api/v1/users/{id}/role-assignments` | `createUserRoleAssignment` | rbac:assignment:edit | {roleId} |
| DELETE | `/api/v1/users/{id}/role-assignments/{assignmentId}` | `deleteUserRoleAssignment` | rbac:assignment:edit | 按用户核对指派所属再删 |
| GET | `/api/v1/users/{id}/systems/{code}/permissions` | `getUserEffectivePermissions` | rbac:user:view | EffectiveVO |

## 权限定义（3）

按系统及 UI/API 种类检索、创建和启停权限；当前没有通用权限改名或删除端点。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/permissions` | `listPermissions` | rbac:permission:view | ?systemCode,kind,keyword,page,pageSize |
| POST | `/api/v1/permissions` | `createPermission` | rbac:permission:edit | {systemCode,code,name,kind} |
| PUT | `/api/v1/permissions/{id}/status` | `setPermissionStatus` | rbac:permission:edit | {status} |

## 菜单与按钮资源（5）

维护 MENU/BUTTON 树、路由、顺序与权限码，返回本人可见菜单；父菜单不自动授予按钮权限。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/systems/{code}/ui-resources` | `getUiResources` | rbac:resource:view | UiResourceVO[]树 |
| POST | `/api/v1/systems/{code}/ui-resources` | `createUiResource` | rbac:resource:edit | {parentId?,type,code,name,route?,component?,sortOrder?,permissionCode,status?} |
| PATCH | `/api/v1/ui-resources/{id}` | `updateUiResource` | rbac:resource:edit | 同上可编辑字段，禁止换system |
| DELETE | `/api/v1/ui-resources/{id}` | `deleteUiResource` | rbac:resource:edit | 有子节点/映射则409，不静默孤儿 |
| GET | `/api/v1/menus/me` | `getMyMenus` | @authenticated | 当前aud可见UiResourceVO[]树，含按钮 |

## API 操作登记（3）

登记 operationId、HTTP 方法、路径、权限/角色条件及访问策略；中心受保护路由不能被弱化。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/systems/{code}/api-operations` | `listApiOperations` | rbac:resource:view | ?keyword,page,pageSize |
| POST | `/api/v1/systems/{code}/api-operations` | `createApiOperation` | rbac:resource:edit | {operationId,method,pathTemplate,permissionCode?,requiredRole?,accessPolicy,enabled} |
| PATCH | `/api/v1/api-operations/{id}` | `updateApiOperation` | rbac:resource:edit | 同上可修改；中心受保护路由不能弱化OAS要求 |

## UI 与 API 映射（2）

维护多对多覆盖关系并查询未映射项；映射用于追踪，不自动授予 API 权限。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/systems/{code}/ui-api-mappings` | `getUiApiMappings` | rbac:resource:view | MappingVO[]及覆盖报告 `{mappings,unmappedUi,unmappedApi}` |
| PUT | `/api/v1/systems/{code}/ui-api-mappings` | `replaceUiApiMappings` | rbac:resource:edit | {mappings:[{uiResourceId,apiOperationId}]}原子替换 |

## 鉴权与解释（2）

查询某用户在某系统的 UI/API 许可，解释角色继承来源；调用资格不足是 403，正常拒绝可为 200 + allowed:false。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/authorization/check` | `checkAuthorization` | rbac:authz:invoke | {systemCode,userId?,kind:UI/API,permission?或operationId?} |
| POST | `/api/v1/authorization/explain` | `explainAuthorization` | rbac:authz:invoke | 同上 |

## 管理审计查询（1）

查询中心和课程来源的审计事实；筛选、权限隔离和分页在业务/数据层完成。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/audit-logs` | `queryAuditLogs` | rbac:audit:view | 同外部过滤＋systemCode，Page<AuditVO> |

## 私有服务投影（2）

提供当前系统真实 API 能力与统一用户查询；仅可信后端可用，公开 Nginx 阻断。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/integration/authorities` | `getIntegrationAuthorities` | @authenticated | getIntegrationAuthorities |
| GET | `/identity/users/{userId}` | `lookupUnifiedIdentityUser` | @authenticated | lookupUnifiedIdentityUser |

## 公钥与运行状态（3）

JWKS 用于签名验证；health/info 用于运行观察，不表示业务验收完成。

| 方法 | 路径 | operationId | 入口权限 | 摘要 |
| --- | --- | --- | --- | --- |
| GET | `/.well-known/jwks.json` | `getJwks` | PUBLIC | getJwks |
| GET | `/api/v1/actuator/health` | `getHealth` | PUBLIC | getHealth |
| GET | `/api/v1/actuator/info` | `getInfo` | PUBLIC | getInfo |

## 课程业务目录（80）

课程接口由教师后端实现；权限中心登记其操作和全局许可。UI 权限码、AuthorityCodes 与对象关系分别承担不同职责。

### 统一身份鉴权（1）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| POST | `/login` | courseLogin | 课程系统登录 |

### 当前用户（1）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| GET | `/me` | getCurrentUser | 获取本系统当前用户 |

### 学期（6）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| GET | `/terms` | listTerms | 学期列表 |
| POST | `/terms` | createTerm | 新建学期 |
| PATCH | `/terms/{termId}` | updateTerm | 修改非当前学期（名称、起止日期） |
| DELETE | `/terms/{termId}` | deleteTerm | 删除学期 |
| PUT | `/current-term` | replaceCurrentTerm | 替换当前学期 |
| DELETE | `/current-term` | clearCurrentTerm | 取消当前学期 |

### 课程（13）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| POST | `/terms/{termId}/courses` | createCourse | 教师提交新建课程 |
| POST | `/courses/{courseId}/submit-review` | submitCourseReview | 提交课程审核（教学组长） |
| POST | `/courses/{courseId}/reset-to-application` | resetCourseToApplication | 将开放课程改回申请状态（管理员） |
| POST | `/courses/{courseId}/review` | reviewCourse | 审核待审核课程（管理员） |
| GET | `/courses/{courseId}` | getCourse | 课程详情 |
| PATCH | `/courses/{courseId}` | updateCourse | 修改课程（名称、代码、描述） |
| DELETE | `/courses/{courseId}` | deleteCourse | 删除课程 |
| PATCH | `/courses/{courseId}/description` | updateCourseDescription | 修改课程描述（教学组长） |
| PUT | `/courses/{courseId}/leaders` | setCourseLeaders | 指定 / 更换课程组长（教学组长） |
| POST | `/courses/copy` | copyCourse | 课程组长或具备课程拷贝权限的用户拷贝一门课程 |
| GET | `/me/courses` | listStudentCourses | 学生的我的课程 |
| GET | `/admin/courses` | listAdminCourses | 管理员所有课程列表 |
| GET | `/teacher/courses` | listTeacherCourses | 教师的我的课程 |

### 教学班（5）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| GET | `/courses/{courseId}/classes` | listClasses | 教学班列表 |
| POST | `/courses/{courseId}/classes` | createClasses | 批量建立教学班（教学组长） |
| PATCH | `/classes/{classId}` | updateClass | 修改教学班（改名） |
| DELETE | `/classes/{classId}` | deleteClass | 删除教学班 |
| PUT | `/classes/{classId}/teachers` | setClassTeachers | 指派 / 更换任课教师 |

### 学生名单（5）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| GET | `/classes/{classId}/students` | listClassStudents | 本班学生名单 |
| POST | `/classes/{classId}/students` | addStudent | 手工添加学生 |
| GET | `/classes/{classId}/student-candidates` | searchStudentCandidates | 按学号或姓名查询学生 |
| DELETE | `/classes/{classId}/students/{studentId}` | removeStudent | 从本班名单移除学生 |
| POST | `/classes/{classId}/roster/import` | importRoster | 导入选课名单（Excel） |

### 分组规则（5）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| GET | `/courses/{courseId}/group-rule` | getCourseGroupRule | 课程级分组规则（默认值） |
| PATCH | `/courses/{courseId}/group-rule` | updateCourseGroupRule | 修改课程级分组规则 |
| GET | `/classes/{classId}/group-rule` | getClassGroupRule | 本班生效的分组规则 |
| POST | `/classes/{classId}/group-rule` | setClassGroupRule | 班级分组规则 |
| DELETE | `/classes/{classId}/group-rule` | deleteClassGroupRule | 删除班级分组规则 |

### 小组（14）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| GET | `/courses/{courseId}/groups` | listCourseGroups | 本课程师生查看课程里的所有小组 |
| POST | `/courses/{courseId}/groups` | createGroupAsStudent | 本课程学生建组（自己成为组长） |
| GET | `/classes/{classId}/groups` | listClassGroups | 本班负责的小组（仅本课程教师） |
| POST | `/classes/{classId}/groups` | createGroupAsTeacher | 本班任课教师新建小组 |
| GET | `/groups/{groupId}` | getGroup | 小组详情（仅本课程师生） |
| PATCH | `/groups/{groupId}` | updateGroup | 教师编辑小组（改名 / 直接增删成员） |
| DELETE | `/groups/{groupId}` | dissolveGroup | 解散小组（物理删除，当前组长或本班任课教师） |
| GET | `/groups/{groupId}/join-requests` | listGroupJoinRequests | 加入申请列表（组长） |
| POST | `/groups/{groupId}/join-requests` | createGroupJoinRequest | 提交加入申请（学生） |
| PATCH | `/join-requests/{requestId}` | decideGroupJoinRequest | 审批加入申请（组长） |
| DELETE | `/join-requests/{requestId}` | cancelGroupJoinRequest | 撤回加入申请（学生） |
| DELETE | `/groups/{groupId}/members/{studentId}` | removeGroupMemberAsLeader | 组长移除组员 |
| PUT | `/groups/{groupId}/leader` | transferGroupLeader | 转交组长（当前组长） |
| POST | `/groups/{groupId}/leave` | leaveGroup | 学生退出小组 |

### 里程碑（4）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| GET | `/courses/{courseId}/milestones` | listMilestones | 课程里程碑列表 |
| POST | `/courses/{courseId}/milestones` | createMilestone | 新增里程碑 |
| PATCH | `/milestones/{milestoneId}` | updateMilestone | 修改里程碑 |
| DELETE | `/milestones/{milestoneId}` | deleteMilestone | 删除里程碑 |

### 提交与检查（7）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| GET | `/classes/{classId}/milestones/{milestoneId}/submissions` | listSubmissions | 提交列表（教师检查页） |
| GET | `/groups/{groupId}/milestones/{milestoneId}/submission` | getGroupSubmission | 某个组在这个里程碑上的提交与检查记录（仅分组制） |
| POST | `/groups/{groupId}/milestones/{milestoneId}/submissions` | submitMaterials | 小组提交 / 重新提交材料（仅分组制） |
| GET | `/me/courses/{courseId}/milestones/{milestoneId}/submission` | getMyMilestoneSubmission | 我在这个里程碑上的提交与检查记录（个人制） |
| POST | `/me/courses/{courseId}/milestones/{milestoneId}/submissions` | submitMyMaterials | 个人提交 / 重新提交材料（个人制） |
| GET | `/submissions/{submissionId}` | getSubmissionById | 按提交 id 读取详情 |
| POST | `/submissions/{submissionId}/review` | reviewSubmission | 批改（通过 / 驳回 + 评语 + 检查对象分数） |

### 口试与预约（10）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| GET | `/classes/{classId}/milestones/{milestoneId}/oral-slots` | listOralSlots | 已发布的口试时段 |
| POST | `/classes/{classId}/milestones/{milestoneId}/oral-slots` | createOralSlot | 发布口试时段（本班任课教师） |
| DELETE | `/oral-slots/{slotId}` | deleteOralSlot | 删除已发布的时段（本班任课教师） |
| POST | `/oral-slots/{slotId}/bookings` | bookOralSlot | 预约或改约口试时段（本班学生） |
| DELETE | `/bookings/{bookingId}` | cancelBooking | 取消预约（本组组员 / 学生本人） |
| GET | `/classes/{classId}/milestones/{milestoneId}/oral-status` | listOralStatus | 检查状态（教师口试页下半部分） |
| POST | `/milestones/{milestoneId}/groups/{groupId}/oral-records` | createGroupOralRecord | 记录小组口试检查结果 |
| POST | `/milestones/{milestoneId}/students/{studentId}/oral-records` | createStudentOralRecord | 记录个人口试检查结果 |
| PATCH | `/oral-records/{oralRecordId}` | updateOralRecord | 修改已完成的口试检查结果 |
| POST | `/oral-records/{oralRecordId}/memo-summary` | summarizeOralMemo | 录音转文字摘要（口试备忘录） |

### 成绩（3）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| GET | `/classes/{classId}/grades` | getClassGrades | 成绩汇总（教师：学生 × 里程碑） |
| POST | `/classes/{classId}/grades/export` | exportClassGrades | 导出成绩表（Excel） |
| GET | `/me/courses/{courseId}/grades` | getMyGrades | 学生的「我的成绩」（一行一个里程碑） |

### 文件与审计（2）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| GET | `/files/{fileId}` | downloadFile | 下载提交材料 |
| GET | `/audit-logs` | listAuditLogs | 日志查询 |

### 教师（4）

| 方法 | 路径 | operationId | 摘要 |
| --- | --- | --- | --- |
| GET | `/teachers` | listTeachers | 教师列表（管理员） |
| POST | `/teachers` | createTeacher | 添加教师 |
| PATCH | `/teachers/{teacherId}` | updateTeacher | 修改教师信息 |
| DELETE | `/teachers/{teacherId}` | deleteTeacher | 删除教师 |

## 请求模型、参数、状态码与源码

离线页面的 API 页签可搜索并展开每个操作，查看参数、请求模型完整结构、响应模型与 Controller 来源。机器可读提取见 [JSON](data/openapi-operations.json)，可筛选表格见 [CSV](data/openapi-operations.csv)。

## 路由核对

按 HTTP 方法及规范化路径匹配两版 Controller，所有非框架端点均应找到对应声明；health/info 由 Actuator 自动注册。该检查不证明运行时授权或业务行为正确。

| 操作 | 结构化显式匹配 | DDD 显式匹配 | 解释 |
| --- | --- | --- | --- |
| getHealth | false | false | Actuator 框架注册；另核对 application.yml 暴露配置 |
| getInfo | false | false | Actuator 框架注册；另核对 application.yml 暴露配置 |
