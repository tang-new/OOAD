import assert from 'node:assert/strict'
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { delimiter, join } from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const root = fileURLToPath(new URL('../', import.meta.url))

/** 复制脚本到一次性目录，所有Docker/Maven命令均由本地替身记录，绝不读取真实.env或部署。 */
function fixture(t, module = '') {
  const dir = mkdtempSync(join(tmpdir(), 'rbac-m2-scripts-'))
  t.after(() => rmSync(dir, { recursive: true, force: true }))
  mkdirSync(join(dir, 'deploy'))
  mkdirSync(join(dir, 'bin'))
  for (const file of ['build.sh', 'stack.sh', 'backend-module.sh']) {
    copyFileSync(join(root, 'deploy', file), join(dir, 'deploy', file))
  }
  const log = join(dir, 'calls.log')
  writeFileSync(join(dir, '.env'), [
    `MODULE=${module}`, 'REGISTRY=fixture.invalid/from-env/', 'TAG=fixture-tag',
    'MYSQL_ROOT_PASSWORD=fixture-root-38', 'DB_PASSWORD=fixture-app-91',
    'RBAC_JWT_SECRET=fixture-only-not-a-real-secret-12345678901234567890',
  ].join('\n'))
  writeFileSync(join(dir, 'bin', 'docker'), [
    '#!/usr/bin/env bash',
    'printf "docker %s\\n" "$*" >> "$CALL_LOG"',
    'if [ "$1 ${2:-}" = "service inspect" ]; then',
    '  [ "${LEGACY_SERVICE:-0}" = 1 ] && exit 0',
    '  exit 1',
    'fi',
    'exit 0',
    '',
  ].join('\n'), { mode: 0o755 })
  writeFileSync(join(dir, 'mvnw'), '#!/usr/bin/env bash\nprintf "maven %s\\n" "$*" >> "$CALL_LOG"\n', { mode: 0o755 })
  return {
    run(script, args = [], env = {}) {
      return spawnSync(process.env.BASH_TEST_SHELL ?? 'bash', [
        join(dir, 'deploy', script).replaceAll('\\', '/'), ...args,
      ], {
        cwd: dir,
        env: { ...process.env, MODULE: '', REGISTRY: '', TAG: '', PATH: join(dir, 'bin') + delimiter + process.env.PATH, CALL_LOG: log, ...env },
        encoding: 'utf8',
        timeout: 20000,
      })
    },
    calls: () => existsSync(log) ? readFileSync(log, 'utf8') : '',
  }
}

test('M2: build默认structured且只构建所选后端与共用前端', (t) => {
  const f = fixture(t)
  const result = f.run('build.sh')
  assert.equal(result.status, 0, result.stderr)
  assert.match(f.calls(), /--build-arg MODULE=rbac-structured/)
  assert.doesNotMatch(f.calls(), /MODULE=rbac-ddd/)
  assert.match(f.calls(), /rbac-web:fixture-tag/)
})

test('M2: 显式MODULE与镜像配置不被.env覆盖，push也选择正确镜像', (t) => {
  const f = fixture(t, 'rbac-structured')
  const result = f.run('build.sh', ['push'], { MODULE: 'rbac-ddd', REGISTRY: 'fixture.invalid/explicit/', TAG: 'explicit-tag' })
  assert.equal(result.status, 0, result.stderr)
  assert.match(f.calls(), /--build-arg MODULE=rbac-ddd/)
  assert.match(f.calls(), /push fixture\.invalid\/explicit\/rbac-ddd:explicit-tag/)
  assert.doesNotMatch(f.calls(), /push .*rbac-structured/)
})

test('M2: 非法MODULE在任何构建或部署副作用前拒绝', (t) => {
  const f = fixture(t, 'rbac-structured')
  for (const script of ['build.sh', 'stack.sh']) {
    const result = f.run(script, script === 'stack.sh' ? ['up'] : [], { MODULE: '../../other' })
    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /MODULE/)
  }
  assert.equal(f.calls(), '')
})

test('M2: stack保留显式MODULE且检测到旧服务时不自动部署或删除', (t) => {
  const f = fixture(t, 'rbac-structured')
  const result = f.run('stack.sh', ['up'], { MODULE: 'rbac-ddd', REGISTRY: 'fixture.invalid/explicit/', TAG: 'explicit-tag', LEGACY_SERVICE: '1' })
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /旧服务/)
  assert.doesNotMatch(f.calls(), /stack deploy|service rm|stack rm|volume rm/)
})

test('M2: stack选择与build一致的显式镜像，不触及真实Docker', (t) => {
  const f = fixture(t, 'rbac-structured')
  const result = f.run('stack.sh', ['up'], { MODULE: 'rbac-ddd', REGISTRY: 'fixture.invalid/explicit/', TAG: 'explicit-tag', LEGACY_SERVICE: '0' })
  assert.equal(result.status, 0, result.stderr)
  assert.match(result.stdout, /fixture\.invalid\/explicit\/rbac-ddd:explicit-tag/)
  assert.match(f.calls(), /stack deploy -c docker-compose.yml/)
})
