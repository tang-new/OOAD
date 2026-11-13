#!/usr/bin/env bash
# Swarm 集群部署。对应实验二第四部分，命令都在 manager 节点（编译服务器）的仓库根目录执行。
# 每一步做什么、为什么，见 docs/deployment.adoc。
#
#   bash deploy/stack.sh init <本机内网IP>           建集群，打印给工作节点执行的 join 命令
#   bash deploy/stack.sh label <数据节点> <应用节点>   按主机名打 rbac.role 标签（docker node ls 看主机名）
#   bash deploy/stack.sh up                          读 .env、检查口令、部署或更新
#   bash deploy/stack.sh ps                          各服务副本数与每个任务在哪台机器上
#   bash deploy/stack.sh logs [服务名]               默认 rbac-backend
#   bash deploy/stack.sh down                        删除整个栈（数据卷保留）
set -euo pipefail
cd "$(dirname "$0")/.."

STACK="${STACK:-rbac}"
. ./deploy/backend-module.sh

load_env() {
  if [ ! -f .env ]; then
    echo "缺少 .env：cp deploy/env.example .env 后改掉里面的口令" >&2
    exit 1
  fi
  # docker stack deploy 不读 .env（docker compose 才读），所以先导出成环境变量。
  # 显式环境优先于.env；空值最后回退默认配置。
  local _tag="${TAG-}" _module="${MODULE-}" _registry="${REGISTRY-}"
  set -a; . ./.env; set +a
  TAG="${_tag:-${TAG-}}"
  MODULE="${_module:-${MODULE-}}"
  REGISTRY="${_registry:-${REGISTRY-}}"
  select_backend_module
}

# 实验二要求每组数据库口令不同：拒绝模板值和本机开发用的默认值
check_secrets() {
  local bad=0 name value
  for name in MYSQL_ROOT_PASSWORD DB_PASSWORD; do
    value="${!name:-}"
    case "$value" in
      ""|*change-me*|root|rbac|123456|demouser)
        echo "✗ $name 还是空值、模板值或常见弱口令，先改 .env" >&2; bad=1 ;;
    esac
  done
  if [ "${MYSQL_ROOT_PASSWORD:-}" = "${DB_PASSWORD:-}" ]; then
    echo "✗ MYSQL_ROOT_PASSWORD 与 DB_PASSWORD 不要相同：应用只拿得到 DB_PASSWORD" >&2; bad=1
  fi
  case "${RBAC_JWT_SECRET:-}" in
    *change-me*|dev-only-*) echo "✗ RBAC_JWT_SECRET 还是模板值" >&2; bad=1 ;;
  esac
  local secret="${RBAC_JWT_SECRET:-}"
  if [ "${#secret}" -lt 32 ]; then
    echo "✗ RBAC_JWT_SECRET 不足 32 字节（openssl rand -base64 48）" >&2; bad=1
  fi
  if [ -z "${REGISTRY:-}" ]; then
    echo "✗ REGISTRY 为空：集群里其他节点拉不到只在本机的镜像，填 SWR 地址" >&2; bad=1
  fi
  [ "$bad" = 0 ] || exit 1
}

cmd="${1:-}"
case "$cmd" in
  init)
    ip="${2:?用法：stack.sh init <本机内网IP>（ip addr 或华为云控制台「私有IP」）}"
    docker swarm init --advertise-addr "$ip"
    echo
    echo "把上面那条 docker swarm join ... 命令复制到两台部署服务器上执行。"
    echo "token 每次建集群都不同，不要用历史记录里旧的；忘了就：docker swarm join-token worker"
    ;;
  label)
    data="${2:?用法：stack.sh label <数据节点主机名> <应用节点主机名>}"
    app="${3:?用法：stack.sh label <数据节点主机名> <应用节点主机名>}"
    docker node update --label-add rbac.role=data "$data"
    docker node update --label-add rbac.role=app "$app"
    for n in "$data" "$app"; do
      printf '%-20s %s\n' "$n" "$(docker node inspect "$n" --format '{{json .Spec.Labels}}')"
    done
    ;;
  up)
    load_env
    check_secrets
    TAG="${TAG:-latest}"
    export TAG
    # 服务改名不能自动遗留旧写入节点或造成8081端口冲突；需受控维护迁移。
    if docker service inspect "${STACK}_rbac-structured" >/dev/null 2>&1; then
      echo "发现一期旧服务；请先批准维护窗口并核验旧服务已停，再部署rbac-backend。不会自动删服务或清库。" >&2
      exit 1
    fi
    echo "==> 部署栈 $STACK，镜像 ${REGISTRY}${MODULE}:${TAG}"
    # --with-registry-auth：把本机 docker login 的 SWR 凭证转给工作节点，否则它们拉不到私有镜像
    docker stack deploy -c docker-compose.yml --with-registry-auth "$STACK"
    echo
    if [ "$MODULE" = rbac-structured ]; then
      echo "首次部署需等待MySQL初始化；应用可能失败重启，必须核对最终健康状态。"
    else
      echo "DDD当前仅为健康骨架，businessReady=false，不具备登录或授权业务。"
    fi
    echo "查看进度：bash deploy/stack.sh ps"
    ;;
  ps)
    docker stack services "$STACK"
    echo
    docker stack ps "$STACK" --format 'table {{.Name}}\t{{.Node}}\t{{.CurrentState}}\t{{.Error}}'
    ;;
  logs)
    docker service logs --tail 100 -f "${STACK}_${2:-rbac-backend}"
    ;;
  down)
    # stack rm 是异步的：命令返回时容器可能还在停。再次 up 之前先确认 ps 已经为空
    docker stack rm "$STACK"
    echo "数据卷 ${STACK}_mysql-data 保留；禁止将删除数据卷当作发布或回滚步骤。"
    ;;
  *)
    sed -n '2,10p' "$0"
    exit 1
    ;;
esac
