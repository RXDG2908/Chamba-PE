import { test, expect } from '@playwright/test'
import { Buffer } from 'node:buffer'

const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6Fz0AAAAASUVORK5CYII=', 'base64')
const foto = { name: 'ficticia.png', mimeType: 'image/png', buffer: png }

async function preparar(page, cambios = {}) {
  const estado = {
    vigente: false, estado: 'ausente', version_texto: 'demo-dni-v2', eliminacion_pendiente: false,
    falloConsulta: false, falloIntento: false, falloRedIntento: false, falloVersion: false,
    revocarAlEnviar: false, usuario: { id: 1, rol: 'prestador', nombres: 'Ficticio', prestador_id: 1 },
    escrituras: [], consultas: 0, ...cambios,
  }
  await page.addInitScript(() => {
    window.llamadasCamara = 0
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: {
      getUserMedia: () => {
        window.llamadasCamara++
        return Promise.reject(new DOMException('Camara ficticia no disponible', 'NotAllowedError'))
      },
    } })
  })
  await page.context().addCookies([{ name: 'csrftoken', value: 'token-ficticio', url: 'http://127.0.0.1:5191' }])
  await page.context().route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/usuarios/csrf/') return json({})
    if (path === '/api/usuarios/yo/') return estado.usuario ? json(estado.usuario) : json({ detail: 'Sin sesión' }, 403)
    if (path === '/api/prestadores/rubros/') return json([])
    if (path === '/api/verificacion/consentimiento/' && request.method() === 'GET') {
      estado.consultas++
      if (estado.falloRedConsentimiento) return route.abort('failed')
      if (estado.falloConsulta) return json({}, 503)
      if (Object.hasOwn(estado, 'respuestaConsentimiento')) return json(estado.respuestaConsentimiento)
      return json({
        finalidad: 'verificacion_dni', version_texto: estado.version_texto,
        texto: 'Texto de prueba provisional para verificar el DNI. No autoriza reconocimiento facial.',
        provisional: true, vigente: estado.vigente, estado: estado.estado,
        aceptado_en: null, revocado_en: null,
        eliminacion_pendiente: estado.eliminacion_pendiente,
      })
    }
    if (path === '/api/verificacion/consentimiento/' && request.method() === 'POST') {
      const datos = request.postDataJSON()
      estado.escrituras.push({ path, datos, csrf: request.headers()['x-csrftoken'] })
      if (estado.falloVersion) return json({ version_texto: ['El texto cambió. Consulta la versión vigente.'] }, 400)
      estado.vigente = true
      estado.estado = 'vigente'
      return json({ vigente: true, estado: 'vigente', version_texto: estado.version_texto,
        texto: 'Texto de prueba provisional.', finalidad: 'verificacion_dni', provisional: true,
        eliminacion_pendiente: estado.eliminacion_pendiente }, 201)
    }
    if (path === '/api/verificacion/consentimiento/revocar/') {
      estado.escrituras.push({ path, csrf: request.headers()['x-csrftoken'] })
      estado.vigente = false
      estado.estado = 'revocado'
      return json({ vigente: false, estado: 'revocado', version_texto: estado.version_texto,
        texto: 'Texto de prueba provisional.', finalidad: 'verificacion_dni', provisional: true,
        eliminacion_pendiente: estado.eliminacion_pendiente })
    }
    if (path === '/api/verificacion/dni/') {
      if (request.method() === 'GET' && estado.falloRedIntento) return route.abort('failed')
      if (request.method() === 'GET') return json({}, estado.falloIntento ? 500 : 404)
      estado.escrituras.push({ path })
      if (estado.revocarAlEnviar) { estado.vigente = false; estado.estado = 'revocado' }
      return json({ detail: 'Consentimiento requerido' }, 403)
    }
    return json({}, 404)
  })
  return estado
}

async function adjuntarAmbos(page) {
  await page.locator('input[type=file]').setInputFiles(foto)
  await expect(page.getByText('Paso 2 de 2:')).toBeVisible()
  await page.locator('input[type=file]').setInputFiles(foto)
  await expect(page.getByRole('button', { name: 'Enviar para validar' })).toBeVisible()
}

const permisosDesconocidos = [
  ['sin barrera', { vigente: true }],
  ['sin vigencia', { eliminacion_pendiente: false }],
  ['barrera nula', { vigente: true, eliminacion_pendiente: null }],
  ['vigencia nula', { vigente: null, eliminacion_pendiente: false }],
  ['barrera como texto', { vigente: true, eliminacion_pendiente: 'false' }],
  ['vigencia como texto', { vigente: 'true', eliminacion_pendiente: false }],
  ['cuerpo nulo', null],
]

for (const [nombre, respuesta] of permisosDesconocidos) {
  test('regresión permiso desconocido al abrir: ' + nombre, async ({ page }) => {
    await preparar(page, { respuestaConsentimiento: respuesta })
    await page.goto('/verificar-dni')
    await expect(page.getByRole('alert')).toContainText('No pudimos comprobar')
    await expect(page.locator('input[type=file]')).toHaveCount(0)
    expect(await page.evaluate(() => window.llamadasCamara)).toBe(0)
  })

  test('regresión permiso desconocido antes de enviar: ' + nombre, async ({ page }) => {
    const estado = await preparar(page, { vigente: true, estado: 'vigente' })
    await page.goto('/verificar-dni')
    await adjuntarAmbos(page)
    estado.respuestaConsentimiento = respuesta
    await page.getByRole('button', { name: 'Enviar para validar' }).click()
    await expect(page.getByRole('alert')).toContainText('No pudimos comprobar')
    await expect(page.locator('.vistas img')).toHaveCount(0)
    await expect(page.locator('input[type=file]')).toHaveCount(0)
    expect(estado.escrituras).toHaveLength(0)
    delete estado.respuestaConsentimiento
    await page.getByRole('button', { name: 'Reintentar consulta' }).click()
    await expect(page.locator('input[type=file]')).toBeVisible()
  })
}

for (const fallo of ['falloConsulta', 'falloRedConsentimiento']) {
  test('regresión error de permiso antes de enviar: ' + fallo, async ({ page }) => {
    const estado = await preparar(page, { vigente: true, estado: 'vigente' })
    await page.goto('/verificar-dni')
    await adjuntarAmbos(page)
    estado[fallo] = true
    await page.getByRole('button', { name: 'Enviar para validar' }).click()
    await expect(page.getByRole('alert')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Reintentar consulta' })).toBeVisible()
    await expect(page.locator('.vistas img')).toHaveCount(0)
    await expect(page.locator('input[type=file]')).toHaveCount(0)
    expect(estado.escrituras).toHaveLength(0)
  })
}

test('sin consentimiento no abre cámara ni selector y exige aceptación explícita', async ({ page }) => {
  await preparar(page)
  await page.goto('/verificar-dni')
  await expect(page).toHaveURL(/\/consentimiento$/)
  await expect(page.getByRole('heading', { name: 'Consentimiento para verificar mi DNI' })).toBeVisible()
  await expect(page.getByText(/^Demostración:/)).toBeVisible()
  await expect(page.getByRole('checkbox')).not.toBeChecked()
  await expect(page.getByRole('button', { name: 'Aceptar consentimiento' })).toBeDisabled()
  await expect(page.locator('input[type=file]')).toHaveCount(0)
  expect(await page.evaluate(() => window.llamadasCamara)).toBe(0)
})

test('acepta con CSRF, confirma y permite continuar a la captura', async ({ page }) => {
  const estado = await preparar(page)
  await page.goto('/consentimiento')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Aceptar consentimiento' }).click()
  await expect(page.getByRole('status')).toHaveText('Consentimiento registrado correctamente.')
  expect(estado.escrituras[0].datos).toEqual({ acepto: true, version_texto: 'demo-dni-v2' })
  expect(estado.escrituras[0].csrf).toBe('token-ficticio')
  await page.getByRole('link', { name: 'Continuar a verificar mi DNI' }).click()
  await expect(page.locator('input[type=file]')).toBeVisible()
  expect(await page.evaluate(() => window.llamadasCamara)).toBeGreaterThan(0)
})

test('revocación requiere confirmación; cancelar no escribe y confirmar bloquea captura', async ({ page }) => {
  const estado = await preparar(page, { vigente: true, estado: 'vigente' })
  await page.goto('/consentimiento')
  await page.getByRole('button', { name: 'Revocar consentimiento', exact: true }).click()
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click()
  expect(estado.escrituras).toHaveLength(0)
  await page.getByRole('button', { name: 'Revocar consentimiento', exact: true }).click()
  await page.getByRole('button', { name: 'Confirmar revocación', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Consentimiento revocado')
  await expect(page.getByRole('checkbox')).not.toBeChecked()
  await page.getByRole('link', { name: 'Verificar mi DNI', exact: true }).click()
  await expect(page).toHaveURL(/\/consentimiento$/)
  expect(await page.evaluate(() => window.llamadasCamara)).toBe(0)
})

test('versión anterior exige una nueva aceptación', async ({ page }) => {
  await preparar(page, { estado: 'desactualizado' })
  await page.goto('/consentimiento')
  await expect(page.getByText('Versión anterior', { exact: true })).toBeVisible()
  await expect(page.getByRole('checkbox')).not.toBeChecked()
})

test('error consultando consentimiento mantiene cámara bloqueada y permite reintento', async ({ page }) => {
  const estado = await preparar(page, { falloConsulta: true })
  await page.goto('/verificar-dni')
  await expect(page.getByRole('alert')).toContainText('No pudimos consultar tu consentimiento')
  expect(await page.evaluate(() => window.llamadasCamara)).toBe(0)
  await expect(page.locator('input[type=file]')).toHaveCount(0)
  estado.falloConsulta = false
  await page.getByRole('button', { name: 'Reintentar consulta' }).click()
  await expect(page).toHaveURL(/\/consentimiento$/)
})

test('error al consultar intento anterior no abre cámara', async ({ page }) => {
  await preparar(page, { vigente: true, estado: 'vigente', falloIntento: true })
  await page.goto('/verificar-dni')
  await expect(page.getByRole('alert')).toContainText('No pudimos consultar tu último intento')
  expect(await page.evaluate(() => window.llamadasCamara)).toBe(0)
})

test('error de red al consultar intento informa en español y conserva el bloqueo', async ({ page }) => {
  await preparar(page, { vigente: true, estado: 'vigente', falloRedIntento: true })
  await page.goto('/verificar-dni')
  await expect(page.getByRole('alert')).toHaveText('No hay conexión con el servidor. Revisa tu internet.')
  await expect(page.locator('input[type=file]')).toHaveCount(0)
  expect(await page.evaluate(() => window.llamadasCamara)).toBe(0)
})

test('revocación después de capturar impide enviar las fotografías', async ({ page }) => {
  const estado = await preparar(page, { vigente: true, estado: 'vigente' })
  await page.goto('/verificar-dni')
  await adjuntarAmbos(page)
  estado.vigente = false
  estado.estado = 'revocado'
  await page.getByRole('button', { name: 'Enviar para validar' }).click()
  await expect(page).toHaveURL(/\/consentimiento$/)
  expect(estado.escrituras.filter((e) => e.path === '/api/verificacion/dni/')).toHaveLength(0)
})

test('403 definitivo del backend devuelve a consentimiento tras una revocación concurrente', async ({ page }) => {
  await preparar(page, { vigente: true, estado: 'vigente', revocarAlEnviar: true })
  await page.goto('/verificar-dni')
  await adjuntarAmbos(page)
  await page.getByRole('button', { name: 'Enviar para validar' }).click()
  await expect(page).toHaveURL(/\/consentimiento$/)
  await expect(page.locator('input[type=file]')).toHaveCount(0)
})

test('rechazo por versión cambiada muestra error y permite consultar el texto vigente', async ({ page }) => {
  const estado = await preparar(page, { falloVersion: true })
  await page.goto('/consentimiento')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Aceptar consentimiento' }).click()
  await expect(page.getByRole('alert')).toContainText('El texto cambió')
  estado.falloVersion = false
  estado.version_texto = 'demo-dni-v2'
  await page.getByRole('button', { name: 'Consultar estado actual' }).click()
  await expect(page.getByText('demo-dni-v2', { exact: true })).toBeVisible()
  await expect(page.getByRole('checkbox')).not.toBeChecked()
})

for (const rol of [null, 'cliente', 'administrador']) {
  test('ruta privada rechaza ' + (rol || 'anónimo'), async ({ page }) => {
    const estado = await preparar(page, { usuario: rol ? { id: 2, nombres: 'Ficticio', rol } : null })
    await page.goto('/consentimiento')
    await expect(page).not.toHaveURL(/\/consentimiento$/)
    expect(estado.consultas).toBe(0)
    expect(await page.evaluate(() => window.llamadasCamara)).toBe(0)
  })
}

for (const width of [320, 390, 1280]) {
  test('consentimiento y confirmación sin desbordamiento a ' + width + ' px', async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await preparar(page, { vigente: true, estado: 'vigente' })
    await page.goto('/consentimiento')
    await page.getByRole('button', { name: 'Revocar consentimiento', exact: true }).click()
    await expect(page.getByRole('button', { name: 'Confirmar revocación', exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.getByRole('button', { name: 'Confirmar revocación', exact: true }).click()
    await expect(page.getByRole('checkbox')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: test.info().outputPath('consentimiento-' + width + '.png'), fullPage: true })
  })
}
