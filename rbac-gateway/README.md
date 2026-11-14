# 正式课程 API 网关

Java 21 / Spring Boot 3.3.5，无数据库、Redis或运行时模拟身份系统。

```sh
# 尚未通过根 reactor 安装时先安装父与最新契约资源
./mvnw -N install -DskipTests
./mvnw -f rbac-contract/pom.xml install -DskipTests
./mvnw -f rbac-gateway/pom.xml -am clean verify
java -jar rbac-gateway/target/rbac-gateway.jar \
  --gateway.identity-base-url=https://identity.example \
  --gateway.course-base-url=https://course.example \
  --gateway.issuer=rbac-center \
  --gateway.jwk-set-uri=https://identity.example/.well-known/jwks.json
```

默认监听8090。基址只能是固定 origin（无用户名、路径、查询、片段）；HTTP仅可在显式 `--gateway.allow-http=true` 时使用。默认 localhost HTTP 地址同样必须显式启用，避免不知情的明文 bearer 转发。

`gateway.timeout-ms` 默认2000、范围100..30000，分别约束连接/响应头及响应体；`gateway.max-request-bytes` 与 `gateway.max-response-bytes` 默认10MiB、范围1..64MiB。身份响应也受响应体上限限制；JWKS固定64KiB。上传不启用 Servlet multipart 解析，原字节有界转发。响应缓存到有界内存后返回；不提供无限流式下载。健康/info路径为 `/api/v1/actuator/{health,info}`。

课程路由只读 rbac-contract 中 `catalog/course-operations.json` 的80个业务操作，不包含7个身份提供者操作。每次课程访问均先本地验证JWT，再真实调用中心鉴权；不缓存中心允许决定。课程对象权限始终由课程后端判定（对象403保持原字节），copyCourse只按manifest的teacher资格注册，不额外强制CC。

所有出站请求禁用重定向；入站请求头按必要字段白名单重新构建。客户端传入的operationId、system、上游URL与服务凭据均不能改变路由、权限或上游origin。技术错误使用固定错误码，不输出底层异常消息或凭据。
