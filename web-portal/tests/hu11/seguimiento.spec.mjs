import { test, expect } from '@playwright/test'

// API simulada: estas pruebas cubren las pantallas; las reglas del servidor están en servicios/test_seguimiento.py.
const PRESTADOR = { id: 5, username: 'ana', nombres: 'Ana', rol: 'prestador' }
const CLIENTE = { id: 7, username: 'carlos', nombres: 'Carlos', rol: 'cliente' }
const NOMBRES = { pendiente: 'Pendiente', aceptada: 'Confirmado', en_camino: 'En camino',
  en_servicio: 'En servicio', finalizada: 'Finalizada', rechazada: 'Rechazada' }
const SIGUIENTE = { aceptada: 'en_camino', en_camino: 'en_servicio', en_servicio: 'finalizada' }
const ORDEN = ['aceptada', 'en_camino', 'en_servicio', 'finalizada']

function historial(estado) {
  const hasta = ORDEN.indexOf(estado)
  return ORDEN.slice(0, hasta + 1).map((e, i) => ({
    estado: e, estado_display: NOMBRES[e], fecha: `2026-11-23T1${5 + i}:00:00Z`,
  }))
}

function recibida(estado) {
  return {
    id: 41, rubro: 'Gasfitería', descripcion: 'Fuga ficticia', cliente_nombre: 'Cliente ficticio',
    cliente_telefono: '900000000', latitud: '0', longitud: '0', estado, estado_display: NOMBRES[estado],
    siguiente_estado: SIGUIENTE[estado] ?? null, historial: historial(estado),
    creada: '2026-11-23T14:00:00Z', respondida: '2026-11-23T15:00:00Z',
  }
}

function servicio(estado) {
  return {
    id: 41, prestador: { id: 3, nombre: 'Ana Pérez' }, rubro: 'Gasfitería', descripcion: 'Fuga ficticia',
    estado, estado_display: NOMBRES[estado], creada: '2026-11-23T14:00:00Z',
    calificacion: null, puede_calificar: estado === 'finalizada',
  }
}

async function preparar(page, { usuario = PRESTADOR, estado = 'aceptada', avanzar } = {}) {
  const datos = { estado, avances: [], consultasCliente: 0 }
  await page.context().addCookies([{ name: 'csrftoken', value: 'token-ficticio', url: 'http://127.0.0.1:5193' }])
  await page.context().route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/usuarios/csrf/') return json({})
    if (path === '/api/usuarios/yo/') return json(usuario)
    if (path === '/api/servicios/solicitudes/recibidas/') return json([recibida(datos.estado)])
    if (path === '/api/servicios/solicitudes/recibidas/41/') return json(recibida(datos.estado))
    if (path === '/api/servicios/solicitudes/41/avanzar/' && request.method() === 'POST') {
      const cuerpo = request.postDataJSON()
      datos.avances.push({ cuerpo, csrf: request.headers()['x-csrftoken'] })
      if (avanzar) return avanzar(route, json, datos)
      if (SIGUIENTE[datos.estado] !== cuerpo.estado) return json({ estado: 'No', estado_actual: datos.estado }, 409)
      datos.estado = cuerpo.estado
      return json(recibida(datos.estado))
    }
    if (path === '/api/calificaciones/mis-servicios/') {
      datos.consultasCliente++
      return json([servicio(datos.estado)])
    }
    return json({}, 404)
  })
  return datos
}

test('el prestador avanza el servicio en orden hasta finalizarlo', async ({ page }) => {
  const datos = await preparar(page)
  await page.goto('/solicitudes/41')
  await expect(page.getByText('Estado: Confirmado', { exact: true })).toBeVisible()
  for (const [boton, estado] of [['Voy en camino', 'En camino'], ['Empecé el servicio', 'En servicio'],
    ['Finalizar servicio', 'Finalizada']]) {
    await page.getByRole('button', { name: boton, exact: true }).click()
    await expect(page.getByText(`Estado: ${estado}`, { exact: true })).toBeVisible()
    await expect(page.getByText(`Estado actualizado: ${estado}.`)).toBeVisible()
  }
  await expect(page.getByText('Servicio finalizado.', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /Voy en camino|Empecé el servicio|Finalizar servicio/ })).toHaveCount(0)
  expect(datos.avances).toEqual([
    { cuerpo: { estado: 'en_camino' }, csrf: 'token-ficticio' },
    { cuerpo: { estado: 'en_servicio' }, csrf: 'token-ficticio' },
    { cuerpo: { estado: 'finalizada' }, csrf: 'token-ficticio' },
  ])
})

test('la línea de etapas marca la actual y las horas de las anteriores', async ({ page }) => {
  await preparar(page, { estado: 'en_camino' })
  await page.goto('/solicitudes/41')
  const etapas = page.getByRole('list', { name: 'Seguimiento del servicio' }).getByRole('listitem')
  await expect(etapas).toHaveText([/Confirmado/, /En camino/, /En servicio/, /Finalizada/])
  await expect(etapas.nth(1)).toHaveAttribute('aria-current', 'step')
})

test('con 409 recarga el estado real sin repetir el paso', async ({ page }) => {
  const datos = await preparar(page, {
    avanzar: (route, json, d) => { d.estado = 'en_servicio'; return json({ estado: 'Ya cambió' }, 409) },
  })
  await page.goto('/solicitudes/41')
  await page.getByRole('button', { name: 'Voy en camino', exact: true }).click()
  await expect(page.getByText('Estado: En servicio', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Finalizar servicio', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Voy en camino', exact: true })).toHaveCount(0)
  expect(datos.avances).toHaveLength(1)
})

test('rechazada no muestra botones de seguimiento', async ({ page }) => {
  await preparar(page, { estado: 'rechazada' })
  await page.goto('/solicitudes/41')
  await expect(page.getByText('Estado: Rechazada', { exact: true })).toBeVisible()
  await expect(page.getByRole('list', { name: 'Seguimiento del servicio' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: /Voy en camino|Empecé el servicio|Finalizar servicio/ })).toHaveCount(0)
})

test('el cliente ve el avance solo, sin recargar, y deja de consultar al finalizar', async ({ page }) => {
  await page.clock.install()
  const datos = await preparar(page, { usuario: CLIENTE, estado: 'aceptada' })
  await page.goto('/mis-servicios')
  const item = page.getByRole('listitem').filter({ hasText: 'Fuga ficticia' })
  await expect(item.locator('.etiqueta-estado')).toHaveText('Confirmado')
  datos.estado = 'en_camino'
  await page.clock.fastForward(5000)
  await expect(item.locator('.etiqueta-estado')).toHaveText('En camino')
  datos.estado = 'finalizada'
  await page.clock.fastForward(5000)
  await expect(item.locator('.etiqueta-estado')).toHaveText('Finalizada')
  await expect(item.getByRole('button', { name: 'Enviar calificación' })).toBeVisible()
  const consultas = datos.consultasCliente
  await page.clock.fastForward(15000)
  expect(datos.consultasCliente).toBe(consultas)
})
