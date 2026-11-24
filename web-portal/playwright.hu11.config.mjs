import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/hu11',
  workers: 1,
  retries: 0,
  timeout: 30000,
  reporter: 'list',
  outputDir: '../.hu11-local/playwright-hu11',
  use: {
    baseURL: 'http://127.0.0.1:5193',
    browserName: 'chromium',
    channel: 'chromium',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 5193 --strictPort',
    url: 'http://127.0.0.1:5193',
    reuseExistingServer: false,
    timeout: 60000,
  },
})
