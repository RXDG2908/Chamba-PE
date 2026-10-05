import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Redirige /api al backend de Django para evitar problemas de CORS en desarrollo.
  server: { proxy: { '/api': 'http://127.0.0.1:8000' } },
})
