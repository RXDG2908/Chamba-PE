import { test, expect } from '@playwright/test'

const solicitud = (estado = 'pendiente') => ({ id: 41, rubro: 'Gasfitería',
  descripcion: 'Fuga ficticia', cliente_nombre: 'Cliente ficticio',
  cliente_telefono: '900000000', estado })

async function preparar(page, { rol = 'prestador', consultar, responder } = {}) {
  await page.context().route('**/api/**', async (route) => {
    const { pathname } = new URL(route.request().url())
    if (pathname.endsWith('/csrf/')) return route.fulfill({ json: {} })
    if (pathname.endsWith('/yo/')) return route.fulfill(rol
      ? { json: { id: 7, rol, nombres: 'Prueba', username: 'prueba' } }
      : { status: 403, json: {} })
    if (pathname.includes('/solicitudes/recibidas/')) {
      if (consultar) return consultar(route, pathname)
      return route.fulfill({ json: pathname.endsWith('/41/') ? solicitud() : [solicitud()] })
    }
    if (/\/(aceptar|rechazar)\/$/.test(pathname)) return responder
      ? responder(route) : route.fulfill({ json: solicitud('aceptada') })
    return route.fulfill({ status: 404, json: {} })
  })
}

for (const ruta of ['/solicitudes', '/solicitudes/41']) {
  test('409 recarga estado sin acciones obsoletas en ' + ruta, async ({ page }) => {
    let consultadas = 0
    await preparar(page, {
      consultar: (r, p) => {
        const dato = solicitud(++consultadas === 1 ? 'pendiente' : 'rechazada')
        return r.fulfill({ json: p.endsWith('/41/') ? dato : [dato] })
      },
      responder: (r) => r.fulfill({ status: 409, json: { estado: 'Ya respondida' } }),
    })
    await page.goto(ruta)
    await page.getByRole('button', { name: 'Aceptar', exact: true }).click()
    await expect(page.getByText('Estado: rechazada', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: /^(Aceptar|Rechazar)$/ })).toHaveCount(0)
    expect(consultadas).toBe(2)
    await expect(page).toHaveURL(new RegExp(ruta + '$'))
  })

  test('409 y error de recarga no restauran botones; reintento en ' + ruta, async ({ page }) => {
    let consultas = 0
    let respuestas = 0
    await preparar(page, {
      consultar: (r, p) => {
        consultas++
        if (consultas === 2) return r.abort()
        const dato = solicitud(consultas === 1 ? 'pendiente' : 'aceptada')
        return r.fulfill({ json: p.endsWith('/41/') ? dato : [dato] })
      },
      responder: (r) => { respuestas++; return r.fulfill({ status: 409, json: {} }) },
    })
    await page.goto(ruta)
    await page.getByRole('button', { name: 'Rechazar', exact: true }).click()
    await expect(page.getByRole('alert')).toBeVisible()
    await expect(page.getByRole('button', { name: /^(Aceptar|Rechazar)$/ })).toHaveCount(0)
    await page.getByRole('button', { name: 'Reintentar consulta' }).click()
    await expect(page.getByText('Estado: aceptada', { exact: true })).toBeVisible()
    expect(respuestas).toBe(1)
  })
}

for (const accion of ['Aceptar', 'Rechazar']) {
  test(accion + ' actualiza el detalle y retira acciones', async ({ page }) => {
    await preparar(page, { responder: (r) => r.fulfill({ json: solicitud(accion === 'Aceptar' ? 'aceptada' : 'rechazada') }) })
    await page.goto('/solicitudes/41')
    await page.getByRole('button', { name: accion, exact: true }).click()
    await expect(page.getByText('Respuesta guardada.', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: /^(Aceptar|Rechazar)$/ })).toHaveCount(0)
  })
}

test('sesión vencida al responder vuelve al login', async ({ page }) => {
  await preparar(page, { responder: (r) => r.fulfill({ status: 403, json: {} }) })
  await page.goto('/solicitudes/41')
  await page.getByRole('button', { name: 'Aceptar', exact: true }).click()
  await expect(page).toHaveURL(/\/ingresar$/)
  await expect(page.getByRole('button', { name: 'Aceptar', exact: true })).toHaveCount(0)
})

for (const rol of ['cliente', 'administrador', null]) {
  test('ruta privada no expone solicitudes a ' + rol, async ({ page }) => {
    let consultas = 0
    await preparar(page, { rol, consultar: (r) => { consultas++; return r.fulfill({ json: [] }) } })
    await page.goto('/solicitudes/41')
    await expect(page).toHaveURL(rol ? /\/$/ : /\/ingresar$/)
    expect(consultas).toBe(0)
    await expect(page.getByRole('link', { name: 'Solicitudes', exact: true })).toHaveCount(0)
  })
}

test('menú conserva disponibilidad y certificados y añade solicitudes al prestador', async ({ page }) => {
  await preparar(page)
  await page.goto('/solicitudes')
  for (const name of ['Mi disponibilidad', 'Cargar certificado', 'Solicitudes']) {
    await expect(page.getByRole('navigation').getByRole('link', { name, exact: true })).toBeVisible()
  }
})
