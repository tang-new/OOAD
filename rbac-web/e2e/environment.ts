import { join } from 'node:path'
export function environment() {
  const module = process.env.E2E_MODULE ?? 'rbac-structured'
  if (module !== 'rbac-structured' && module !== 'rbac-ddd') throw new Error(`拒绝未知 E2E_MODULE: ${module}`)
  const host = process.env.E2E_DB_HOST ?? '127.0.0.1'
  const port = Number(process.env.E2E_DB_PORT ?? 13306)
  if (host !== '127.0.0.1' || port !== 13306) throw new Error('E2E 仅允许 127.0.0.1:13306 专用 MySQL')
  if (process.env.E2E_DB) throw new Error('禁止指定既有测试库；globalSetup 必须创建全新库')
  if ((process.env.E2E_REDIS_HOST ?? '127.0.0.1') !== '127.0.0.1' || Number(process.env.E2E_REDIS_PORT ?? 16379) !== 16379) throw new Error('E2E Redis 仅允许 127.0.0.1:16379；不执行 FLUSHDB')
  const apiPort = Number(process.env.E2E_API_PORT ?? (module === 'rbac-structured' ? 18081 : 18082))
  const webPort = Number(process.env.E2E_WEB_PORT ?? 5174)
  const baseline = process.env.E2E_VISUAL_BASELINE === '1' ? 'http://127.0.0.1:5175' : undefined
  return { module, host, port, apiPort, webPort, api: `http://127.0.0.1:${apiPort}`, web: `http://127.0.0.1:${webPort}`, baseline, logDir: join(process.cwd(), 'test-results', 'logs') }
}
