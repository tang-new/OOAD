import { defineConfig, devices } from '@playwright/test'
import { environment } from './e2e/environment'
const env = environment()
export default defineConfig({
  testDir: './e2e', globalSetup: './e2e/global-setup.ts',
  timeout: 120000, expect: { timeout: 15000 }, workers: 1, fullyParallel: false, retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: { baseURL: env.web, trace: 'retain-on-failure', screenshot: 'only-on-failure', launchOptions: process.env.PW_CHROMIUM ? { executablePath: process.env.PW_CHROMIUM } : {} },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1000 } } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
})
