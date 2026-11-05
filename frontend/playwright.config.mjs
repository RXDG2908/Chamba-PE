import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/hu7',
  fullyParallel: true,
  workers: 2,
  timeout: 30000,
  use: { baseURL: 'http://127.0.0.1:5187', browserName: 'chromium', channel: 'chromium', headless: true, screenshot: 'only-on-failure' },
  reporter: 'list',
  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 5187 --strictPort',
    url: 'http://127.0.0.1:5187',
    reuseExistingServer: false,
    timeout: 30000,
  },
})
