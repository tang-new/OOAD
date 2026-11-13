import { test, expect } from '@playwright/test'

/** 验证浏览器同源代理确实选择DDD，不能仅因某个健康端点返回UP就认定切换成功。 */
test('M2/UI-01: 同一前端连接DDD骨架，但不冒充已有登录业务', async ({ page }, testInfo) => {
  const response = await page.goto('/login')
  expect(response?.status()).toBe(200)
  const health = await page.evaluate(async () => {
    const result = await fetch('/api/v1/actuator/health')
    return { status: result.status, body: await result.json() }
  })
  expect(health.status).toBe(200)
  expect(health.body.status).toBe('UP')
  expect(JSON.stringify(health.body)).toContain('"backend":"ddd"')
  expect(JSON.stringify(health.body)).toContain('"businessReady":false')
  const unimplemented = await page.evaluate(async () => (
    await fetch('/api/v1/auth/login', { method: 'POST' })
  ).status)
  expect(unimplemented).toBe(404)
  await page.screenshot({ path: testInfo.outputPath('ddd-skeleton.png'), fullPage: true })
})
