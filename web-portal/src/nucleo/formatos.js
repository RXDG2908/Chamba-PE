// Formatos compartidos por la búsqueda, el mapa y las tarjetas de prestadores.
export const LIMA = [-12.046374, -77.042793]

export const formatoDistancia = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 2 })
const formatoPromedio = new Intl.NumberFormat('es-PE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
/** Cantidades con separador de miles: 1234567 → «1,234,567». */
export const formatoEntero = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 0 })

// Grafemas (lo que la persona ve como un carácter): un emoji con tono de piel o una letra con
// diacríticos combinados son varias unidades UTF-16, y charAt(0) los partía («�»).
const grafemas = typeof Intl.Segmenter === 'function' ? new Intl.Segmenter('es', { granularity: 'grapheme' }) : null

function primeraLetra(texto = '') {
  const partes = grafemas ? Array.from(grafemas.segment(String(texto)), (s) => s.segment) : Array.from(String(texto))
  const letra = partes.find((p) => /\p{L}/u.test(p)) ?? partes.find((p) => p.trim()) ?? ''
  // Sin marcas combinadas apiladas («Z̷̢» → «Z»); «Ñ» o «Ç» se conservan porque en NFC son un solo carácter.
  return letra.normalize('NFC').replace(/\p{M}+/gu, '').toLocaleUpperCase('es')
}

/** Iniciales para el avatar: primera letra real del nombre y del apellido («👷🏽‍♀️ Rosa», «Quispe» → «RQ»). */
export function iniciales(nombres, apellidos) {
  return (primeraLetra(nombres) + primeraLetra(apellidos)) || '?'
}

// HU-12: promedio y total de calificaciones; sin datos válidos se mantiene «Sin calificaciones».
export function textoCalificacion(prestador) {
  const total = prestador.calificaciones_total
  const promedio = prestador.calificacion_promedio
  if (!Number.isInteger(total) || total < 1 || !Number.isFinite(promedio)) return 'Sin calificaciones'
  return `★ ${formatoPromedio.format(promedio)} · ${formatoEntero.format(total)} ${total === 1 ? 'calificación' : 'calificaciones'}`
}

// Prestadores que comparten exactamente la misma ubicación (un solo marcador los agrupa).
export function coincidentesCon(prestador, prestadores) {
  return prestadores.filter((otro) => otro.latitud === prestador.latitud && otro.longitud === prestador.longitud)
}

// Clave de Google Maps (web-portal/.env.local). Sin clave se usa el mapa de OpenStreetMap.
export const CLAVE_GOOGLE_MAPS = import.meta.env.VITE_GOOGLE_MAPS_API_KEY || ''
export const USA_GOOGLE_MAPS = Boolean(CLAVE_GOOGLE_MAPS)
