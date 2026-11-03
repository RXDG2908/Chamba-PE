import { defineConfig } from '@playwright/test'
import process from 'node:process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontend = path.dirname(fileURLToPath(import.meta.url))
const root = path.dirname(frontend)
const python = process.env.HU7_TEST_PYTHON
if (!python) throw new Error('Define HU7_TEST_PYTHON con un intérprete que tenga requirements.txt instalado.')

export default defineConfig({
  testDir: './tests/hu7-local',
  workers: 1,
  fullyParallel: false,
  timeout: 45000,
  use: { baseURL: 'http://127.0.0.1:5189', browserName: 'chromium', channel: 'chromium', headless: true,
    screenshot: 'only-on-failure', trace: 'retain-on-failure' },
  reporter: [['list'], ['json', { outputFile: '../docs/evidencias/hu7-local/resultados.json' }]],
  webServer: [
    { command: `"${python}" tools/hu7_backend_local.py`, cwd: root,
      url: 'http://127.0.0.1:8019/api/prestadores/rubros/', reuseExistingServer: false, timeout: 90000,
      env: { PYTHONDONTWRITEBYTECODE: '1' } },
    { command: 'npm run build -- --config vite.hu7-local.config.mjs && npm run preview -- --config vite.hu7-local.config.mjs',
      cwd: frontend, url: 'http://127.0.0.1:5189', reuseExistingServer: false, timeout: 60000 },
  ],
})
