# RBAC 统一身份与权限中心

厦门大学 OOAD 课程设计：**结构化 + 独立 DDD 两套 RBAC1 后端**，共用严格契约和同一 Vue3 权限中心；真实教师课程系统经固定上游网关接入。

> **本地交付验收已通过**：188项后端JUnit、双后端25+25及T21共51项真库验收、38项前端单测、PC/手机4例、教师原440项与真实课程12例。两轮七服务本地Swarm验证完成，最终DDD。完整范围、hash和未执行的外部事项见[最终验收](docs/final-acceptance.adoc)，不把本地通过冒充外部服务器或GitHub CI成功。

**重构优先于迁移和兼容。** 单公司、用户唯一部门；部门用于组织管理和角色准入，不表示课程业务数据范围。同系统角色继承 DAG；UI/API 独立授权及显式映射；同步审计。课程对象级权限仍由真实课程 Service 执行，不做 RBAC2/3、热力图或旧协议兼容层。

## 文档入口

| 文档 | 用途 |
|---|---|
| [需求与追踪](docs/requirements.adoc) / [完整输入](docs/sources/requirements-final.md) | 需求 ID、范围及验收定义 |
| [实现合同](docs/implementation-contract.adoc) / [数据库合同](docs/schema-contract.adoc) | 两实现共同遵守的行为与十五表新基线 |
| [课程集成合同](docs/course-integration-contract.adoc) | 教师身份/审计接口、真实 CC 与服务端边界 |
| [结构化设计](docs/architecture-structured.adoc) / [DDD 设计](docs/architecture-ddd.adoc) | 实际分层、事务与独立领域模型 |
| [最终设计与 UML](docs/final-design.adoc) / [答辩](docs/defense.adoc) | 六组对应类图/顺序图、图源与源码索引 |
| [数据库](docs/database.adoc) / [API](docs/api.adoc) | 当前唯一契约、种子及安全边界 |
| [最终验收](docs/final-acceptance.adoc) / [测试证据](docs/test-plan.adoc) / [共享验收](docs/acceptance-plan.adoc) | 真实结果、双实例、故障注入及行为等价 |
| [浏览器截图](docs/acceptance/screenshots/README.adoc) | 两后端PC/手机真实已加载页面，不含真实人员或秘密 |
| [课程验证](course-integration/VERIFICATION.md) | 固定教师源码、440项原测试、边界测试与真实联调状态 |
| [部署](docs/deployment.adoc) / [部署证据](deploy/deployment-evidence.json) | 隔离 Swarm、TLS、秘密、真实镜像与验收限制 |
| [基线](docs/migration-baseline.adoc) / [迭代记录](docs/iteration-log.adoc) | 起始版本、保全备份及真实阶段结果 |

## 工程

```text
rbac-contract/      唯一 OpenAPI、V1/V2、课程80操作目录；不含共享业务实现
rbac-structured/    Controller → 无状态 Service → Spring Data/JPA/Redis Mapper
rbac-ddd/           独立纯JDK领域、应用编排、端口、JPA/Redis适配器及REST
rbac-gateway/       JWT/JWKS、逐请求中心决策、真实路由匹配和固定课程上游
rbac-web/           同一 Vue3/TypeScript 权限中心，支持选择任一后端
course-integration/ 固定教师源码的补丁/覆盖层、真实课程前端及测试
                                   （不复制完整上游仓库或真实人员名单）
deploy/            同一服务名选择后端、七服务隔离部署和验证工具
docs/design/       当前可维护图源、SVG/PNG与源码/方法索引
tools/             合同生成、CloneFactory、共享真库验收和设计核验
```

权限中心均为 Java21 / Spring Boot3.3.5 / JPA / MySQL8 / Redis7。教师工程保留其固定版本和 MIT 许可，不伪装为我方重新实现的课程业务。
共享契约目前为 **64个中心操作、80个课程操作、15张表**；两个额外私有身份投影不向浏览器开放。

非RBAC导论实验已迁入独立私有仓库 [vintcessun/intro-spec-coding](https://github.com/vintcessun/intro-spec-coding)。原四条历史及1176个文件的初始 tree `29c24ee631060a8d208350ad65ac2b294b25ce95` 已与远端核验；后续提交 `381e498` 修正独立说明和凭据忽略规则。原目录退出主仓库，被忽略的私有资产保全在仓库外。

## 本地验证

要求 **显式 JDK21**、Node22+、Python3、Docker；wrapper提供 Maven3.9.9。绘图另需 Graphviz。

**只可使用专用 loopback 测试实例，绝不能指向生产。新V1/V2只能用于空开发库，禁止覆写生产旧迁移历史。**
先检查13306/16379及容器名称没有被其它用途占用；下列命令不用于重建已有实例：

```bash
docker run -d --name rbac-phase2-test-mysql -p 127.0.0.1:13306:3306 \
  -e MYSQL_ROOT_PASSWORD=root mysql:8.0
docker run -d --name rbac-phase2-test-redis -p 127.0.0.1:16379:6379 redis:7-alpine
# 等健康就绪，并设置 JAVA_HOME 为 JDK21；测试密码仅限这两个专用实例。
bash tools/install-clonefactory.sh
# Python 合同校验依赖 PyYAML/openapi-spec-validator，详见工具文件头。
python tools/generate-rbac1-contract.py --check
python tools/generate-rbac1-contract-test.py
node --test tools/*.test.mjs

# 正常跑JUnit并打包；外部HTTP覆盖收集后才执行原覆盖率门槛。
./mvnw -B -s deploy/maven-settings-huawei.xml clean package
source deploy/full-jacoco-agent.sh -s deploy/maven-settings-huawei.xml
export ACCEPTANCE_DB_PASSWORD='<专用测试实例凭据>'
(cd tools/acceptance && npm ci && npm test && npm run acceptance)
./mvnw -B -s deploy/maven-settings-huawei.xml verify -DskipTests

cd rbac-web
npm ci
npm run typecheck
npm run test:coverage
npm run build
E2E_MODULE=rbac-structured npm run e2e
E2E_MODULE=rbac-ddd npm run e2e
```

shared HTTP 使用本次唯一新库、私有临时 jar 快照和自有 Redis namespace，不清空共享实例。覆盖率经官方 JaCoCo TCP dump 后与JUnit合并，**不降低门槛、不扩大排除**；中间不能clean。Windows需 Git Bash / `mvnw.cmd`，Playwright可通过 `PW_CHROMIUM` 指定本机 Chrome。

前端开发默认代理structured8081；`RBAC_API=http://127.0.0.1:8082 npm run dev` 可连接DDD。真实课程准备与复现见 `course-integration/`，不能把stub边界测试当作两中心真实联调。

## 部署与安全

`MODULE=rbac-structured|rbac-ddd` 选择一个中心后端；前端经稳定服务名 `rbac-backend` 代理。七服务为中心前后端、课程前后端、网关、MySQL、Redis；只在两边缘发布TLS，数据库及业务后端不公开端口。

运行密钥、数据库口令及首次bootstrap密码由被忽略的运行目录和Swarm secrets提供；没有固定公开root密码。bootstrap只用于未初始化root；应在首次使用前改密，改密会失效旧令牌。本地自签TLS不等于公网可信证书。

- [OpenAPI](rbac-contract/src/main/resources/openapi/rbac-api.yaml) 是唯一接口事实来源；外部 `/auth/*` 保留教师裸响应。
- `/integration/*`、`/identity/*` 仅用于可信服务，需 bearer + 服务密钥，公共边缘拒绝访问；服务密钥不能进入前端。
- 原始员工表只留在被忽略的 [data/raw](data/raw/README.md)，不提交、不打镜像。
- 旧版文档、审查运行目录和演示代码由Git历史保存，不恢复多公司或业务scope模型。
- 用户提供的37页PDF是一期RBAC0资料；未收到的新版评分原件/课堂PPT以及外部服务器条件不冒称已验收。
- 部署工具不自动删除旧服务或数据卷；本地Swarm证据不能冒充外部生产部署。
- [依赖安全核验](docs/dependency-security.adoc)：GitHub实际告警78→1个开发依赖medium，high/critical为0；npm仍计5个dev-only moderate，无兼容修复，明确保留而非假清零。
