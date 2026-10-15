import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: { chunkSizeWarningLimit: 600 },
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // e2e/ 必须显式排除：Vitest 默认的 include glob 会把 `*.spec.ts` 也收进来，
    // 于是 Playwright 的 test() 在 Vitest 环境里被调用并报「did not expect test() here」。
    exclude: ['**/node_modules/**', '**/dist/**', 'e2e/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      // 只统计真正承载逻辑的文件。视图层（views/）大量是模板与事件绑定，
      // 单测覆盖它收益很低却很吵，因此交给 Playwright 冒烟用例去覆盖。
      include: ['src/api/**', 'src/composables/**', 'src/components/**', 'src/stores/**'],
      // 门禁贴着当前实测值往下留 8~12 个百分点：低于它就说明有新逻辑完全没被测到，
      // 而不是覆盖率在正常范围内波动。实测：语句 81.5 / 分支 81.3 / 函数 76.1 / 行 82.9。
      // 留得比一般项目宽，是因为 views/ 与 PackingCanvas 这类模板密集的文件本来就低，
      // 门禁卡太紧会逼着去给模板写凑数断言。
      thresholds: { lines: 73, functions: 64, branches: 72, statements: 72 },
    },
  },
})
