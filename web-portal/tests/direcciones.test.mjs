// Direcciones como en la app: calle y distrito, sin coordenadas a la vista.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { aDireccion, textoCorto } from '../src/nucleo/direcciones.js'

test('un lugar de Nominatim se lee como calle y distrito', () => {
  const d = aDireccion({
    lat: '-12.0445', lon: '-76.9525',
    address: { road: 'Avenida Los Cascanueces', house_number: '2221', neighbourhood: 'Asoc Nuevo Monterrey', suburb: 'Santa Anita', city: 'Lima' },
  })
  assert.equal(d.titulo, 'Avenida Los Cascanueces 2221')
  assert.equal(d.distrito, 'Santa Anita')
  assert.equal(textoCorto(d), 'Avenida Los Cascanueces 2221, Santa Anita')
  assert.equal(d.latitud, -12.0445)
})

test('sin calle usa el nombre o el distrito, y sin coordenadas no hay dirección', () => {
  assert.equal(textoCorto(aDireccion({ lat: '-12.1', lon: '-77', name: 'Parque Kennedy', address: { suburb: 'Miraflores' } })), 'Parque Kennedy, Miraflores')
  assert.equal(textoCorto(aDireccion({ lat: '-12.1', lon: '-77', address: { suburb: 'Lince' } })), 'Lince')
  assert.equal(aDireccion({ lat: 'x', lon: '-77' }), null)
  assert.equal(textoCorto({ titulo: '', latitud: -12, longitud: -77 }), 'Punto marcado en el mapa')
  assert.equal(textoCorto(null), '')
})
