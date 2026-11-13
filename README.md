# RBAC 统一身份与权限中心

厦门大学 OOAD 课程设计。当前正在从一期结构化 RBAC0，分阶段重构为 **结构化 + DDD 两套 RBAC1 后端**。

> **目标不等于已实现。** 当前业务后端仍是一期 RBAC0；角色 DAG、部门角色准入、课程中心七个身份/审计接口和独立 DDD 业务尚未交付。进度以测试、代码和真实提交为准，不沿用一期 README 的通过数量。

## 先读这些

| 文档 | 用途 |
|---|---|
| [当前需求与逐项追踪](docs/requirements.adoc) | 范围、需求 ID、差距、测试和六组对照设计用例 |
| [完整输入快照](docs/sources/requirements-final.md) | 用户提供的 `需求文档-final`，完整规则与验收定义 |
| [实施基线与决策](docs/migration-baseline.adoc) | 实际起始提交、工具、备份、资料缺口、迁移边界 |
| [真实迭代记录](docs/iteration-log.adoc) | 已执行修改和验证；不伪造历史 |
| [结构化设计](docs/architecture-structured.adoc) | 当前三层实现与二阶段差距 |
| [DDD 设计](docs/architecture-ddd.adoc) | 明确标注尚未实现的职责与依赖方向 |
| [数据库](docs/database.adoc) | 当前迁移与目标模型的区别 |
| [API](docs/api.adoc) | 现行契约与外部七接口目标 |
| [测试计划](docs/test-plan.adoc) | 隔离测试、安全门槛、两实现共同验收 |
| [部署](docs/deployment.adoc) | 受控本地运行、配置与远端验收缺口 |

**优先级：重构优先于迁移和兼容。** 按收缩 → 双工程布局 → 结构化模型/API重构 → 双后端 RBAC1 推进；不为废弃的多公司和业务数据范围保留复杂兼容层，也不因此擅自清空生产库。

二阶段范围：单公司、用户唯一部门、部门仅组织管理和角色准入；系统隔离；同系统角色继承 DAG；菜单/按钮与 API 独立授权及显式映射；同步审计。权限中心不实现课程业务的对象级规则，不做 RBAC2/3、热力图和风险大屏。

## 工程

```text
rbac-contract/     当前唯一 OpenAPI、Flyway 迁移
rbac-structured/   当前可运行的结构化业务后端（Java21 / Spring Boot3.3.5）
rbac-web/          Vue3 / TypeScript 前端
rbac-ddd/          双工程布局阶段新增，尚未交付业务实现
  （计划）
deploy/           构建、Swarm脚本和环境模板
docs/             当前生效需求、设计和验证记录
diagrams/         一期图源/工具；不应冒充二阶段当前代码图
tools/            工程布局回归检查
```

一期非RBAC导论实验已提取独立历史到同级 `intro-spec-coding-export/`，1176个受跟踪文件的Git tree一致。新远程仓库地址未提供，按需求先保留源目录，尚不宣称迁移完成。

## 本地验证

要求 **JDK21**（不能用默认Java8）、Node20+、Docker。wrapper下载Maven3.9.9。

**集成测试会清空所配置的测试数据库和 Redis，绝不能指向生产。**
先检查测试端口是否已占用；以下仅供创建全新隔离环境，不能用于重建已有生产实例。

```bash
docker run -d --name rbac-phase2-test-mysql -p 127.0.0.1:13306:3306 \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=rbac_test mysql:8.0
docker run -d --name rbac-phase2-test-redis -p 127.0.0.1:16379:6379 redis:7-alpine
# 等数据库健康就绪；确认 JAVA_HOME 指向 JDK21
./mvnw -B -s deploy/maven-settings-huawei.xml verify
node --test tools/project-layout.test.mjs diagrams/tools/gen-mdj.test.mjs

cd rbac-web
npm ci
npm run typecheck
npm run test:coverage
npm run build
```

Windows也可用 `mvnw.cmd` 和 PowerShell。生产密码/密钥不得复用上述测试值。
JaCoCo报告在 `rbac-structured/target/site/jacoco/index.html`，本轮未完成的测试不能引用旧报告冒充通过。

前端开发 `npm run dev`；当前默认代理到structured。双后端切换将在工程布局阶段实现并验证。

## 资料与安全

- [OpenAPI](rbac-contract/src/main/resources/openapi/rbac-api.yaml) 是当前管理接口的唯一事实来源；外部 `/auth/*` 不能用旧包装响应顶替。
- 员工原始表含真实人员信息，只能留在被忽略的 [data/raw](data/raw/README.md)，不提交、不放镜像。
- 旧版文档、审查记录和演示代码由Git历史保存；起始版本及仓库外完整bundle见实施基线。
- 新下载的37页PDF描述一期RBAC0，不是新版评分标准。评分原件、课堂OO设计PPT和课程中心部署条件仍需核验。
- 不声称远端Swarm、课程系统联调或当前UML目视验证已完成。
