import { test, expect } from '@playwright/test'

// API simulada: estas pruebas cubren la ventana del chat; las reglas del servidor están en
// servicios/test_chat.py (Django) y ChatApiTest.java (Spring).
const PRESTADOR = { id: 5, username: 'ana', nombres: 'Ana', rol: 'prestador' }
const CLIENTE = { id: 7, username: 'carlos', nombres: 'Carlos', rol: 'cliente' }
const NOMBRES = { pendiente: 'Pendiente', aceptada: 'Confirmado', en_camino: 'En camino',
  en_servicio: 'En servicio', finalizada: 'Finalizada', rechazada: 'Rechazada' }

function recibida(estado) {
  return {
    id: 41, rubro: 'Gasfitería', descripcion: 'Fuga ficticia', cliente_nombre: 'Carlos Cliente',
    cliente_telefono: '900000000', latitud: '0', longitud: '0', estado, estado_display: NOMBRES[estado],
    siguiente_estado: null, historial: [], creada: '2026-11-28T14:00:00Z', respondida: '2026-11-28T15:00:00Z',
  }
}

function servicio(estado) {
  return {
    id: 41, prestador: { id: 3, nombre: 'Ana Pérez' }, rubro: 'Gasfitería', descripcion: 'Fuga ficticia',
    estado, estado_display: NOMBRES[estado], creada: '2026-11-28T14:00:00Z', historial: [],
    calificacion: null, puede_calificar: estado === 'finalizada',
  }
}

async function preparar(page, { usuario = CLIENTE, estado = 'aceptada', telefono = '987654321' } = {}) {
  const yo = usuario.rol
  const datos = { estado, enviados: [], consultas: 0, mensajes: [
    { id: 1, de: yo === 'cliente' ? 'prestador' : 'cliente', texto: 'Hola, ya vi tu solicitud.', enviado: '2026-11-28T15:01:00Z' },
  ] }
  await page.context().addCookies([{ name: 'csrftoken', value: 'token-ficticio', url: 'http://127.0.0.1:5195' }])
  await page.context().route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/usuarios/csrf/') return json({})
    if (path === '/api/usuarios/yo/') return json(usuario)
    if (path === '/api/servicios/solicitudes/recibidas/') return json([recibida(datos.estado)])
    if (path === '/api/calificaciones/mis-servicios/') return json([servicio(datos.estado)])
    if (path === '/api/servicios/solicitudes/41/chat/') {
      const activo = datos.estado !== 'finalizada'
      if (request.method() === 'POST') {
        if (!activo) return json({ chat: 'El servicio ya finalizó; el chat queda solo para leer.' }, 409)
        const { texto } = request.postDataJSON()
        datos.enviados.push({ texto, csrf: request.headers()['x-csrftoken'] })
        const mensaje = { id: datos.mensajes.length + 1, de: yo, texto, enviado: '2026-11-28T15:05:00Z' }
        datos.mensajes.push(mensaje)
        return json(mensaje, 201)
      }
      datos.consultas++
      return json({
        solicitud: 41, estado: datos.estado, estado_display: NOMBRES[datos.estado], activo, yo,
        contraparte: yo === 'cliente'
          ? { nombre: 'Ana Pérez', telefono: activo ? telefono : null }
          : { nombre: 'Carlos Cliente', telefono: '900000000' },
        mensajes: datos.mensajes,
      })
    }
    return json({}, 404)
  })
  return datos
}

test('el cliente abre el chat desde Mis servicios, escribe y ve el teléfono del prestador', async ({ page }) => {
  const datos = await preparar(page)
  await page.goto('/mis-servicios')
  await page.getByRole('button', { name: 'Chat', exact: true }).click()
  const chat = page.getByRole('region', { name: 'Chat con Ana Pérez' })
  await expect(chat.getByText('Hola, ya vi tu solicitud.')).toBeVisible()
  await expect(chat.getByText('Solicitud #41 · Confirmado')).toBeVisible()

  await chat.getByRole('textbox', { name: 'Mensaje' }).fill('¿A qué hora llega?')
  await chat.getByRole('button', { name: 'Enviar' }).click()
  await expect(chat.getByText('¿A qué hora llega?')).toBeVisible()
  await expect(chat.getByRole('textbox', { name: 'Mensaje' })).toHaveValue('')
  expect(datos.enviados).toEqual([{ texto: '¿A qué hora llega?', csrf: 'token-ficticio' }])

  // En la PC no hay marcador: se ve el número para copiarlo, sin «Llamar».
  await chat.getByRole('button', { name: 'Teléfono de Ana Pérez' }).click()
  await expect(chat.getByText('987654321')).toBeVisible()
  await expect(chat.getByRole('button', { name: 'Copiar' })).toBeVisible()
  await expect(chat.getByRole('link', { name: 'Llamar' })).toHaveCount(0)
  await page.screenshot({ path: '../.chat-local/cliente.png' })
})

test('se actualiza solo cada 5 segundos con los mensajes nuevos', async ({ page }) => {
  const datos = await preparar(page)
  await page.goto('/mis-servicios')
  await page.getByRole('button', { name: 'Chat', exact: true }).click()
  const chat = page.getByRole('region', { name: 'Chat con Ana Pérez' })
  await expect(chat.getByText('Hola, ya vi tu solicitud.')).toBeVisible()
  datos.mensajes.push({ id: 9, de: 'prestador', texto: 'Llego en 20 minutos.', enviado: '2026-11-28T15:10:00Z' })
  await expect(chat.getByText('Llego en 20 minutos.')).toBeVisible({ timeout: 8000 })
})

test('el prestador abre el chat desde su bandeja, lo minimiza y lo cierra', async ({ page }) => {
  await preparar(page, { usuario: PRESTADOR, estado: 'en_camino' })
  await page.goto('/solicitudes')
  await page.getByRole('button', { name: 'Chat', exact: true }).click()
  const chat = page.getByRole('region', { name: 'Chat con Carlos Cliente' })
  await expect(chat.getByText('Hola, ya vi tu solicitud.')).toBeVisible()
  await chat.getByRole('button', { name: 'Teléfono de Carlos Cliente' }).click()
  await expect(chat.getByText('900000000')).toBeVisible()
  await page.screenshot({ path: '../.chat-local/prestador.png' })

  await chat.getByRole('button', { name: 'Minimizar chat' }).click()
  await expect(chat.getByRole('textbox', { name: 'Mensaje' })).toHaveCount(0)
  await chat.getByRole('button', { name: 'Abrir chat' }).click()
  await expect(chat.getByRole('textbox', { name: 'Mensaje' })).toBeVisible()
  await chat.getByRole('button', { name: 'Cerrar chat' }).click()
  await expect(chat).toHaveCount(0)
})

test('finalizado: el chat queda solo para leer y sin teléfono del prestador', async ({ page }) => {
  await preparar(page, { estado: 'finalizada' })
  await page.goto('/mis-servicios')
  await page.getByRole('button', { name: 'Chat', exact: true }).click()
  const chat = page.getByRole('region', { name: 'Chat con Ana Pérez' })
  await expect(chat.getByText('El servicio finalizó; el chat queda solo para leer.')).toBeVisible()
  await expect(chat.getByRole('textbox', { name: 'Mensaje' })).toHaveCount(0)
  await expect(chat.getByRole('button', { name: /Teléfono/ })).toHaveCount(0)
})

test('pendiente o rechazada no muestran el botón de chat', async ({ page }) => {
  for (const estado of ['pendiente', 'rechazada']) {
    await preparar(page, { estado })
    await page.goto('/mis-servicios')
    await expect(page.getByText(NOMBRES[estado]).first()).toBeVisible()
    await expect(page.getByRole('button', { name: 'Chat', exact: true })).toHaveCount(0)
    await page.context().unrouteAll({ behavior: 'ignoreErrors' })
  }
})

test('en el celular la ventana ocupa el ancho de la pantalla', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 800 })
  await preparar(page)
  await page.goto('/mis-servicios')
  await page.getByRole('button', { name: 'Chat', exact: true }).click()
  const chat = page.getByRole('region', { name: 'Chat con Ana Pérez' })
  await expect(chat.getByText('Hola, ya vi tu solicitud.')).toBeVisible()
  const caja = await chat.boundingBox()
  expect(caja.x).toBe(0)
  expect(caja.width).toBe(390)
  await page.screenshot({ path: '../.chat-local/cliente-movil.png' })
})

test.describe('en un celular (pantalla táctil)', () => {
  test.use({ viewport: { width: 390, height: 800 }, hasTouch: true, isMobile: true })

  test('«Llamar» abre el marcador con el número del prestador', async ({ page }) => {
    await preparar(page)
    await page.goto('/mis-servicios')
    await page.getByRole('button', { name: 'Chat', exact: true }).click()
    const chat = page.getByRole('region', { name: 'Chat con Ana Pérez' })
    await chat.getByRole('button', { name: 'Teléfono de Ana Pérez' }).click()
    await expect(chat.getByRole('link', { name: 'Llamar' })).toHaveAttribute('href', 'tel:987654321')
    await expect(chat.getByRole('button', { name: 'Copiar' })).toBeVisible()
    await page.screenshot({ path: '../.chat-local/cliente-movil-telefono.png' })
  })
})
