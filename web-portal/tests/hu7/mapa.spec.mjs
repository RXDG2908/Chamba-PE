import { test, expect } from '@playwright/test'

// Diagnóstico de red sin alterar respuestas ni tiempos de la aplicación.
const redes = new WeakMap()
test.beforeEach(async ({ page }) => {
  const eventos = []
  redes.set(page, eventos)
  for (const evento of ['request', 'response', 'requestfinished', 'requestfailed']) {
    page.on(evento, (dato) => {
      const req = evento === 'response' ? dato.request() : dato
      if (!req.url().includes('/api/')) return
      eventos.push({ evento, url: req.url(), estado: evento === 'response' ? dato.status() : undefined,
        fallo: evento === 'requestfailed' ? req.failure()?.errorText : undefined, tiempo: Date.now() })
    })
  }
})
test.afterEach(async ({ page }, info) => {
  await info.attach('red-api', { body: JSON.stringify({ eventos: redes.get(page),
    estado: await page.getByRole('status').allTextContents().catch(() => []),
    tarjetas: await page.locator('.lista-prestadores > li').count().catch(() => -1) }, null, 2), contentType: 'application/json' })
})


const centro = { latitud: -12.046374, longitud: -77.042793 }
const rubros = [{ id: 1, nombre: 'Gasfitería' }, { id: 2, nombre: 'Electricidad' }]
const persona = (id, cambios = {}) => ({
  id, nombres: id === 1 ? 'Ana' : 'Beto', apellidos: 'Prueba',
  rubros: [rubros[0]], distancia_km: 0, ...centro, ...cambios,
})
const par = [persona(1), persona(2, {
  latitud: centro.latitud + 0.01, longitud: centro.longitud + 0.01, distancia_km: 1.5,
})]

async function preparar(page, opciones = {}) {
  const errores = []
  page.on('pageerror', (e) => errores.push(e.message))
  await page.context().route('https://tile.openstreetmap.org/**', (r) => r.fulfill({
    contentType: 'image/png',
    body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==', 'base64'),
  }))
  // Mantener la interceptación en el contexto aislado, también durante el arranque de sesión.
  await page.context().route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/csrf/')) return route.fulfill({ json: {} })
    if (url.pathname.endsWith('/yo/')) return route.fulfill({ status: 403, json: {} })
    if (url.pathname.endsWith('/rubros/')) return route.fulfill({ json: rubros })
    if (url.pathname.endsWith('/buscar/')) {
      if (opciones.buscar) return opciones.buscar(route, url)
      return route.fulfill({ json: opciones.datos ?? par })
    }
    return route.fulfill({ status: 404, json: {} })
  })
  await page.goto('/resultados', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('combobox', { name: /Rubro/ })).toBeEnabled()
  await page.getByRole('combobox', { name: /Rubro/ }).selectOption('1')
  await page.getByLabel('Latitud', { exact: true }).fill(String(centro.latitud))
  await page.getByLabel('Longitud', { exact: true }).fill(String(centro.longitud))
  await page.getByRole('button', { name: 'Usar coordenadas', exact: true }).click()
  return errores
}
async function buscar(page) {
  await page.getByRole('button', { name: 'Buscar', exact: true }).click()
}
const marcadores = (page) => page.locator('.marcador-prestador')
const tarjetas = (page) => page.locator('.lista-prestadores > li')

test('mapa/lista conservan personas, coordenadas relativas y popup sin mover al cliente', async ({ page }) => {
  const errores = await preparar(page)
  await buscar(page)
  await expect(tarjetas(page)).toHaveCount(2)
  await expect(marcadores(page)).toHaveCount(2)
  await expect(marcadores(page).nth(0)).toHaveAttribute('title', /Ana/)
  const [a, b] = await Promise.all([marcadores(page).nth(0).boundingBox(), marcadores(page).nth(1).boundingBox()])
  expect(b.x).toBeGreaterThan(a.x)
  expect(b.y).toBeLessThan(a.y)
  const ubicacion = await page.locator('.ubicacion-elegida').textContent()
  await marcadores(page).first().click()
  await expect(page.locator('.leaflet-popup-content')).toContainText('Ana Prueba')
  await expect(page.locator('.leaflet-popup-content')).toContainText('Gasfitería')
  await expect(page.locator('.leaflet-popup-content')).toContainText('Sin calificaciones')
  await page.locator('.leaflet-popup-content strong').click()
  await expect(tarjetas(page)).toHaveCount(2)
  await expect(page.locator('.ubicacion-elegida')).toHaveText(ubicacion)
  expect(errores).toEqual([])
})

test('coordenadas compartidas con cliente permiten consultar a todos', async ({ page }) => {
  await preparar(page, { datos: [persona(1), persona(2)] })
  await buscar(page)
  await expect(marcadores(page)).toHaveCount(2)
  await marcadores(page).last().click()
  const popup = page.locator('.leaflet-popup-content')
  await expect(popup).toContainText('Ana Prueba')
  await expect(popup).toContainText('Beto Prueba')
  await expect(popup.locator('article')).toHaveCount(2)
  await expect(tarjetas(page)).toHaveCount(2)
  await expect(page.locator('.ubicacion-elegida')).toContainText(String(centro.latitud))
})

test('cambiar rubro o coordenadas retira tarjetas y marcadores anteriores', async ({ page }) => {
  await preparar(page)
  await buscar(page)
  await expect(tarjetas(page)).toHaveCount(2)
  await page.getByRole('combobox', { name: /Rubro/ }).selectOption('2')
  await expect(tarjetas(page)).toHaveCount(0)
  await expect(marcadores(page)).toHaveCount(0)
  await buscar(page)
  await expect(tarjetas(page)).toHaveCount(2)
  await page.getByLabel('Latitud', { exact: true }).fill('-12.05')
  await expect(tarjetas(page)).toHaveCount(0)
  await expect(marcadores(page)).toHaveCount(0)
  await expect(page.locator('.ubicacion-elegida')).toContainText('Todavía no elegiste')
})

test('carga, cancelación y respuesta tardía no reponen el rubro anterior', async ({ page }) => {
  let liberar
  let finalizada
  const listo = new Promise((r) => { finalizada = r })
  const espera = new Promise((r) => { liberar = r })
  await preparar(page, { buscar: async (route, url) => {
    if (url.searchParams.get('rubro') === '1') {
      await espera
      await route.fulfill({ json: [persona(1)] }).catch(() => {})
      finalizada()
    } else await route.fulfill({ json: [persona(2)] })
  } })
  await buscar(page)
  await expect(page.getByText('Buscando prestadores disponibles…')).toBeVisible()
  await expect(page.locator('section[aria-busy="true"]')).toBeVisible()
  await page.getByRole('combobox', { name: /Rubro/ }).selectOption('2')
  await buscar(page)
  await expect(tarjetas(page)).toHaveCount(1)
  await expect(tarjetas(page)).toContainText('Beto')
  liberar()
  await listo
  await expect(tarjetas(page)).toContainText('Beto')
  await expect(tarjetas(page)).not.toContainText('Ana')
  await expect(marcadores(page)).toHaveCount(1)
})

test('vacío, error HTTP, error de red y recuperación sin restos', async ({ page }) => {
  let modo = 'ok'
  await preparar(page, { buscar: (route) => {
    if (modo === 'red') return route.abort()
    if (modo === 'http') return route.fulfill({ status: 400, json: { rubro: ['Rubro de prueba inválido.'] } })
    return route.fulfill({ json: modo === 'vacio' ? [] : par })
  } })
  await buscar(page)
  await expect(tarjetas(page)).toHaveCount(2)
  modo = 'vacio'
  await buscar(page)
  await expect(page.getByText(/No encontramos prestadores disponibles/)).toBeVisible()
  await expect(marcadores(page)).toHaveCount(0)
  modo = 'http'
  await buscar(page)
  await expect(page.getByRole('alert')).toContainText('Rubro de prueba inválido')
  modo = 'red'
  await buscar(page)
  await expect(page.getByRole('alert')).toContainText('No pudimos comunicarnos')
  await expect(tarjetas(page)).toHaveCount(0)
  modo = 'ok'
  await buscar(page)
  await expect(tarjetas(page)).toHaveCount(2)
})

for (const [caso, dato] of [
  ['latitud ausente', { longitud: centro.longitud }],
  ['longitud nula', { ...centro, longitud: null }],
  ['texto', { ...centro, latitud: '-12' }],
  ['fuera de rango', { ...centro, longitud: 181 }],
]) {
  test('respuesta con coordenadas inválidas: ' + caso, async ({ page }) => {
    const errores = await preparar(page, { datos: [{ ...persona(1), latitud: undefined, ...dato }] })
    await buscar(page)
    await expect(page.getByRole('alert')).toContainText('coordenadas inválidas')
    await expect(tarjetas(page)).toHaveCount(0)
    await expect(marcadores(page)).toHaveCount(0)
    expect(errores).toEqual([])
  })
}

test('respuesta mal formada con coordenadas válidas muestra error sin romper React', async ({ page }) => {
  const errores = await preparar(page, { datos: [{ id: 1, ...centro }] })
  await buscar(page)
  await expect(page.getByRole('alert')).toBeVisible()
  expect(errores).toEqual([])
})

test('teclado abre marcador, recupera encuadre y mapa tiene nombre accesible', async ({ page }) => {
  let consultas = 0
  await preparar(page, { buscar: (r) => { consultas++; return r.fulfill({ json: par }) } })
  await buscar(page)
  await expect(marcadores(page)).toHaveCount(2)
  await expect(page.getByRole('region', { name: 'Mapa de prestadores y ubicación' })).toBeVisible()
  await marcadores(page).first().focus()
  await page.keyboard.press('Enter')
  await expect(page.locator('.leaflet-popup-content')).toContainText('Ana Prueba')
  await page.getByRole('button', { name: 'Ver ubicación y resultados' }).click()
  expect(consultas).toBe(1)
  await expect(tarjetas(page)).toHaveCount(2)
})

for (const ancho of [320, 390, 1280]) {
  test('responsive sin desbordamiento y popup usable a ' + ancho + ' px', async ({ page }) => {
    await page.setViewportSize({ width: ancho, height: 900 })
    await preparar(page, { datos: [persona(1, { apellidos: 'NombreMuyLargo'.repeat(8) })] })
    await buscar(page)
    await expect(tarjetas(page)).toHaveCount(1)
    await marcadores(page).first().click()
    await expect(page.locator('.leaflet-popup-content')).toBeVisible()
    const medidas = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, ancho: innerWidth }))
    expect(medidas.scroll).toBeLessThanOrEqual(medidas.ancho)
    // Leaflet anima el ajuste de posición: comprobar el resultado estable.
    await expect.poll(async () => (await page.locator('.leaflet-popup').boundingBox())?.x ?? -1).toBeGreaterThanOrEqual(0)
    await expect.poll(async () => {
      const popup = await page.locator('.leaflet-popup').boundingBox()
      return popup ? popup.x + popup.width : Infinity
    }).toBeLessThanOrEqual(ancho + 1)
  })
}

test('seleccionar punto libre limpia resultados; volver al mapa no cambia ubicación', async ({ page }) => {
  await preparar(page)
  await buscar(page)
  await expect(marcadores(page)).toHaveCount(2)
  await page.locator('.mapa-busqueda').click({ position: { x: 45, y: 45 } })
  await expect(marcadores(page)).toHaveCount(0)
  await expect(tarjetas(page)).toHaveCount(0)
  await expect(page.locator('.ubicacion-elegida')).not.toContainText('-12.046374, -77.042793')
})

test('solicitar desde tarjeta conserva prestador, rubro y coordenadas elegidas', async ({ page }) => {
  await preparar(page)
  let enviada
  await page.context().route('**/api/servicios/solicitudes/', async (route) => {
    enviada = route.request().postDataJSON()
    await route.fulfill({ status: 201, json: { id: 42, estado: 'pendiente' } })
  })
  await buscar(page)
  const tarjeta = tarjetas(page).first()
  await tarjeta.getByLabel('Tu nombre', { exact: true }).fill('Cliente Prueba')
  await tarjeta.getByLabel('Teléfono: 9 dígitos sin +51', { exact: true }).fill('900000000')
  await tarjeta.getByLabel('Describe el trabajo', { exact: true }).fill('Prueba de mapa aislada')
  await tarjeta.getByRole('button', { name: 'Enviar solicitud' }).click()
  await expect(tarjeta.getByText('Solicitud #42')).toBeVisible()
  expect(enviada).toMatchObject({ prestador: 1, rubro: 1, latitud: '-12.046374', longitud: '-77.042793' })
})
