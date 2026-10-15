import { defineConfig, devices } from '@playwright/test'

/**
 * 冒烟用例要跑的是**真实链路**：Vite 起前端，Spring Boot 起后端，浏览器点一个 2×2
 * 的实验并等它跑完。因此这里不用 mock、不走 `vite preview` 静态产物。
 *
 * 后端由外部脚本先起好（见 README「端到端冒烟」），Playwright 只负责起前端并等待
 * 后端健康检查通过——让 Playwright 去管一个 Maven 进程会把失败原因搅成一团。
 */
export default defineConfig({
  testDir: './e2e',
  // 真实实验受机器负载影响，单条用例给足时间；整体超时比它再宽一些。
  timeout: 180_000,
  expect: { timeout: 30_000 },
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env.E2E_BASE_URL ? undefined : {
    // 直接用本地 vite 二进制而不是 `pnpm dev`：Playwright 通过 /bin/sh 拉起子进程，
    // 那里不保证有 pnpm（本机就没有），包管理器是否在 PATH 上不该决定用例能否跑。
    command: 'node_modules/.bin/vite --host 127.0.0.1 --port 5173 --strictPort',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
