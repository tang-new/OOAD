import { defineConfig, devices } from '@playwright/test'
import { join } from 'node:path'

/** M2只检查同一前端代理到真实DDD骨架，不清库、不运行尚未实现的业务套件。 */
const apiPort = Number(process.env.M2_API_PORT ?? 18082)
const webPort = Number(process.env.M2_WEB_PORT ?? 5175)
const java = process.env.JAVA_BIN ?? (process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java')

export default defineConfig({
  testDir: './e2e-m2',
  outputDir: 'test-results/m2',
  fullyParallel: false,
  workers: 1,
  use: {
    baseURL: `http://127.0.0.1:${webPort}`,
    trace: 'retain-on-failure',
    ...(process.env.PW_CHROMIUM ? { launchOptions: { executablePath: process.env.PW_CHROMIUM } } : {}),
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
  webServer: [
    {
      command: `"${java}" -jar ../rbac-ddd/target/rbac-ddd.jar`,
      url: `http://127.0.0.1:${apiPort}/api/v1/actuator/health`,
      timeout: 120_000,
      reuseExistingServer: false,
      env: { SERVER_ADDRESS: '127.0.0.1', SERVER_PORT: String(apiPort) },
    },
    {
      command: 'npm run dev -- --host 127.0.0.1',
      url: `http://127.0.0.1:${webPort}`,
      timeout: 60_000,
      reuseExistingServer: false,
      env: { RBAC_API: `http://127.0.0.1:${apiPort}`, WEB_PORT: String(webPort) },
    },
  ],
})
