import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 开发时把 /api 代理到权限中心；部署时由 Nginx 做同样的转发（rbac-web/nginx.conf）
const backend = process.env.RBAC_API ?? 'http://localhost:8081'
const proxy = Object.fromEntries(
  ['^/api(/|$)', '^/auth(/|$)', '^/\\.well-known(/|$)'].map(path => [path, { target: backend, changeOrigin: true }]),
)

export default defineConfig({
  plugins: [vue()],
  cacheDir: process.env.RBAC_VITE_CACHE_DIR,
  optimizeDeps: { include: ['naive-ui', '@vue-flow/core', 'elkjs/lib/elk-api'] },
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: Number(process.env.WEB_PORT ?? 5173),
    proxy,
  },
  preview: {
    port: Number(process.env.WEB_PORT ?? 4173),
    proxy,
  },
  build: {
    chunkSizeWarningLimit: 1500,
  },
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.spec.ts'],
    setupFiles: ['tests/setup.ts'],
    css: false,
    testTimeout: 30_000,
    coverage: {
      provider: 'v8',
      // 单测覆盖逻辑层（请求与错误处理、登录与权限状态、路由、指令、格式化）；
      // 页面由 Playwright 端到端测试连真实后端覆盖（e2e/），见 docs/test-plan.adoc
      include: ['src/{api,stores,router,utils,directives}/**/*.ts'],
      reporter: ['text-summary', 'html', 'lcov'],
      thresholds: { lines: 85, branches: 80 },
    },
  },
})
