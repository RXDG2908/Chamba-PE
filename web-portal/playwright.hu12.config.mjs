import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/hu12',
  workers: 1,
  retries: 0,
  timeout: 30000,
  reporter: 'list',
  outputDir: '../.hu12-local/playwright-hu12',
  use: {
    baseURL: 'http://127.0.0.1:5192',
    browserName: 'chromium',
    channel: 'chromium',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 5192 --strictPort',
    url: 'http://127.0.0.1:5192',
    reuseExistingServer: false,
    timeout: 60000,
  },
})
