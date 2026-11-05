import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/hu7',
  outputDir: './test-results/hu7',
  fullyParallel: true,
  // Evitar contención de navegadores en el entorno local/CI; mismos casos y tiempos.
  workers: 1,
  timeout: 30000,
  use: { baseURL: 'http://127.0.0.1:5187', browserName: 'chromium', channel: 'chromium', headless: true,
    screenshot: 'only-on-failure', trace: 'retain-on-failure' },
  reporter: 'list',
  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 5187 --strictPort',
    url: 'http://127.0.0.1:5187',
    reuseExistingServer: false,
    timeout: 30000,
  },
})
