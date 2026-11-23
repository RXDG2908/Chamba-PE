import { test, expect } from '@playwright/test'
import { Buffer } from 'node:buffer'

const id = '00000000-0000-4000-8000-000000000021'
const momento = '2026-11-05T12:00:00Z'
const evento = (estado, ejecucion = 0) => ({ estado, anterior: '', fecha: momento, ejecucion, codigo: '', pendientes: 0 })
const solicitud = (estado = 'pendiente') => ({
  id, estado, creada: momento, actualizada: momento, atendida_en: null,
  version_alcance: 'verificacion_dni-v1', alcance: ['fotografias_dni', 'datos_leidos'],
  ejecuciones: 0, codigo_error: '', pendientes: 0, eventos: [evento('pendiente')],
})

async function preparar(page, opciones = {}) {
  const e = { usuario: { id: 1, rol: 'prestador', nombres: 'Ficticio' }, solicitudes: [],
    falloConsulta: false, falloCsrf: false, respuestaPerdida: false, conflicto: false,
    bloqueoCaptura: false, rechazoCaptura: false, peticiones: [], consultas: 0, ...opciones }
  await page.context().addCookies([{ name: 'csrftoken', value: 'csrf-ficticio-hu21', url: 'http://127.0.0.1:5191' }])
  await page.addInitScript(() => {
    window.camaras = 0
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: () => {
      window.camaras++
      return Promise.reject(new DOMException('Cámara simulada', 'NotAllowedError'))
    } } })
  })
  await page.context().route('**/api/**', async (route) => {
    const r = route.request()
    const path = new URL(r.url()).pathname
    const json = (body, status = 200) => route.fulfill({ json: body, status })
    if (path.endsWith('/csrf/')) return json({})
    if (path.endsWith('/yo/')) return e.usuario ? json(e.usuario) : json({}, 403)
    if (path === '/api/prestadores/rubros/') return json([])
    if (path === '/api/verificacion/consentimiento/') return json({ vigente: true, eliminacion_pendiente: e.bloqueoCaptura })
    if (path === '/api/verificacion/dni/') return r.method() === 'GET' ? json({}, 404) : json({ detail: 'Eliminación en curso.' }, e.rechazoCaptura ? 409 : 403)
    if (path === '/api/verificacion/eliminaciones/' && r.method() === 'GET') {
      e.consultas++
      return e.falloConsulta ? json({}, 503) : json({ bloqueada: e.solicitudes.some((s) => s.estado !== 'atendida'), solicitudes: e.solicitudes })
    }
    if (path === '/api/verificacion/eliminaciones/' && r.method() === 'POST') {
      const body = r.postDataJSON()
      e.peticiones.push({ ...body, csrf: r.headers()['x-csrftoken'] })
      if (e.falloCsrf) return json({ detail: 'No se pudo comprobar CSRF. Revisa tu sesión.' }, 403)
      if (e.conflicto) return json({ detail: 'Ya tienes una solicitud.', solicitud: e.solicitudes[0] }, 409)
      if (!e.solicitudes.length) e.solicitudes.push(solicitud(e.estadoRegistro || 'pendiente'))
      if (e.respuestaPerdida) { e.respuestaPerdida = false; return route.abort('failed') }
      return json(e.solicitudes[0], e.peticiones.length === 1 ? 202 : 200)
    }
    if (path.endsWith('/reintentar/')) {
      const s = e.solicitudes[0]
      s.estado = 'pendiente'
      s.eventos.push(evento('pendiente', 1))
      s.codigo_error = ''
      return json(s, 202)
    }
    return json({}, 404)
  })
  return e
}

// Retiene un cuerpo ya recibido: abortar fetch no basta para invalidar su continuación.
async function retenerSegundaConsulta(page, fallar = false) {
  await page.addInitScript(({ fallar }) => {
    const original = window.fetch.bind(window)
    let consultas = 0
    window.fetch = async (...args) => {
      const respuesta = await original(...args)
      if (String(args[0]) === '/api/verificacion/eliminaciones/' && !args[1]?.method && ++consultas === 2) {
        const json = respuesta.json.bind(respuesta)
        respuesta.json = async () => {
          const cuerpo = await json()
          window.consultaRetenida = true
          await new Promise((resolve) => { window.liberarConsulta = resolve })
          if (fallar) throw new TypeError('Fallo antiguo simulado')
          return cuerpo
        }
      }
      return respuesta
    }
  }, { fallar })
}

for (const estado of ['pendiente', 'procesando', 'parcial', 'fallida', 'atendida']) {
  test('regresión consulta antigua no reemplaza POST ' + estado, async ({ page }) => {
    await preparar(page, { estadoRegistro: estado })
    await retenerSegundaConsulta(page)
    await page.goto('/eliminar-datos')
    await page.getByRole('checkbox').check()
    await page.getByRole('button', { name: 'Consultar estado', exact: true }).click()
    await page.waitForFunction(() => window.consultaRetenida === true)
    await page.getByRole('button', { name: 'Solicitar eliminación', exact: true }).click()
    await expect(page.getByText('Solicitud: ' + id, { exact: true })).toBeVisible()
    await page.evaluate(async () => {
      window.liberarConsulta()
      await new Promise(requestAnimationFrame)
      await new Promise(requestAnimationFrame)
    })
    await expect(page.getByText('Solicitud: ' + id, { exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: estado[0].toUpperCase() + estado.slice(1), exact: true })).toBeVisible()
    await expect(page.getByText('Todavía no tienes solicitudes de eliminación.', { exact: true })).toHaveCount(0)
  })
}

test('regresión error de consulta antigua no borra el POST confirmado', async ({ page }) => {
  await preparar(page)
  await retenerSegundaConsulta(page, true)
  await page.goto('/eliminar-datos')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Consultar estado', exact: true }).click()
  await page.waitForFunction(() => window.consultaRetenida === true)
  await page.getByRole('button', { name: 'Solicitar eliminación', exact: true }).click()
  await expect(page.getByText('Solicitud: ' + id, { exact: true })).toBeVisible()
  await page.evaluate(async () => {
    window.liberarConsulta()
    await new Promise(requestAnimationFrame)
    await new Promise(requestAnimationFrame)
  })
  await expect(page.getByText('Solicitud: ' + id, { exact: true })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('solicita con confirmación explícita y CSRF; 202 no anuncia borrado atendido', async ({ page }) => {
  const e = await preparar(page)
  await page.goto('/eliminar-datos')
  await expect(page.getByText('Conserva tu cuenta y el DNI registrado en tu perfil.')).toBeVisible()
  const boton = page.getByRole('button', { name: 'Solicitar eliminación' })
  await expect(boton).toBeDisabled()
  await page.getByRole('checkbox').check()
  await boton.click()
  await expect(page.getByRole('heading', { name: 'Pendiente', exact: true })).toBeVisible()
  await expect(page.getByText('La eliminación todavía no está comprobada como completa.')).toBeVisible()
  expect(e.peticiones).toHaveLength(1)
  expect(e.peticiones[0].confirmo).toBe(true)
  expect(e.peticiones[0].csrf).toBe('csrf-ficticio-hu21')
  await expect(page.getByRole('checkbox')).toHaveCount(0)
})

test('respuesta perdida conserva clave de idempotencia y no duplica solicitud', async ({ page }) => {
  const e = await preparar(page, { respuestaPerdida: true })
  await page.goto('/eliminar-datos')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Solicitar eliminación' }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await page.getByRole('button', { name: 'Solicitar eliminación' }).click()
  await expect(page.getByRole('heading', { name: 'Pendiente', exact: true })).toBeVisible()
  expect(e.peticiones).toHaveLength(2)
  expect(e.peticiones[0].clave_idempotencia).toBe(e.peticiones[1].clave_idempotencia)
  expect(e.solicitudes).toHaveLength(1)
})

for (const estado of ['parcial', 'fallida']) {
  test('reintenta ' + estado + ' conservando el historial y el bloqueo', async ({ page }) => {
    const s = { ...solicitud(estado), codigo_error: 'almacenamiento', pendientes: 1,
      eventos: [evento('pendiente'), evento('procesando', 1), evento(estado, 1)] }
    await preparar(page, { solicitudes: [s] })
    await page.goto('/eliminar-datos')
    await expect(page.getByText('Hay archivos que no se pudieron eliminar.')).toBeVisible()
    await page.getByRole('button', { name: 'Reintentar eliminación' }).click()
    await expect(page.getByRole('heading', { name: 'Pendiente', exact: true })).toBeVisible()
    await page.getByText('Historial de la solicitud', { exact: true }).click()
    await expect(page.locator('details ol li')).toHaveCount(4)
    await expect(page.getByRole('checkbox')).toHaveCount(0)
    await expect(page.locator('img')).toHaveCount(0)
  })
}

test('consulta final atendida muestra comprobación y conserva historial', async ({ page }) => {
  const e = await preparar(page, { solicitudes: [solicitud('procesando')] })
  await page.goto('/eliminar-datos')
  await expect(page.getByRole('heading', { name: 'Procesando', exact: true })).toBeVisible()
  e.solicitudes[0].estado = 'atendida'
  e.solicitudes[0].atendida_en = momento
  e.solicitudes[0].eventos.push(evento('atendida', 1))
  await page.getByRole('button', { name: 'Consultar estado' }).click()
  await expect(page.getByRole('heading', { name: 'Atendida', exact: true })).toBeVisible()
  await expect(page.getByText(/Eliminación comprobada:/)).toBeVisible()
  await expect(page.getByRole('checkbox')).not.toBeChecked()
})

test('conflicto concurrente muestra la solicitud existente sin crear otra', async ({ page }) => {
  const e = await preparar(page)
  await page.goto('/eliminar-datos')
  await page.getByRole('checkbox').check()
  e.conflicto = true
  e.solicitudes.push(solicitud())
  await page.getByRole('button', { name: 'Solicitar eliminación' }).click()
  await expect(page.getByText('Ya tienes una solicitud sin resolver; se conserva su historial.')).toBeVisible()
  await expect(page.locator('.solicitudes-eliminacion > li')).toHaveCount(1)
})

test('fallo consultando mantiene solicitud deshabilitada y permite recuperación', async ({ page }) => {
  const e = await preparar(page, { falloConsulta: true })
  await page.goto('/eliminar-datos')
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByRole('checkbox')).toHaveCount(0)
  e.falloConsulta = false
  await page.getByRole('button', { name: 'Consultar estado' }).click()
  await expect(page.getByRole('checkbox')).toBeVisible()
})

test('rechazo CSRF no muestra éxito ni solicitud inexistente', async ({ page }) => {
  const e = await preparar(page, { falloCsrf: true })
  await page.goto('/eliminar-datos')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Solicitar eliminación' }).click()
  await expect(page.getByRole('alert')).toContainText('CSRF')
  await expect(page.locator('.solicitudes-eliminacion > li')).toHaveCount(0)
  expect(e.solicitudes).toHaveLength(0)
})

test('barrera impide montar cámara incluso con consentimiento vigente', async ({ page }) => {
  await preparar(page, { bloqueoCaptura: true })
  await page.goto('/verificar-dni')
  await expect(page.getByRole('alert')).toContainText('captura está bloqueada')
  await expect(page.locator('input[type=file]')).toHaveCount(0)
  expect(await page.evaluate(() => window.camaras)).toBe(0)
})

test('rechazo backend de captura tardía retira fotos y enlaza eliminación', async ({ page }) => {
  await preparar(page, { rechazoCaptura: true })
  await page.goto('/verificar-dni')
  const foto = { name: 'ficticia.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6Fz0AAAAASUVORK5CYII=', 'base64') }
  await page.locator('input[type=file]').setInputFiles(foto)
  await page.locator('input[type=file]').setInputFiles(foto)
  await page.getByRole('button', { name: 'Enviar para validar' }).click()
  await expect(page.getByRole('alert')).toContainText('captura está bloqueada')
  await expect(page.locator('.vistas img')).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Consultar eliminación' })).toBeVisible()
})

test('teclado permite confirmar sin aceptación implícita', async ({ page }) => {
  await preparar(page)
  await page.goto('/eliminar-datos')
  const casilla = page.getByRole('checkbox')
  await casilla.focus()
  await casilla.press('Space')
  await expect(casilla).toBeChecked()
  const boton = page.getByRole('button', { name: 'Solicitar eliminación' })
  await boton.focus()
  await boton.press('Enter')
  await expect(page.getByRole('heading', { name: 'Pendiente', exact: true })).toBeVisible()
})

for (const rol of [null, 'cliente', 'administrador']) {
  test('eliminación privada rechaza ' + (rol || 'anónimo'), async ({ page }) => {
    const e = await preparar(page, { usuario: rol ? { id: 2, nombres: 'Ficticio', rol } : null })
    await page.goto('/eliminar-datos')
    await expect(page).not.toHaveURL(/\/eliminar-datos$/)
    expect(e.consultas).toBe(0)
  })
}

for (const width of [320, 390, 1280]) {
  test('eliminación responsive sin desbordamiento a ' + width + ' px', async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await preparar(page, { solicitudes: [solicitud('parcial')] })
    await page.goto('/eliminar-datos')
    await page.getByText('Historial de la solicitud', { exact: true }).click()
    await expect(page.getByRole('button', { name: 'Reintentar eliminación' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: test.info().outputPath('eliminacion-' + width + '.png'), fullPage: true })
  })
}
