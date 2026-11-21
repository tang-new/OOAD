# Docker 部署 DDD，联调本机课程系统

这份 Compose 在服务器运行一个 DDD 容器。DDD 连接已有的 MySQL 和 Redis，MySQL 可以在另一台服务器。老师原版的课程前后端和课程数据库继续在你的电脑运行。

```text
本机课程网页 /api/* → 服务器 HTTPS → DDD 鉴权 → 本机课程后端 :8084
本机课程网页 /auth/* → 服务器 HTTPS → DDD
本机课程网页 /api/login → 本机课程后端 /login → 服务器 /auth/login、/auth/me
```

## 1. 设置服务器参数和密钥

在服务器的 OOAD 仓库根目录执行：

```bash
bash deploy/ddd-server-secrets.sh
cp -n deploy/ddd-server.env.example .runtime/ddd-server/server.env
```

编辑 `.runtime/ddd-server/server.env`：

| 参数 | 填写内容 |
| --- | --- |
| `DDD_SERVER_DB_URL`、`DDD_SERVER_DB_USER` | 远端 MySQL 的 JDBC 地址和应用用户名。数据库要事先创建，应用用户名要有该库的查询和 Flyway 迁移权限。 |
| `DDD_SERVER_REDIS_HOST`、`DDD_SERVER_REDIS_PORT` | 已有 Redis 的地址和端口。需要认证或 TLS 时设置 `DDD_SERVER_REDIS_PASSWORD` 或 `DDD_SERVER_REDIS_TLS`。 |
| `DDD_SERVER_ORIGIN` | 浏览器和老师的 Java 后端实际使用的入口，例如服务器已有反代的 `https://rbac.example.edu`。 |
| `DDD_SERVER_BIND_ADDRESS`、`DDD_SERVER_HTTP_PORT` | 默认在服务器发布 `127.0.0.1:8083`，供同机反向代理访问。走 VPN 直接联调时也可绑定服务器的 VPN 地址，并把 `DDD_SERVER_ORIGIN` 设为相应的私网 URL。 |
| `COURSE_UPSTREAM` | DDD **容器能访问**的本机课程后端根地址，例如 `http://10.20.0.24:8084`。使用实际 VPN 或私网地址。 |

首次部署，把现有 MySQL 应用账号的密码写入 `.runtime/ddd-server/center-db-password`。例如在 Bash 中输入：

```bash
read -rsp 'MySQL 应用账号密码: ' db_password
(umask 077; set -o noclobber; printf '%s' "$db_password" > .runtime/ddd-server/center-db-password)
unset db_password
```

密钥脚本生成首次登录口令和 RSA 密钥。公网 HTTPS 可以由服务器已有的反向代理终止，将域名的请求转发到本机 `127.0.0.1:8083`，保留 `Authorization` 请求头。使用受浏览器和 Java 信任的证书。

## 2. 构建并启动

使用 JDK 21，先在构建机准备 DDD JAR：

```bash
bash tools/install-clonefactory.sh
./mvnw -B -ntp -pl rbac-ddd -am package -DskipTests
```

如果构建和部署是同一台服务器，从仓库根目录执行：

```bash
docker compose --env-file .runtime/ddd-server/server.env -f deploy/ddd-server.compose.yml up -d --build --wait
docker compose --env-file .runtime/ddd-server/server.env -f deploy/ddd-server.compose.yml ps
```

如果在另一台机器构建，将 DDD 镜像推送到镜像仓库。服务器在 `server.env` 用 `DDD_SERVER_IMAGE` 指定镜像，执行：

```bash
docker compose --env-file .runtime/ddd-server/server.env -f deploy/ddd-server.compose.yml pull
docker compose --env-file .runtime/ddd-server/server.env -f deploy/ddd-server.compose.yml up -d --no-build --wait
```

用 `DDD_SERVER_ORIGIN` 下的 `/api/v1/actuator/health` 检查启动。空库首次初始化的管理员是 `root`，初始口令在 `.runtime/ddd-server/bootstrap-password`。已有库继续使用原来的管理员账号。

## 3. 配置本机老师项目

- 老师后端继续用本机课程库和 `8084`，将 IDEA 环境变量 `IDENTITY_BASE_URL` 改为服务器的 `DDD_SERVER_ORIGIN`。
- 使用已配置代理的老师前端或 OOAD 工作台，在 IDEA 的 npm 环境变量里设 `VITE_RBAC_PROXY_TARGET` 为同一个 HTTPS 地址。登录继续指向本机 `8084`。
- 服务器权限中心要有课程角色和对应账号。若本机课程名单已经绑定某个 `subject`，服务器账号须使用同一个标识，或更新本机名单的绑定。

部署后，从 DDD 容器检查反向网络路径，再用本机课程网页登录并查询课程：

```bash
docker compose --env-file .runtime/ddd-server/server.env -f deploy/ddd-server.compose.yml exec rbac-backend \
  sh -c 'curl -fsS --max-time 5 "${COURSE_UPSTREAM%/}/actuator/health"'
```

如果能登录但查询课程时报 `503 COURSE_UNAVAILABLE`，先检查这个探测。登录验证了本机到服务器的方向，业务转发还需要服务器能回到本机。
