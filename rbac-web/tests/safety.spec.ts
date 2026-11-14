import { it, expect, vi, afterEach } from 'vitest'
import { environment } from '../e2e/environment'
import { splitPermissionCode, toInstant } from '@/utils/format'
afterEach(() => vi.unstubAllEnvs())
it('E2E 未知模块与非专用数据库/Redis 均拒绝，不能指定既有库', () => {
  vi.stubEnv('E2E_MODULE', 'rbac-structured'); vi.stubEnv('E2E_DB_HOST', '127.0.0.1'); vi.stubEnv('E2E_DB_PORT', '13306'); vi.stubEnv('E2E_REDIS_HOST', '127.0.0.1'); vi.stubEnv('E2E_REDIS_PORT', '16379'); vi.stubEnv('E2E_DB', '')
  expect(environment().apiPort).toBe(18081)
  vi.stubEnv('E2E_MODULE', 'rbac-ddd'); expect(environment().apiPort).toBe(18082)
  vi.stubEnv('E2E_MODULE', 'unknown'); expect(environment).toThrow('未知')
  vi.stubEnv('E2E_MODULE', 'rbac-ddd'); vi.stubEnv('E2E_DB_HOST', 'prod'); expect(environment).toThrow('专用 MySQL')
  vi.stubEnv('E2E_DB_HOST', '127.0.0.1'); vi.stubEnv('E2E_DB_PORT', '3306'); expect(environment).toThrow('专用 MySQL')
  vi.stubEnv('E2E_DB_PORT', '13306'); vi.stubEnv('E2E_DB', 'production'); expect(environment).toThrow('既有测试库')
  vi.stubEnv('E2E_DB', ''); vi.stubEnv('E2E_REDIS_PORT', '6379'); expect(environment).toThrow('Redis')
})
it('保留权限码展示，审计日期必须带 UTC 时区', () => {
  expect(splitPermissionCode('a:b:c')).toEqual(['a', 'b', 'c'])
  expect(toInstant('')).toBeUndefined(); expect(toInstant('2026-01-01T01:00:00+08:00')).toBe('2025-12-31T17:00:00.000Z')
  expect(() => toInstant('invalid')).toThrow()
})
