import { defineConfig } from '@playwright/test'
import process from 'node:process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// T11.3: HU-11 de punta a punta con el Django real (sin API simulada).
const web = path.dirname(fileURLToPath(import.meta.url))
const django = path.resolve(web, '../backend/django')
const python = process.env.HU11_TEST_PYTHON || process.env.HU7_TEST_PYTHON
if (!python) throw new Error('Define HU11_TEST_PYTHON con un intérprete que tenga requirements.txt instalado.')

export default defineConfig({
  testDir: './tests/hu11-local',
  outputDir: '../.hu11-local/playwright',
  workers: 1,
  fullyParallel: false,
  retries: 0,
  timeout: 60000,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:5195', browserName: 'chromium', channel: 'chromium', headless: true,
    screenshot: 'only-on-failure', trace: 'retain-on-failure',
  },
  webServer: [
    { command: `"${python}" tools/hu11_backend_local.py`, cwd: django,
      url: 'http://127.0.0.1:8021/api/prestadores/rubros/', reuseExistingServer: false, timeout: 90000,
      env: { PYTHONDONTWRITEBYTECODE: '1' } },
    { command: 'npx vite build --config vite.hu11-local.config.mjs && npx vite preview --config vite.hu11-local.config.mjs',
      cwd: web, url: 'http://127.0.0.1:5195', reuseExistingServer: false, timeout: 60000 },
  ],
})
