// T11.3: pruebas de transición de estados de HU-11 de punta a punta, con el Django real
// (tools/hu11_backend_local.py, base temporal y cuentas ficticias). Sin API simulada.
import { test, expect, request as playwrightRequest } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

const BASE = 'http://127.0.0.1:5195'
let datos
test.beforeAll(() => {
  datos = JSON.parse(fs.readFileSync(path.resolve('../.hu11-local/fixtures.json'), 'utf8'))
})

async function ingresar(page, usuario) {
  await page.goto('/ingresar')
  await page.getByLabel('Usuario o correo').fill(usuario)
  await page.getByLabel('Contraseña').fill(datos.clave)
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click()
  await expect(page).not.toHaveURL(/\/ingresar/)
}

// Sesión de API con cookie y CSRF, como la usa la web.
async function sesion(usuario) {
  const api = await playwrightRequest.newContext({ baseURL: BASE })
  await api.get('/api/usuarios/csrf/')
  const token = async () => (await api.storageState()).cookies.find((c) => c.name === 'csrftoken').value
  const r = await api.post('/api/usuarios/login/', {
    data: { username: usuario, password: datos.clave }, headers: { 'X-CSRFToken': await token() },
  })
  expect(r.status(), await r.text()).toBe(200)
  return {
    api,
    post: async (ruta, cuerpo = {}, conCsrf = true) => api.post(ruta, {
      data: cuerpo, headers: conCsrf ? { 'X-CSRFToken': await token() } : {},
    }),
    get: (ruta) => api.get(ruta),
  }
}

async function nuevaSolicitud(cliente, descripcion) {
  const r = await cliente.post('/api/servicios/solicitudes/', {
    prestador: datos.prestadora.id, rubro: datos.rubro, cliente_nombre: 'Cliente Ficticio',
    cliente_telefono: '900000000', descripcion,
    latitud: datos.centro.latitud.toFixed(6), longitud: datos.centro.longitud.toFixed(6),
  })
  expect(r.status(), await r.text()).toBe(201)
  return (await r.json()).id
}

const avanzar = (sesionApi, id, estado, conCsrf = true) =>
  sesionApi.post(`/api/servicios/solicitudes/${id}/avanzar/`, { estado }, conCsrf)

test('flujo completo en el navegador: solicitar, avanzar hasta Finalizada y calificar', async ({ browser }) => {
  const contextoCliente = await browser.newContext()
  const contextoPrestadora = await browser.newContext()
  const cliente = await contextoCliente.newPage()
  const prestadora = await contextoPrestadora.newPage()

  // El cliente busca y envía la solicitud desde la tarjeta de Ana.
  await ingresar(cliente, datos.cliente)
  await cliente.goto('/resultados')
  await expect(cliente.getByRole('combobox', { name: /Rubro/ })).toBeEnabled()
  await cliente.getByRole('combobox', { name: /Rubro/ }).selectOption(String(datos.rubro))
  await cliente.getByLabel('Latitud', { exact: true }).fill(String(datos.centro.latitud))
  await cliente.getByLabel('Longitud', { exact: true }).fill(String(datos.centro.longitud))
  await cliente.getByRole('button', { name: 'Usar coordenadas', exact: true }).click()
  await cliente.getByRole('button', { name: 'Buscar', exact: true }).click()
  const tarjeta = cliente.locator('.lista-prestadores > li').filter({ hasText: 'Ana Ficticia' })
  await expect(tarjeta).toHaveCount(1)
  await tarjeta.getByLabel('Tu nombre').fill('Cliente Ficticio')
  await tarjeta.getByLabel('Teléfono: 9 dígitos sin +51').fill('900000000')
  await tarjeta.getByLabel('Describe el trabajo').fill('Fuga en el lavadero (T11.3).')
  await tarjeta.getByRole('button', { name: 'Enviar solicitud' }).click()
  const estadoCliente = tarjeta.locator('.estado-solicitud')
  await expect(estadoCliente).toHaveText('Pendiente de respuesta')
  const id = Number((await tarjeta.getByRole('heading', { name: /Solicitud #/ }).textContent()).match(/\d+/)[0])

  // Ana la ve en su bandeja, la acepta y la avanza paso a paso.
  await ingresar(prestadora, datos.prestadora.usuario)
  await prestadora.goto('/solicitudes')
  const caja = prestadora.locator('article').filter({ hasText: `Solicitud #${id}` })
  await caja.getByRole('button', { name: 'Aceptar' }).click()
  await expect(caja).toContainText('Estado: Confirmado')
  for (const [boton, estado] of [['Voy en camino', 'En camino'], ['Empecé el servicio', 'En servicio'],
    ['Finalizar servicio', 'Finalizada']]) {
    await caja.getByRole('button', { name: boton }).click()
    await expect(caja).toContainText(`Estado: ${estado}`)
  }
  await expect(caja).toContainText('Servicio finalizado.')
  await expect(caja.getByRole('button')).toHaveCount(0)
  // La línea de etapas del prestador marca las 4 etapas con su hora.
  await expect(caja.locator('.seg-hora')).toHaveCount(4)

  // El cliente, sin recargar, ve que terminó (consulta cada 5 s) con las horas de cada etapa.
  await expect(estadoCliente).toContainText('El servicio terminó', { timeout: 15000 })
  await expect(tarjeta.locator('.seg-hora')).toHaveCount(4)
  await expect(tarjeta).not.toContainText('Consultaremos el estado automáticamente')

  // En Mis servicios ve el mismo avance y califica.
  await cliente.goto('/mis-servicios')
  const servicio = cliente.locator('li.servicio').filter({ hasText: 'Fuga en el lavadero (T11.3).' })
  await expect(servicio.locator('.etiqueta-estado')).toHaveText('Finalizada')
  await expect(servicio.locator('.seg-hora')).toHaveCount(4)
  await servicio.getByRole('radio', { name: '5 estrellas', exact: true }).check()
  await servicio.getByLabel('Comentario (opcional)').fill('Llegó a tiempo.')
  await servicio.getByRole('button', { name: 'Enviar calificación' }).click()
  await expect(servicio).toContainText('Tu calificación:')

  // La búsqueda ya muestra el promedio de Ana.
  await cliente.goto('/resultados')
  await cliente.getByRole('combobox', { name: /Rubro/ }).selectOption(String(datos.rubro))
  await cliente.getByLabel('Latitud', { exact: true }).fill(String(datos.centro.latitud))
  await cliente.getByLabel('Longitud', { exact: true }).fill(String(datos.centro.longitud))
  await cliente.getByRole('button', { name: 'Usar coordenadas', exact: true }).click()
  await cliente.getByRole('button', { name: 'Buscar', exact: true }).click()
  await expect(cliente.locator('.lista-prestadores > li').filter({ hasText: 'Ana Ficticia' }))
    .toContainText('★ 5.0 · 1 calificación')

  await contextoCliente.close()
  await contextoPrestadora.close()
})

test('reglas de transición contra el servidor real', async () => {
  const cliente = await sesion(datos.cliente)
  const ana = await sesion(datos.prestadora.usuario)
  const beto = await sesion(datos.otro_prestador.usuario)
  const id = await nuevaSolicitud(cliente, 'Reglas de transición (T11.3).')

  // Pendiente: primero hay que aceptarla.
  expect((await avanzar(ana, id, 'en_camino')).status()).toBe(409)
  expect((await ana.post(`/api/servicios/solicitudes/${id}/aceptar/`)).status()).toBe(200)

  // Saltar una etapa, estado inexistente, otro prestador, el cliente y sin CSRF: nada cambia.
  const salto = await avanzar(ana, id, 'en_servicio')
  expect(salto.status()).toBe(409)
  expect(await salto.json()).toMatchObject({ estado_actual: 'aceptada' })
  expect((await avanzar(ana, id, 'volando')).status()).toBe(400)
  expect((await avanzar(beto, id, 'en_camino')).status()).toBe(404)
  expect((await avanzar(cliente, id, 'en_camino')).status()).toBe(403)
  expect((await avanzar(ana, id, 'en_camino', false)).status()).toBe(403)

  // En orden sí avanza; repetir o retroceder da 409.
  expect((await avanzar(ana, id, 'en_camino')).status()).toBe(200)
  expect((await avanzar(ana, id, 'en_camino')).status()).toBe(409)
  expect((await avanzar(ana, id, 'aceptada')).status()).toBe(409)
  expect((await avanzar(ana, id, 'en_servicio')).status()).toBe(200)
  expect((await avanzar(ana, id, 'finalizada')).status()).toBe(200)
  // Finalizada es final.
  expect((await avanzar(ana, id, 'finalizada')).status()).toBe(409)

  // El cliente ve el historial completo, en orden y con horas crecientes.
  const detalle = await (await cliente.get(`/api/servicios/solicitudes/${id}/`)).json()
  expect(detalle.estado).toBe('finalizada')
  expect(detalle.historial.map((h) => h.estado)).toEqual(['aceptada', 'en_camino', 'en_servicio', 'finalizada'])
  const horas = detalle.historial.map((h) => Date.parse(h.fecha))
  expect([...horas].sort((a, b) => a - b)).toEqual(horas)

  // Rechazada también es final.
  const rechazada = await nuevaSolicitud(cliente, 'Se rechaza (T11.3).')
  expect((await ana.post(`/api/servicios/solicitudes/${rechazada}/rechazar/`)).status()).toBe(200)
  expect((await avanzar(ana, rechazada, 'en_camino')).status()).toBe(409)

  for (const s of [cliente, ana, beto]) await s.api.dispose()
})

test('dos avances a la vez: uno gana, el otro recibe 409 y el historial no se duplica', async () => {
  const cliente = await sesion(datos.cliente)
  const ana = await sesion(datos.prestadora.usuario)
  const id = await nuevaSolicitud(cliente, 'Doble clic (T11.3).')
  expect((await ana.post(`/api/servicios/solicitudes/${id}/aceptar/`)).status()).toBe(200)

  const respuestas = await Promise.all([1, 2, 3].map(() => avanzar(ana, id, 'en_camino')))
  expect(respuestas.map((r) => r.status()).sort()).toEqual([200, 409, 409])
  const detalle = await (await cliente.get(`/api/servicios/solicitudes/${id}/`)).json()
  expect(detalle.historial.map((h) => h.estado)).toEqual(['aceptada', 'en_camino'])

  for (const s of [cliente, ana]) await s.api.dispose()
})

test('solo el cliente que la pidió puede calificarla, y solo al final', async () => {
  const cliente = await sesion(datos.cliente)
  const otroCliente = await sesion(datos.otro_cliente)
  const ana = await sesion(datos.prestadora.usuario)
  const id = await nuevaSolicitud(cliente, 'Calificación al final (T11.3).')
  const calificar = (s) => s.post('/api/calificaciones/', { solicitud: id, estrellas: 4 })

  expect((await ana.post(`/api/servicios/solicitudes/${id}/aceptar/`)).status()).toBe(200)
  for (const estado of ['en_camino', 'en_servicio']) {
    expect((await avanzar(ana, id, estado)).status()).toBe(200)
    expect((await calificar(cliente)).status()).toBe(409)  // todavía no termina
  }
  expect((await avanzar(ana, id, 'finalizada')).status()).toBe(200)
  expect((await calificar(otroCliente)).status()).toBe(404)
  expect((await calificar(cliente)).status()).toBe(201)
  expect((await calificar(cliente)).status()).toBe(409)  // una sola vez

  for (const s of [cliente, otroCliente, ana]) await s.api.dispose()
})
