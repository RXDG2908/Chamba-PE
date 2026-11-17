import { defineConfig } from 'vite'
import base from './vite.config.js'

const proxy = Object.fromEntries(Object.entries(base.server.proxy).map(([ruta, opciones]) =>
  [ruta, { ...opciones, target: 'http://127.0.0.1:8019' }]))

export default defineConfig({
  ...base,
  server: { ...base.server, host: '127.0.0.1', port: 5189, strictPort: true, proxy },
  preview: { host: '127.0.0.1', port: 5189, strictPort: true, proxy },
})
