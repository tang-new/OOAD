# RBAC1 前端与验收

## 合同和页面

已完整读取 `docs/implementation-contract.adoc`、`docs/schema-contract.adoc` 和 `docs/sources/requirements-final.md`。内部响应仅接受字符串 `OK`，分页保持 `page/pageSize/total/items`；身份协议根路径裸响应使用独立 client。

| 需求 | 页面 / 实现 | 验证 |
|---|---|---|
| ID-01/03/07、UI-01 | LoginView、MeView、auth store、双 HTTP client | auth/http/protocol/router.spec.ts |
| ID-04/05/06、ROLE-05/08 | UserWorkspace：数据库分页检索、资料编辑、独立状态/迁部门、分系统指派与来源 | console.spec.ts J01/J02 |
| ROLE-01/02/03/04 | RoleWorkspace + RoleDag SVG：直接权限、继承、多父 DAG、独立状态、明确循环错误 | components.spec.ts、console.spec.ts |
| ROLE-05/06 | CatalogWorkspace 部门树与全系统准入原子替换（保留其它系统） | console.spec.ts 不准入与迁部门回收 |
| RES-01/02/03 | ResourceWorkspace 菜单按钮 / 独立 API / 显式映射 / 覆盖报告 | console.spec.ts 真实 API、UI 与映射配置 |
| AUD-03/06 | CatalogWorkspace 审计过滤分页详情、UTC 时间 | safety.spec.ts、console.spec.ts |
| UI-02/05/06 | 原生响应式表单表格、403/404、简洁三计数首页 | desktop/mobile Playwright 项目 |

管理目标系统只是过滤，不更换管理 JWT audience。菜单、路由、按钮只做精确 UI 权限检查；identityRole=admin 不通配。所有 mutation 后刷新身份、本人有效权限和菜单；HTTP 403 显示真实错误，正常 allowed:false 不当作服务故障。

## 一期测试替换映射（非静默删安全预期）

| 旧测试/能力 | 新位置 / 处置 |
|---|---|
| http 数字 code、旧统一认证包装 | http.spec.ts、protocol.spec.ts：OK 字符串、两个 client、401/403/404/409/503、裸响应/204 |
| auth superAdmin 通配、scope/selfOnly | auth.spec.ts：精确 has、不允许 admin 通配、撤权刷新替换、会话恢复/清理 |
| router 旧菜单 componentKey/routePath | router.spec.ts：新 UiResourceVO 字段、深层菜单、菜单/按钮区分、无权深链403、未知404 |
| components 旧批量导入/公司/范围指派 | 按冻结需求退役；RecordForm 白名单、类型/密码清理、DAG多父、指令禁用/恢复保留在 components.spec.ts |
| utils 热力图/角色@范围、无时区时间 | 明确废弃；password 边界保留，UTC审计时间和权限码展示由 safety/components.spec.ts 覆盖 |
| M2 骨架切换 e2e | 删除专用 M2 config/测试，最终同一 console.spec.ts 由 E2E_MODULE 选择真实后端执行 |
| e2e Redis FLUSHDB、复用/指定数据库 | 安全 globalSetup 全新库、仅自有库清理，绝不 FLUSHDB；safety.spec.ts 断言危险输入拒绝 |

保留并复用了 `vPerm`、`PermissionCode`、密码策略工具；旧公司/范围/导入/热力图组件已不适配冻结模型，重写。ECharts 依赖及 lockfile 项已移除。覆盖配置的逻辑目录与 lines85/branches80 门槛未放宽、未添加排除。

## 运行

```sh
npm ci
npm run typecheck
npm run test:coverage
npm run build
# 默认 structured:8081；不改业务源码可切 DDD
RBAC_API=http://127.0.0.1:8082 npm run dev
# 先由后端负责人 clean package 对应 jar；环境提供 Java21。
E2E_MODULE=rbac-structured npm run e2e
E2E_MODULE=rbac-ddd npm run e2e
```

Vite dev/preview 同时代理 `/api`、`/auth`、`/.well-known`。不修改 Dockerfile/nginx.conf，容器代理由主代理处理。

E2E 使用真实 MySQL `127.0.0.1:13306`，新建 `rbac1_e2e_<module>_<timestamp>`；默认本机专用 root/root 可通过 E2E_DB_USER/PASSWORD 改。Redis 只能 `127.0.0.1:16379`，每次独立 namespace，不发清理命令。后端默认18081/18082、Vite5174。`JAVA_HOME`/`JAVA_BIN` 和 `PW_CHROMIUM` 由环境指定，源码不含机器路径。虚构强 bootstrap 密码每次生成。审计服务密钥仅给后端进程，不给浏览器。停止后只清理本次创建库和子进程。

日志在用户 Downloads/rbac1-context-extracted/m8-web-*.log；截图/trace 在 test-results，HTML 在 playwright-report。

## 本轮真实结果（2026-11-14）

- `npm run build`：成功。
- `npm run test:coverage`：初次29通过/1失败（同路径导航不会重新执行 guard 的测试前置错误），改成先离开页面再进入后30/30通过；覆盖 lines100%、branches100%，门槛不变。最终新增安全/UTC用例后32/32通过，lines100%、branches100%（6测试文件，0跳过），详见 m8-web-vitest-final.log。
- structured E2E：Java8默认路径首次启动失败；环境指向Java21后现有 jar 启动因 Flyway 重复 V1 失败。需要后端 clean package，不能以旧 target/classes 混合迁移。
- DDD E2E：现有 M2 jar health 启动成功，但根 `/auth/login` 返回404，desktop/mobile均真实失败（2失败，0跳过）。现有 jar 仍带 `/api/v1` context-path，是旧骨架，需最终后端重新打包。
- `E2E_MODULE=unknown npm run e2e`：真实启动即拒绝未知模块，无数据库/后端副作用。
- 实测上述失败 teardown 后 `SHOW DATABASES LIKE 'rbac1_e2e_%'` 返回0个，未遗留本次测试库。
- 以上 RED 不代表完整业务旅程已完成；PC/手机成功业务截图待最终两个后端 jar 可用后重跑，不把失败空白截图当可用验收。
- 未提交 Git。未改后端、部署代理文件。主代理需在后端完成后重新跑两模块完整E2E并关闭真实失败。
