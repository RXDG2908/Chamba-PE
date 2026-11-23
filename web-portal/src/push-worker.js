import { initializeApp } from 'firebase/app'
import { getMessaging, onBackgroundMessage } from 'firebase/messaging/sw'
import { firebaseConfig } from './firebase-config.js'

const vistos = new Set()
const mostrando = new Set()
async function mostrar(datos) {
  if (!/^\d+$/.test(datos?.solicitud_id)) return
  // Verificar la sesión actual también con la app cerrada o tras cambiar de cuenta.
  const respuesta = await fetch('/api/usuarios/yo/', { cache: 'no-store' })
  if (!respuesta.ok) return
  const usuario = await respuesta.json()
  if (usuario.rol !== 'prestador' || String(usuario.id) !== datos.usuario_id) return
  const tag = `solicitud-${datos.solicitud_id}`
  if (vistos.has(tag) || mostrando.has(tag)) return
  mostrando.add(tag)
  try {
    if (!(await self.registration.getNotifications({ tag })).length) {
      await self.registration.showNotification('Nueva solicitud de servicio', {
        body: 'Abre Chamba PE para revisar y responder.', tag,
        data: { solicitud_id: datos.solicitud_id },
      })
    }
    // Solo confirmar después de observar o mostrar un aviso con éxito.
    vistos.add(tag)
  } finally {
    // Un error de consulta/visualización permite un nuevo intento posterior.
    mostrando.delete(tag)
  }
}

// Registrar antes de Firebase para conservar el comportamiento del clic.
self.addEventListener('notificationclick', (evento) => {
  evento.notification.close()
  const id = evento.notification.data?.solicitud_id
  if (!/^\d+$/.test(id)) return
  evento.waitUntil((async () => {
    const url = new URL(`/solicitudes/${id}`, self.location.origin).href
    const ventanas = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    const ventana = ventanas.find((v) => new URL(v.url).origin === self.location.origin)
    if (ventana) { await ventana.navigate(url); await ventana.focus() }
    else await self.clients.openWindow(url)
  })())
})
self.addEventListener('message', (evento) => {
  if (evento.data?.tipo === 'SOLICITUD_FCM') evento.waitUntil(mostrar(evento.data.datos).catch(() => {}))
})
onBackgroundMessage(getMessaging(initializeApp(firebaseConfig)), (payload) => mostrar(payload.data).catch(() => {}))
