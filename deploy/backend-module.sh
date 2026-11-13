#!/usr/bin/env bash
# 只解析允许的后端模块；不读取凭据、不执行构建或部署。
select_backend_module() {
  MODULE="${MODULE:-rbac-structured}"
  case "$MODULE" in
    rbac-structured|rbac-ddd) export MODULE ;;
    *) echo "MODULE 只允许 rbac-structured 或 rbac-ddd" >&2; return 1 ;;
  esac
}
