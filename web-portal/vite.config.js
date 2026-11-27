import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import process from 'node:process'

// Arquitectura: la parte de usuario (búsqueda, recomendación con IA, solicitudes, seguimiento,
// disponibilidad y calificaciones) va al Back End de usuario en Spring Boot; el resto (cuentas e inicio
// de sesión, verificación de identidad, certificados y administración) sigue en Django.
// Spring lee la misma sesión de Django y exige el mismo CSRF. Para mandar todo a Django:
// CHAMBA_API_USUARIO=http://127.0.0.1:8000 npm run dev
const DJANGO = 'http://127.0.0.1:8000'
const SPRING = process.env.CHAMBA_API_USUARIO || 'http://127.0.0.1:8080'
const haciaSpring = { target: SPRING, changeOrigin: true, secure: false }

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      input: { app: 'index.html', 'firebase-messaging-sw': 'firebase-messaging-sw.js' },
      output: { entryFileNames: (chunk) => chunk.name === 'firebase-messaging-sw' ? 'firebase-messaging-sw.js' : 'assets/[name]-[hash].js' },
    },
  },
  server: {
    proxy: {
      // Van primero: Vite usa la primera regla que coincide.
      '^/api/(prestadores/(rubros|buscar)|ia/recomendacion|servicios/solicitudes|usuarios/disponibilidad|calificaciones)/': haciaSpring,
      '/api': {
        target: DJANGO,
        changeOrigin: true,
        secure: false,
      },
      // Los archivos subidos (p. ej. certificados) los sirve Django en /media/.
      '/media': {
        target: DJANGO,
        changeOrigin: true,
        secure: false,
      },
      // T9.2: el inicio de sesión del admin (y sus estilos) pasa por el mismo origen
      // que React, así la cookie de sesión sirve para /api/administracion/.
      // Con la barra final para no capturar la ruta /administracion de React.
      '/admin/': {
        target: DJANGO,
        changeOrigin: true,
        secure: false,
      },
      '/static/': {
        target: DJANGO,
        changeOrigin: true,
        secure: false,
      },
    },
  },
})