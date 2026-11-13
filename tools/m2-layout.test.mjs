import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const root = new URL('../', import.meta.url)
const text = (path) => readFileSync(new URL(path, root), 'utf8')

function selectModule(module) {
  return spawnSync(process.env.BASH_TEST_SHELL ?? 'bash', [
    '-c', '. ./deploy/backend-module.sh; select_backend_module && printf "%s" "$MODULE"',
  ], {
    cwd: fileURLToPath(root),
    env: { ...process.env, MODULE: module },
    encoding: 'utf8',
    timeout: 10000,
  })
}

// 本测试只检查工程和配置边界，不代替真实HTTP启动及两后端业务契约测试。
test('M2: 同一部署定义默认结构化后端，并可选择独立DDD镜像', () => {
  assert.match(text('pom.xml'), /<module>rbac-ddd<\/module>/)
  const compose = text('docker-compose.yml')
  assert.match(compose, /^  rbac-backend:/m)
  assert.doesNotMatch(compose, /^  rbac-(?:structured|ddd):/m)
  assert.match(compose, /image:.*\$\{MODULE:-rbac-structured\}/)
  assert.match(compose, /MODULE:.*\$\{MODULE:-rbac-structured\}/)
  assert.match(compose, /SERVER_PORT: ['"]?8081/)
  assert.match(compose, /depends_on: \[rbac-backend\]/)
  assert.match(text('rbac-web/nginx.conf'), /proxy_pass http:\/\/rbac-backend:8081;/)
})

test('M2: DDD不复用结构化业务或数据库缓存运行依赖', () => {
  const pom = text('rbac-ddd/pom.xml')
  assert.match(pom, /<artifactId>rbac-contract<\/artifactId>/)
  assert.doesNotMatch(pom, /<artifactId>(?:rbac-structured|spring-boot-starter-data-jpa|spring-boot-starter-data-redis|flyway-core|mysql-connector-j)<\/artifactId>/)
})

test('M2: 模块选择默认structured，显式ddd可用，非法值在副作用前拒绝', () => {
  const defaults = selectModule('')
  assert.equal(defaults.status, 0, defaults.stderr)
  assert.equal(defaults.stdout, 'rbac-structured')
  const ddd = selectModule('rbac-ddd')
  assert.equal(ddd.status, 0, ddd.stderr)
  assert.equal(ddd.stdout, 'rbac-ddd')
  const invalid = selectModule('../../arbitrary-module')
  assert.notEqual(invalid.status, 0)
  assert.equal(invalid.stdout, '')
  assert.match(invalid.stderr, /MODULE/)
})

test('M2: 前端业务调用不绑定具体后端实现', () => {
  assert.match(text('rbac-web/src/api/http.ts'), /baseURL: '\/api\/v1'/)
  assert.match(text('rbac-web/vite.config.ts'), /process\.env\.RBAC_API/)
})
