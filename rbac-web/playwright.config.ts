import { defineConfig, devices } from '@playwright/test'
import { environment } from './e2e/environment'
const env = environment()
// 每次运行保留独立证据目录，重跑不能覆盖既有 RED/截图。worker 继承相同标识。
const runId = process.env.E2E_RUN_ID ??= `visual-${Date.now()}-${process.pid}`
if (!/^[A-Za-z0-9_-]+$/.test(runId)) throw new Error('非法 E2E_RUN_ID')
export default defineConfig({
  testDir: './e2e', globalSetup: './e2e/global-setup.ts', outputDir: `test-results/${env.module}/${runId}`,
  timeout: 240000, expect: { timeout: 15000 }, workers: 1, fullyParallel: false, retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: `playwright-report/${env.module}/${runId}` }]],
  use: { baseURL: env.web, actionTimeout: 15000, navigationTimeout: 30000, trace: 'retain-on-failure', screenshot: 'only-on-failure', launchOptions: process.env.PW_CHROMIUM ? { executablePath: process.env.PW_CHROMIUM } : {} },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 }, timezoneId: 'Asia/Shanghai' } },
  ],
})
