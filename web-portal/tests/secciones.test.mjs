// Las secciones y el buscador de la web dan lo mismo que la app (RubrosTest.kt).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { coincide, populares, rubrosDe, SECCIONES, seccionDe } from '../src/nucleo/secciones.js'

const NOMBRES = ['Gasfitería', 'Electricidad', 'Carpintería', 'Pintura', 'Albañilería', 'Cerrajería', 'Limpieza',
  'Jardinería', 'Soldadura', 'Mecánica', 'Cámaras de seguridad', 'Aire acondicionado', 'Fumigación',
  'Redes e internet', 'Mudanzas', 'Vidriería', 'Electrodomésticos', 'Techos y drywall', 'Tapicería']
const RUBROS = NOMBRES.map((nombre, i) => ({ id: i + 1, nombre }))
const seccion = (clave) => SECCIONES.find((s) => s.clave === clave)

test('urgente trae lo que no puede esperar, en ese orden', () => {
  assert.deepEqual(rubrosDe(seccion('urgente'), RUBROS).map((r) => r.nombre), ['Gasfitería', 'Cerrajería', 'Electricidad', 'Vidriería'])
})

test('cada rubro conocido está en una sección y los nuevos van a Más servicios', () => {
  const enSecciones = new Set(SECCIONES.filter((s) => s.clave !== 'otros').flatMap((s) => rubrosDe(s, RUBROS).map((r) => r.nombre)))
  for (const nombre of NOMBRES.slice(0, -1)) assert.ok(enSecciones.has(nombre), nombre)
  assert.deepEqual(rubrosDe(seccion('otros'), RUBROS).map((r) => r.nombre), ['Tapicería'])
  assert.equal(seccionDe({ nombre: 'Aire acondicionado' }).clave, 'empresas')
  assert.equal(seccionDe({ nombre: 'Tapicería' }).clave, 'otros')
})

test('el buscador entiende cómo pide la gente', () => {
  const busca = (texto) => RUBROS.filter((r) => coincide(r, texto)).map((r) => r.nombre)
  assert.ok(busca('gasfitero').includes('Gasfitería'))
  assert.ok(busca('Plomero').includes('Gasfitería'))
  assert.deepEqual(busca('wifi'), ['Redes e internet'])
  assert.ok(busca('cámara').includes('Cámaras de seguridad'))
  assert.deepEqual(busca('x'), [])
  assert.deepEqual(busca('zzzz'), [])
  assert.equal(populares(RUBROS)[0].nombre, 'Gasfitería')
})
