import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/hu8',
  outputDir: './test-results/hu8',
  workers: 1,
  timeout: 30000,
  use: { baseURL: 'http://127.0.0.1:5188', browserName: 'chromium', channel: 'chromium',
    headless: true, screenshot: 'only-on-failure', trace: 'retain-on-failure' },
  reporter: 'list',
  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 5188 --strictPort',
    url: 'http://127.0.0.1:5188', reuseExistingServer: false, timeout: 30000,
  },
})
