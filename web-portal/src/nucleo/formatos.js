// Formatos compartidos por la búsqueda, el mapa y las tarjetas de prestadores.
export const LIMA = [-12.046374, -77.042793]

export const formatoDistancia = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 2 })
const formatoPromedio = new Intl.NumberFormat('es-PE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

// HU-12: promedio y total de calificaciones; sin datos válidos se mantiene «Sin calificaciones».
export function textoCalificacion(prestador) {
  const total = prestador.calificaciones_total
  const promedio = prestador.calificacion_promedio
  if (!Number.isInteger(total) || total < 1 || !Number.isFinite(promedio)) return 'Sin calificaciones'
  return `★ ${formatoPromedio.format(promedio)} · ${total} ${total === 1 ? 'calificación' : 'calificaciones'}`
}

// Prestadores que comparten exactamente la misma ubicación (un solo marcador los agrupa).
export function coincidentesCon(prestador, prestadores) {
  return prestadores.filter((otro) => otro.latitud === prestador.latitud && otro.longitud === prestador.longitud)
}

// Clave de Google Maps (web-portal/.env.local). Sin clave se usa el mapa de OpenStreetMap.
export const CLAVE_GOOGLE_MAPS = import.meta.env.VITE_GOOGLE_MAPS_API_KEY || ''
export const USA_GOOGLE_MAPS = Boolean(CLAVE_GOOGLE_MAPS)
