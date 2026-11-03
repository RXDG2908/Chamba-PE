import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      // Los archivos subidos (p. ej. certificados) los sirve Django en /media/.
      '/media': {
        target: 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      // T9.2: el inicio de sesión del admin (y sus estilos) pasa por el mismo origen
      // que React, así la cookie de sesión sirve para /api/administracion/.
      // Con la barra final para no capturar la ruta /administracion de React.
      '/admin/': {
        target: 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
      '/static/': {
        target: 'http://127.0.0.1:8000',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})