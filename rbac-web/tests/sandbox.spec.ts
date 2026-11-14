import { it, expect, vi } from 'vitest'
import { EventEmitter } from 'node:events'
import type { ChildProcess } from 'node:child_process'
import { systemEnvironment, springConfiguration, java21, ownProcess, stopOwned } from '../e2e/sandbox'
import { strictHttp } from '../e2e/http'

it('污染的上游 JVM/Spring/DB/Redis/Node 环境不会传入后端或 Vite', () => {
  const source = { PATH: 'system path', SystemRoot: 'system root', TEMP: 'temp', SPRING_APPLICATION_JSON: '{"evil":true}', SPRING_CONFIG_LOCATION: 'file:/evil', SPRING_DATASOURCE_URL: 'prod', SPRING_FLYWAY_URL: 'prod', DB_URL: 'prod', DB_PASSWORD: 'prod', RBAC_DB_URL: 'prod', RBAC_REDIS_HOST: 'prod', JAVA_TOOL_OPTIONS: '-Dspring.datasource.url=prod', JDK_JAVA_OPTIONS: '-Dspring.flyway.url=prod', _JAVA_OPTIONS: 'prod', JAVA_BIN: 'external', NODE_OPTIONS: '--require evil', VITE_SECRET: 'not-browser', RBAC_AUDIT_SERVICE_KEY: 'upstream-secret' }
  expect(systemEnvironment(source)).toEqual({ PATH: 'system path', SystemRoot: 'system root', TEMP: 'temp' })
})
it('仅自建 JSON 钉死 datasource/flyway/Redis/命名空间/JWT/秘密，不依赖后端变量别名', () => {
  const input = { module: 'rbac-ddd', database: 'rbac1_e2e_ddd_123456', apiPort: 18082, user: 'fictional', password: 'fictional-pw', bootstrap: 'fictional-bootstrap', auditKey: 'fictional-backend-only' }
  const config = JSON.parse(springConfiguration(input))
  expect(config.spring.datasource.url).toContain('jdbc:mysql://127.0.0.1:13306/rbac1_e2e_ddd_123456?')
  expect(config.spring.flyway.url).toBe(config.spring.datasource.url)
  expect(config.spring.flyway.user).toBe(config.spring.datasource.username)
  expect(config.spring.flyway.password).toBe(config.spring.datasource.password)
  expect(config.spring.data.redis).toMatchObject({ host: '127.0.0.1', port: 16379 })
  expect(config.rbac.cache.namespace).toBe('rbac-ddd:rbac1_e2e_ddd_123456')
  expect(config.rbac.jwt['allow-ephemeral']).toBe(true)
  expect(config.RBAC_BOOTSTRAP_PASSWORD).toBe(input.bootstrap)
  expect(config.rbac['audit-service-key']).toBe(input.auditKey)
  expect(() => springConfiguration({ ...input, database: 'production' })).toThrow()
  expect(() => springConfiguration({ ...input, module: 'rbac-structured' })).toThrow()
})
it('拒绝外部 JAVA_BIN 和缺失/相对的 JAVA_HOME', () => {
  expect(() => java21({ JAVA_BIN: 'java' }, {})).toThrow('禁止 JAVA_BIN')
  expect(() => java21({}, {})).toThrow('显式')
  expect(() => java21({ JAVA_HOME: 'relative' }, {})).toThrow('绝对路径')
})
function fakeChild() {
  const child = Object.assign(new EventEmitter(), { pid: 123, exitCode: null as number | null, signalCode: null as string | null, kill: vi.fn((_signal?: string) => { queueMicrotask(() => { child.exitCode = 0; child.emit('exit', 0) }); return true }) })
  return child
}
it('已退出的自有句柄不再 kill；存活句柄必须等待退出', async () => {
  const exited = fakeChild(), first = ownProcess(exited as unknown as ChildProcess)
  exited.exitCode = 0; exited.emit('exit', 0); await stopOwned(first); expect(exited.kill).not.toHaveBeenCalled()
  const live = fakeChild(), second = ownProcess(live as unknown as ChildProcess)
  await stopOwned(second); expect(live.kill).toHaveBeenCalledWith('SIGTERM'); expect(second.exited).toBe(true)
})
it('所有 E2E API fetch 显式 redirect:error，bearer 不随重定向转发', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}', { status: 200 }))
  await strictHttp().post('http://127.0.0.1:18081/auth/login', { data: { username: 'fictional' } })
  expect(fetch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ redirect: 'error', method: 'POST' }))
  fetch.mockRestore()
})
