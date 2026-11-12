import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

// Ejecuta el worker real, sustituyendo solo Firebase y APIs del navegador.
// No simula una entrega real desde los servidores FCM.
function entorno({ usuario = { id: 7, rol: 'prestador' }, autenticado = true, mostrar, consultar } = {}) {
  const eventos = {}
  const avisos = []
  const abiertas = []
  let segundoPlano
  const self = {
    location: { origin: 'https://chamba.test' },
    registration: {
      getNotifications: consultar || (async () => []),
      showNotification: async (titulo, opciones) => {
        await mostrar?.()
        avisos.push({ titulo, opciones })
      },
    },
    clients: { matchAll: async () => [], openWindow: async (url) => abiertas.push(url) },
    addEventListener: (nombre, fn) => { eventos[nombre] = fn },
  }
  const codigo = readFileSync(new URL('../src/push-worker.js', import.meta.url), 'utf8').replace(/^import .*$/gm, '')
  vm.runInNewContext(codigo, {
    self, URL, firebaseConfig: {}, initializeApp: () => ({}), getMessaging: () => ({}),
    onBackgroundMessage: (_, fn) => { segundoPlano = fn },
    fetch: async () => ({ ok: autenticado, json: async () => usuario }),
  })
  return { eventos, avisos, abiertas, recibir: (data) => segundoPlano({ data }) }
}
const datos = { solicitud_id: '123', usuario_id: '7' }

test('primer y segundo plano convergen en un solo aviso genérico', async () => {
  const e = entorno()
  await e.recibir(datos)
  let terminado
  e.eventos.message({ data: { tipo: 'SOLICITUD_FCM', datos }, waitUntil: (p) => { terminado = p } })
  await terminado
  assert.equal(e.avisos.length, 1)
  assert.equal(e.avisos[0].titulo, 'Nueva solicitud de servicio')
  assert.equal(e.avisos[0].opciones.body, 'Abre Chamba PE para revisar y responder.')
  assert.deepEqual(Object.keys(e.avisos[0].opciones.data), ['solicitud_id'])
})
test('sesión vencida, otro usuario o rol no muestran el aviso', async () => {
  for (const config of [{ autenticado: false }, { usuario: { id: 8, rol: 'prestador' } }, { usuario: { id: 7, rol: 'cliente' } }]) {
    const e = entorno(config)
    await e.recibir(datos)
    assert.equal(e.avisos.length, 0)
  }
})
test('clic abre el detalle del mismo origen y rechaza IDs manipulados', async () => {
  const e = entorno()
  let terminado
  e.eventos.notificationclick({ notification: { close() {}, data: { solicitud_id: '123' } }, waitUntil: (p) => { terminado = p } })
  await terminado
  assert.deepEqual(e.abiertas, ['https://chamba.test/solicitudes/123'])
  await e.recibir({ ...datos, solicitud_id: 'https://otro.test' })
  assert.equal(e.avisos.length, 0)
})

for (const fallo of ['mostrar', 'consultar']) {
  test('reintenta tras fallo al ' + fallo + ' sin perder el aviso', async () => {
    let llamadas = 0
    const fallarUnaVez = async () => {
      if (++llamadas === 1) throw new Error('fallo temporal')
      return []
    }
    const e = entorno({ [fallo]: fallarUnaVez })
    await e.recibir(datos)
    assert.equal(e.avisos.length, 0)
    await e.recibir(datos)
    assert.equal(e.avisos.length, 1)
    await e.recibir(datos)
    assert.equal(e.avisos.length, 1)
  })
}

test('entregas concurrentes no duplican un aviso en proceso', async () => {
  let liberar
  let iniciada
  const inicio = new Promise((r) => { iniciada = r })
  const espera = new Promise((r) => { liberar = r })
  const e = entorno({ mostrar: async () => { iniciada(); await espera } })
  const primera = e.recibir(datos)
  await inicio
  await e.recibir(datos)
  liberar()
  await primera
  assert.equal(e.avisos.length, 1)
})

test('un aviso ya visible no se vuelve a mostrar', async () => {
  const e = entorno({ consultar: async () => [{}] })
  await e.recibir(datos)
  assert.equal(e.avisos.length, 0)
})
