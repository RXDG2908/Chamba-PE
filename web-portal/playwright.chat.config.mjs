import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/chat',
  workers: 1,
  retries: 0,
  timeout: 30000,
  reporter: 'list',
  outputDir: '../.hu11-local/playwright-chat',
  use: {
    baseURL: 'http://127.0.0.1:5195',
    browserName: 'chromium',
    channel: 'chromium',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  // Sin clave de Google Maps: las pruebas usan el mapa de respaldo (corren sin red).
  webServer: {
    env: { ...process.env, VITE_GOOGLE_MAPS_API_KEY: '' },
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 5195 --strictPort',
    url: 'http://127.0.0.1:5195',
    reuseExistingServer: false,
    timeout: 60000,
  },
})
