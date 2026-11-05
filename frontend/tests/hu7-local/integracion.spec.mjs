import { test, expect } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

const evidence = path.resolve('../docs/evidencias/hu7-local')
let fixtures
test.beforeAll(() => { fixtures = JSON.parse(fs.readFileSync(path.join(evidence, 'fixtures.json'), 'utf8')) })
const cards = (page) => page.locator('.lista-prestadores > li')
const markers = (page) => page.locator('.marcador-prestador')
const save = (name, data) => fs.writeFileSync(path.join(evidence, name + '.json'), JSON.stringify(data, null, 2))

function urlBusqueda(rubro, latitud = fixtures.centro.latitud, longitud = fixtures.centro.longitud) {
  return '/api/prestadores/buscar/?' + new URLSearchParams({ rubro, latitud, longitud })
}
async function preparar(page) {
  await page.goto('/resultados', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('combobox', { name: /Rubro/ })).toBeEnabled()
  await page.getByRole('combobox', { name: /Rubro/ }).selectOption(String(fixtures.rubros['Gasfitería']))
  await page.getByLabel('Latitud', { exact: true }).fill(String(fixtures.centro.latitud))
  await page.getByLabel('Longitud', { exact: true }).fill(String(fixtures.centro.longitud))
  await page.getByRole('button', { name: 'Usar coordenadas', exact: true }).click()
}
async function buscar(page) {
  const pending = page.waitForResponse((r) => r.url().includes('/api/prestadores/buscar/') && r.status() === 200)
  await page.getByRole('button', { name: 'Buscar', exact: true }).click()
  const response = await pending
  const data = await response.json()
  await expect(cards(page)).toHaveCount(data.length)
  await expect(markers(page)).toHaveCount(data.length)
  return data
}
async function consistencia(page, data) {
  await expect(cards(page).locator('h4')).toHaveText(data.map((p) => p.nombres + ' ' + p.apellidos))
  await expect(markers(page)).toHaveCount(data.length)
}
async function csrf(request) {
  const state = await request.storageState()
  return state.cookies.find((c) => c.name === 'csrftoken')?.value || ''
}

test('API real: rubro, estados, cobertura, coordenadas y entradas inválidas', async ({ request }) => {
  const response = await request.get(urlBusqueda(fixtures.rubros['Gasfitería']))
  expect(response.status()).toBe(200)
  const data = await response.json()
  expect(data.map((p) => p.id).sort((a, b) => a - b)).toEqual([
    fixtures.prestadores.Centro.id, fixtures.prestadores.Noreste.id, fixtures.prestadores.Compartido.id,
  ].sort((a, b) => a - b))
  for (const p of data) {
    expect(typeof p.latitud).toBe('number')
    expect(typeof p.longitud).toBe('number')
    const fixture = Object.values(fixtures.prestadores).find((f) => f.id === p.id)
    expect(p.latitud).toBe(fixture.latitud)
    expect(p.longitud).toBe(fixture.longitud)
    expect(Object.keys(p).sort()).toEqual(['id', 'nombres', 'apellidos', 'rubros', 'latitud', 'longitud', 'distancia_km'].sort())
  }
  const electrico = await (await request.get(urlBusqueda(fixtures.rubros.Electricidad))).json()
  expect(electrico.map((p) => p.id)).toEqual([fixtures.prestadores.Electricidad.id])
  const distancias = []
  for (const km of [4.9, 5.1]) {
    const lat = fixtures.centro.latitud - km / 6371.0088 * 180 / Math.PI
    const lista = await (await request.get(urlBusqueda(fixtures.rubros['Gasfitería'], lat))).json()
    expect(lista.some((p) => p.id === fixtures.prestadores.Centro.id)).toBe(km < 5)
    distancias.push({ km, ids: lista.map((p) => p.id) })
  }
  for (const query of ['rubro=1', 'rubro=1&latitud=NaN&longitud=0', 'rubro=1&latitud=91&longitud=0',
    'rubro=1&latitud=0&longitud=181', 'rubro=1&latitud=Infinity&longitud=0']) {
    expect((await request.get('/api/prestadores/buscar/?' + query)).status()).toBe(400)
  }
  save('api-real', { data, electrico, distancias })
})

test('coordenadas reales coinciden con la proyección de cada marcador', async ({ page }) => {
  await preparar(page)
  const data = await buscar(page)
  await consistencia(page, data)
  const calcular = () => page.evaluate((providers) => {
    const tile = [...document.querySelectorAll('img.leaflet-tile')].find((img) =>
      /\/(\d+)\/(\d+)\/(\d+)\.png/.test(img.src) && img.getBoundingClientRect().width > 250)
    if (!tile) return null
    const match = tile.src.match(/\/(\d+)\/(\d+)\/(\d+)\.png/)
    const [, zoom, tx, ty] = match.map(Number)
    const tileRect = tile.getBoundingClientRect()
    const scale = tileRect.width / 256
    const icons = [...document.querySelectorAll('.marcador-prestador')]
    return providers.map((p, i) => {
      const lat = p.latitud * Math.PI / 180
      const world = 256 * 2 ** zoom
      const x = (p.longitud + 180) / 360 * world
      const y = (1 - Math.log(Math.tan(lat) + 1 / Math.cos(lat)) / Math.PI) / 2 * world
      const expected = { x: tileRect.x + (x - tx * 256) * scale, y: tileRect.y + (y - ty * 256) * scale }
      const rect = icons[i].getBoundingClientRect()
      const actual = { x: rect.x + 18, y: rect.y + 36 }
      return { id: p.id, latitud: p.latitud, longitud: p.longitud, expected, actual,
        errorPx: Math.hypot(expected.x - actual.x, expected.y - actual.y) }
    })
  }, data)
  await expect.poll(async () => {
    const points = await calcular()
    return points ? Math.max(...points.map((p) => p.errorPx)) : Infinity
  }).toBeLessThan(2)
  save('proyeccion-marcadores', await calcular())
  await page.screenshot({ path: path.join(evidence, 'mapa-real-1280.png'), fullPage: true })
})

test('filtros reales limpian resultados y ubicación lejana muestra vacío', async ({ page }) => {
  await preparar(page)
  await buscar(page)
  await page.getByRole('combobox', { name: /Rubro/ }).selectOption(String(fixtures.rubros.Electricidad))
  await expect(cards(page)).toHaveCount(0)
  await expect(markers(page)).toHaveCount(0)
  await consistencia(page, await buscar(page))
  await page.getByLabel('Latitud', { exact: true }).fill('0')
  await expect(markers(page)).toHaveCount(0)
  await page.getByLabel('Longitud', { exact: true }).fill('0')
  await page.getByRole('button', { name: 'Usar coordenadas', exact: true }).click()
  expect(await buscar(page)).toEqual([])
  await expect(page.getByText(/No encontramos prestadores disponibles/)).toBeVisible()
  await page.screenshot({ path: path.join(evidence, 'sin-resultados.png'), fullPage: true })
})

test('respuesta real demorada y cancelada no repone datos del rubro anterior', async ({ page }) => {
  await preparar(page)
  let liberar, fetched
  const gate = new Promise((r) => { liberar = r })
  const ready = new Promise((r) => { fetched = r })
  await page.route('**/api/prestadores/buscar/**', async (route) => {
    const response = await route.fetch()
    const url = new URL(route.request().url())
    if (url.searchParams.get('rubro') === String(fixtures.rubros['Gasfitería'])) {
      fetched()
      await gate
    }
    await route.fulfill({ response }).catch(() => {})
  })
  await page.getByRole('button', { name: 'Buscar', exact: true }).click()
  await ready
  await expect(page.getByText('Buscando prestadores disponibles…')).toBeVisible()
  const cancel = page.waitForEvent('requestfailed', { predicate: (r) => r.url().includes('/api/prestadores/buscar/') })
  await page.getByRole('combobox', { name: /Rubro/ }).selectOption(String(fixtures.rubros.Electricidad))
  const aborted = await cancel
  const data = await buscar(page)
  liberar()
  await consistencia(page, data)
  await expect(cards(page)).toContainText('Ficticio Electricidad')
  save('cancelacion', { failure: aborted.failure()?.errorText, finalIds: data.map((p) => p.id),
    metodo: 'Respuesta de Django real demorada por Playwright; datos sin sustituir.' })
})

test('red desconectada emulada muestra error y recupera búsqueda real', async ({ page, context }) => {
  await preparar(page)
  await buscar(page)
  await context.setOffline(true)
  await page.getByRole('button', { name: 'Buscar', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('No pudimos comunicarnos')
  await expect(markers(page)).toHaveCount(0)
  await page.screenshot({ path: path.join(evidence, 'error-red.png'), fullPage: true })
  await context.setOffline(false)
  await consistencia(page, await buscar(page))
})

test('disponibilidad real mediante sesión/CSRF cambia lista y mapa', async ({ page }) => {
  await preparar(page)
  await buscar(page)
  await page.request.get('/api/usuarios/csrf/')
  const login = await page.request.post('/api/usuarios/login/', {
    headers: { 'X-CSRFToken': await csrf(page.request) },
    data: { username: fixtures.prestadores.Centro.username, password: 'SoloPruebasHU7!2026' },
  })
  expect(login.status()).toBe(200)
  try {
    expect((await page.request.patch('/api/usuarios/disponibilidad/', {
      headers: { 'X-CSRFToken': await csrf(page.request) }, data: { disponible: false },
    })).status()).toBe(200)
    const sinCentro = await buscar(page)
    expect(sinCentro.some((p) => p.id === fixtures.prestadores.Centro.id)).toBe(false)
    await consistencia(page, sinCentro)
  } finally {
    await page.request.patch('/api/usuarios/disponibilidad/', {
      headers: { 'X-CSRFToken': await csrf(page.request) }, data: { disponible: true },
    })
    await page.request.post('/api/usuarios/logout/', { headers: { 'X-CSRFToken': await csrf(page.request) } })
  }
  const restaurado = await buscar(page)
  expect(restaurado.some((p) => p.id === fixtures.prestadores.Centro.id)).toBe(true)
})

test('solicitud desde tarjeta se guarda realmente solo en la BD ficticia', async ({ page }) => {
  await preparar(page)
  const data = await buscar(page)
  const card = cards(page).first()
  await card.getByLabel('Tu nombre', { exact: true }).fill('Cliente Ficticio')
  await card.getByLabel('Teléfono: 9 dígitos sin +51').fill('000000000')
  await card.getByLabel('Describe el trabajo', { exact: true }).fill('Validación HU7 con datos ficticios')
  const sent = page.waitForResponse((r) => r.url().endsWith('/api/servicios/solicitudes/') && r.request().method() === 'POST')
  await card.getByRole('button', { name: 'Enviar solicitud' }).click()
  const response = await sent
  expect(response.status()).toBe(201)
  const solicitud = await response.json()
  expect(solicitud.prestador).toBe(data[0].id)
  expect(solicitud.estado).toBe('pendiente')
  await expect(card.getByText('Solicitud #' + solicitud.id)).toBeVisible()
  const persisted = await (await page.request.get('/api/servicios/solicitudes/' + solicitud.id + '/')).json()
  expect(persisted.id).toBe(solicitud.id)
  expect(persisted.estado).toBe('pendiente')
  save('solicitud-real', { id: solicitud.id, prestador: solicitud.prestador, estado: persisted.estado,
    entorno: 'Base SQLite temporal ficticia, sin Firebase.' })
})

// Coordenadas relativas al mapa: el scroll y el autopan del popup no son el encuadre inicial.
async function posicionRelativa(page) {
  const icon = await markers(page).first().boundingBox()
  const map = await page.locator('.mapa-busqueda').boundingBox()
  return { x: icon.x - map.x, y: icon.y - map.y }
}
async function popupDentro(page) {
  return page.evaluate(() => {
    const popup = document.querySelector('.leaflet-popup')
    if (!popup) return false
    const p = popup.getBoundingClientRect()
    const m = document.querySelector('.mapa-busqueda').getBoundingClientRect()
    return p.left >= m.left && p.right <= m.right && p.top >= m.top && p.bottom <= m.bottom
  })
}
async function marcadoresDentro(page) {
  return page.evaluate(() => {
    const m = document.querySelector('.mapa-busqueda').getBoundingClientRect()
    return [...document.querySelectorAll('.marcador-prestador')].every((e) => {
      const r = e.getBoundingClientRect()
      return r.left >= m.left && r.right <= m.right && r.top >= m.top && r.bottom <= m.bottom
    })
  })
}
for (const width of [320, 390, 1280]) {
 test('puntos compartidos y recuperación de encuadre a ' + width + ' px', async ({ page }) => {
  await page.setViewportSize({ width, height: 900 })
  await preparar(page)
  const data = await buscar(page)
  await expect.poll(() => marcadoresDentro(page)).toBe(true)
  const encuadreInicial = await posicionRelativa(page)
  const compartidos = markers(page).filter({ hasText: '+' })
  await expect(compartidos).toHaveCount(2)
  await compartidos.last().click()
  await expect(page.locator('.leaflet-popup-content')).toContainText('Ficticio Centro')
  await expect(page.locator('.leaflet-popup-content')).toContainText('Ficticio Compartido')
  await expect.poll(() => popupDentro(page)).toBe(true)
  const original = await page.locator('.ubicacion-elegida').textContent()
  await page.locator('.leaflet-popup-close-button').click()
  await expect(page.locator('.leaflet-popup-content')).toHaveCount(0)
  const map = page.locator('.mapa-busqueda')
  await map.scrollIntoViewIfNeeded()
  const box = await map.boundingBox()
  const antesArrastre = await posicionRelativa(page)
  await page.mouse.move(box.x + box.width / 2, box.y + 80)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2 + 90, box.y + 95, { steps: 10 })
  await page.mouse.up()
  await expect.poll(async () => Math.abs((await posicionRelativa(page)).x - antesArrastre.x)).toBeGreaterThan(20)
  await page.getByRole('button', { name: 'Ver ubicación y resultados' }).click()
  await expect.poll(async () => {
    const p = await posicionRelativa(page)
    return Math.hypot(p.x - encuadreInicial.x, p.y - encuadreInicial.y)
  }).toBeLessThan(2)
  await expect.poll(() => marcadoresDentro(page)).toBe(true)
  await expect(page.locator('.ubicacion-elegida')).toHaveText(original)
  await consistencia(page, data)
  save('encuadre-' + width, { ubicacion: original, ids: data.map((p) => p.id), encuadreInicial, restaurado: await posicionRelativa(page), cierre: true })
  await page.screenshot({ path: path.join(evidence, 'compartidos-' + width + '.png'), fullPage: true })
 })
}

for (const width of [320, 390, 1280]) {
  test('responsive real a ' + width + ' px', async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await preparar(page)
    await consistencia(page, await buscar(page))
    // El encuadre debe ser correcto antes del primer clic, sin botón de recuperación.
    await expect.poll(() => marcadoresDentro(page)).toBe(true)
    await markers(page).last().click()
    await expect(page.locator('.leaflet-popup-content')).toBeVisible()
    await expect.poll(async () => (await page.locator('.leaflet-popup').boundingBox())?.x ?? -1).toBeGreaterThanOrEqual(0)
    await expect.poll(async () => {
      const b = await page.locator('.leaflet-popup').boundingBox()
      return b ? b.x + b.width : Infinity
    }).toBeLessThanOrEqual(width + 1)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await expect.poll(() => popupDentro(page)).toBe(true)
    await page.screenshot({ path: path.join(evidence, 'responsive-' + width + '.png'), fullPage: true })
    await page.locator('.leaflet-popup-close-button').click()
    await expect(page.locator('.leaflet-popup-content')).toHaveCount(0)
  })
}

test('teselas OSM reales y teclado sin cambiar la ubicación', async ({ page }) => {
  await preparar(page)
  await buscar(page)
  await expect(page.getByRole('region', { name: 'Mapa de prestadores y ubicación' })).toBeVisible()
  await markers(page).first().focus()
  await page.keyboard.press('Enter')
  await expect(page.locator('.leaflet-popup-content')).toBeVisible()
  const loaded = page.locator('img.leaflet-tile-loaded')
  await expect.poll(() => loaded.count(), { timeout: 15000 }).toBeGreaterThan(0)
  save('teselas-reales', { loadedTiles: await loaded.count(), apiSimulada: false, teselasSimuladas: false })
})
