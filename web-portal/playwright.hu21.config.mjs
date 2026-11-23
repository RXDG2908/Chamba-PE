import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/hu21',
  // Configuración serial validada; se conservan los diagnósticos de los fallos paralelos.
  workers: 1,
  fullyParallel: true,
  retries: 0,
  timeout: 30000,
  reporter: 'list',
  outputDir: '../.hu21-local/playwright-hu21',
  use: {
    baseURL: 'http://127.0.0.1:5191',
    browserName: 'chromium',
    channel: 'chromium',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 5191 --strictPort',
    url: 'http://127.0.0.1:5191',
    reuseExistingServer: false,
    timeout: 60000,
  },
})
