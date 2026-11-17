import { initializeApp, getApps } from 'firebase/app'
import { deleteToken, getMessaging, getToken, isSupported, onMessage } from 'firebase/messaging'
import { enviarJson } from './api.js'
import { firebaseConfig } from './firebase-config.js'

let escucha
let registro
const clave = 'chamba-fcm-token'
export async function compatible() {
  return window.isSecureContext && 'Notification' in window && 'serviceWorker' in navigator && await isSupported()
}
function mensajeria() {
  return getMessaging(getApps()[0] || initializeApp(firebaseConfig))
}
async function preparar() {
  registro = await navigator.serviceWorker.register(
    '/firebase-messaging-sw.js', { type: 'module', scope: '/' },
  )
  if (!registro.active) await new Promise((resolve, reject) => {
    const worker = registro.installing || registro.waiting
    if (!worker) return reject(new Error('No se pudo activar el service worker.'))
    worker.addEventListener('statechange', () => {
      if (worker.state === 'activated') resolve()
      if (worker.state === 'redundant') reject(new Error('No se pudo activar el service worker.'))
    })
  })
  return registro
}
export async function activarNotificaciones(usuario) {
  // Se invoca únicamente desde el botón; no desde efectos ni al cargar.
  if (!await compatible()) throw new Error('Este navegador no admite notificaciones push. Usa HTTPS o localhost.')
  if (Object.values(firebaseConfig).some((v) => !v) || !import.meta.env.VITE_FIREBASE_VAPID_KEY) {
    throw new Error('Falta configurar Firebase en este entorno.')
  }
  if (await Notification.requestPermission() !== 'granted') {
    throw new Error('Permiso denegado. Puedes habilitarlo en la configuración del sitio del navegador.')
  }
  const sw = await preparar()
  const token = await getToken(mensajeria(), { vapidKey: import.meta.env.VITE_FIREBASE_VAPID_KEY, serviceWorkerRegistration: sw })
  const respuesta = await enviarJson('/api/servicios/notificaciones/token/', 'POST', { token })
  if (respuesta.status === 403) throw new Error('La sesión venció. Vuelve a iniciar sesión para activar los avisos.')
  if (!respuesta.ok) throw new Error('No se pudo registrar el navegador. Inténtalo de nuevo.')
  localStorage.setItem(clave, JSON.stringify({ token, usuario: usuario.id }))
  escuchar(usuario)
}
export function escuchar(usuario) {
  escucha?.()
  escucha = undefined
  if (usuario?.rol !== 'prestador' || !('Notification' in window) || Notification.permission !== 'granted' || Object.values(firebaseConfig).some((v) => !v)) return
  let activo = true
  let cancelar
  compatible().then((soportado) => {
    if (!soportado || !activo) return
    cancelar = onMessage(mensajeria(), (payload) => {
      if (payload.data?.usuario_id !== String(usuario.id)) return
      preparar().then((sw) => {
        if (activo) sw.active?.postMessage({ tipo: 'SOLICITUD_FCM', datos: payload.data })
      }).catch(() => {})
    })
  }).catch(() => {})
  escucha = () => { activo = false; cancelar?.() }
  return escucha
}
export async function desactivarNotificaciones() {
  escucha?.()
  escucha = undefined
  const guardado = JSON.parse(localStorage.getItem(clave) || 'null')
  if (guardado) {
    const respuesta = await enviarJson('/api/servicios/notificaciones/token/', 'DELETE', { token: guardado.token })
    if (!respuesta.ok && respuesta.status !== 403) throw new Error('No se pudo desvincular el navegador.')
    if (await compatible()) await deleteToken(mensajeria())
    localStorage.removeItem(clave)
  }
  const sw = await navigator.serviceWorker?.getRegistration()
  for (const aviso of await sw?.getNotifications() || []) aviso.close()
}
