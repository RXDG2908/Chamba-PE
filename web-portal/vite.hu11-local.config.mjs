import { defineConfig } from 'vite'
import base from './vite.config.js'

// T11.3: la web apunta al Django real de tools/hu11_backend_local.py (puerto 8021).
const proxy = Object.fromEntries(Object.entries(base.server.proxy).map(([ruta, opciones]) =>
  [ruta, { ...opciones, target: 'http://127.0.0.1:8021' }]))

export default defineConfig({
  ...base,
  // Se construye fuera de dist/ (en .hu11-local/, ignorado por git) para no pisar el build normal.
  build: { ...base.build, outDir: '../.hu11-local/dist', emptyOutDir: true },
  server: { ...base.server, host: '127.0.0.1', port: 5195, strictPort: true, proxy },
  preview: { host: '127.0.0.1', port: 5195, strictPort: true, proxy },
})
