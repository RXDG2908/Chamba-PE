import { test, expect } from '@playwright/test'

// API simulada: estas pruebas cubren la pantalla; las reglas del servidor están en calificaciones/tests.py.
const CLIENTE = { id: 7, username: 'carlos', nombres: 'Carlos', rol: 'cliente' }

function servicio(id, estado, cambios = {}) {
  const nombres = { pendiente: 'Pendiente', aceptada: 'Aceptada', rechazada: 'Rechazada', finalizada: 'Finalizada' }
  return {
    id, prestador: { id: 3, nombre: 'Ana Pérez' }, rubro: 'Gasfitería', descripcion: `Trabajo ${id}`,
    estado, estado_display: nombres[estado], creada: '2026-11-20T15:00:00Z',
    calificacion: null, puede_calificar: estado === 'finalizada', ...cambios,
  }
}

async function preparar(page, cambios = {}) {
  const estado = {
    usuario: CLIENTE,
    servicios: [servicio(1, 'finalizada'), servicio(2, 'aceptada')],
    respuesta: null, escrituras: [], falloLista: false, ...cambios,
  }
  await page.context().addCookies([{ name: 'csrftoken', value: 'token-ficticio', url: 'http://127.0.0.1:5192' }])
  await page.context().route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/usuarios/csrf/') return json({})
    if (path === '/api/usuarios/yo/') return estado.usuario ? json(estado.usuario) : json({ detail: 'Sin sesión' }, 403)
    if (path === '/api/calificaciones/mis-servicios/') {
      return estado.falloLista ? json({}, 500) : json(estado.servicios)
    }
    if (path === '/api/calificaciones/' && request.method() === 'POST') {
      const datos = request.postDataJSON()
      estado.escrituras.push({ datos, csrf: request.headers()['x-csrftoken'] })
      if (estado.respuesta) return json(...estado.respuesta)
      return json({
        solicitud: datos.solicitud,
        calificacion: { estrellas: datos.estrellas, comentario: datos.comentario, creada: '2026-11-23T15:00:00Z' },
        prestador: { id: 3, calificacion_promedio: datos.estrellas, calificaciones_total: 1 },
      }, 201)
    }
    return json({}, 404)
  })
  return estado
}

test('el cliente califica un servicio finalizado con estrellas y comentario', async ({ page }) => {
  const estado = await preparar(page)
  await page.goto('/mis-servicios')
  await expect(page.getByRole('heading', { name: 'Mis servicios' })).toBeVisible()
  const finalizado = page.getByRole('listitem').filter({ hasText: 'Trabajo 1' })
  await finalizado.getByRole('radio', { name: '4 estrellas', exact: true }).check()
  await finalizado.getByLabel('Comentario (opcional)').fill('  Muy puntual.  ')
  await finalizado.getByRole('button', { name: 'Enviar calificación' }).click()
  await expect(finalizado.getByRole('img', { name: '4 de 5 estrellas' })).toBeVisible()
  await expect(finalizado.getByText('«Muy puntual.»')).toBeVisible()
  await expect(finalizado.getByRole('button', { name: 'Enviar calificación' })).toHaveCount(0)
  expect(estado.escrituras).toEqual([
    { datos: { solicitud: 1, estrellas: 4, comentario: 'Muy puntual.' }, csrf: 'token-ficticio' },
  ])
})

test('un servicio que no está finalizado no muestra el formulario', async ({ page }) => {
  await preparar(page)
  await page.goto('/mis-servicios')
  const aceptado = page.getByRole('listitem').filter({ hasText: 'Trabajo 2' })
  await expect(aceptado.getByText('Aceptada')).toBeVisible()
  await expect(aceptado.getByText('Podrás calificarlo cuando el servicio esté Finalizado.')).toBeVisible()
  await expect(aceptado.getByRole('radio')).toHaveCount(0)
})

test('sin elegir estrellas no se envía', async ({ page }) => {
  const estado = await preparar(page)
  await page.goto('/mis-servicios')
  await page.getByRole('button', { name: 'Enviar calificación' }).click()
  await expect(page.getByRole('alert')).toHaveText('Elige de 1 a 5 estrellas.')
  expect(estado.escrituras).toHaveLength(0)
})

test('muestra el motivo cuando el servidor rechaza la calificación', async ({ page }) => {
  await preparar(page, { respuesta: [{ detail: 'Ya calificaste este servicio.' }, 409] })
  await page.goto('/mis-servicios')
  await page.getByRole('radio', { name: '5 estrellas', exact: true }).check()
  await page.getByRole('button', { name: 'Enviar calificación' }).click()
  await expect(page.getByRole('alert')).toHaveText('Ya calificaste este servicio.')
  await expect(page.getByRole('button', { name: 'Enviar calificación' })).toBeEnabled()
})

test('una calificación ya dada se muestra sin formulario', async ({ page }) => {
  await preparar(page, { servicios: [servicio(1, 'finalizada', {
    puede_calificar: false, calificacion: { estrellas: 2, comentario: 'Llegó tarde.', creada: '2026-11-21T10:00:00Z' },
  })] })
  await page.goto('/mis-servicios')
  await expect(page.getByRole('img', { name: '2 de 5 estrellas' })).toBeVisible()
  await expect(page.getByText('«Llegó tarde.»')).toBeVisible()
  await expect(page.getByRole('radio')).toHaveCount(0)
})

test('sin solicitudes invita a buscar un servicio', async ({ page }) => {
  await preparar(page, { servicios: [] })
  await page.goto('/mis-servicios')
  await expect(page.getByText('Aún no tienes solicitudes.')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Busca un servicio' })).toHaveAttribute('href', '/resultados')
})

test('si la lista falla se avisa sin mostrar datos', async ({ page }) => {
  await preparar(page, { falloLista: true })
  await page.goto('/mis-servicios')
  await expect(page.getByRole('alert')).toHaveText('No pudimos cargar tus servicios. Recarga la página.')
})

test('solo el cliente entra: visitante va a iniciar sesión y prestador vuelve al inicio', async ({ page }) => {
  await preparar(page, { usuario: null })
  await page.goto('/mis-servicios')
  await expect(page).toHaveURL(/\/ingresar$/)
  await page.context().unroute('**/api/**')
  await preparar(page, { usuario: { id: 2, username: 'ana', rol: 'prestador' } })
  await page.goto('/mis-servicios')
  await expect(page).toHaveURL(/\/$/)
})

test('el menú del cliente incluye Mis servicios', async ({ page }) => {
  await preparar(page)
  await page.goto('/')
  await expect(page.getByRole('navigation', { name: 'Menú principal' }).getByRole('link', { name: 'Mis servicios' })).toBeVisible()
})

test('se ve bien en un teléfono de 320 px', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 700 })
  await preparar(page)
  await page.goto('/mis-servicios')
  await expect(page.getByRole('button', { name: 'Enviar calificación' })).toBeVisible()
  const anchoExtra = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  expect(anchoExtra).toBeLessThanOrEqual(0)
})
