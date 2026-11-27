// Ubicación como dirección, igual que la app (device/Direcciones.kt): la persona escribe una calle
// o un distrito, usa su ubicación o marca el mapa; nunca tiene que saber coordenadas.
//
// En la web se usa Nominatim (OpenStreetMap): gratis y sin clave. Su política pide pocas consultas
// (se espera a que la persona deje de escribir) y se limita al Perú. Para producción con mucho
// tráfico conviene un servicio propio o de pago; la interfaz no cambia.

const NOMINATIM = 'https://nominatim.openstreetmap.org'
const CLAVE_MI_DIRECCION = 'chamba.miDireccion'

/** «Av. Arequipa 1200, Lince»: la calle y el distrito, para la barra de ubicación. */
export function textoCorto(direccion) {
  if (!direccion) return ''
  if (!direccion.titulo) return 'Punto marcado en el mapa'
  const zona = direccion.distrito || direccion.detalle?.split(',')[0]?.trim()
  return zona && zona !== direccion.titulo ? `${direccion.titulo}, ${zona}` : direccion.titulo
}

/** Un resultado de Nominatim como dirección (sin coordenadas a la vista). */
export function aDireccion(lugar) {
  const latitud = Number(lugar?.lat)
  const longitud = Number(lugar?.lon)
  if (!Number.isFinite(latitud) || !Number.isFinite(longitud)) return null
  const a = lugar.address ?? {}
  const calle = [a.road ?? a.pedestrian ?? a.footway, a.house_number].filter(Boolean).join(' ')
  const distrito = a.city_district ?? a.suburb ?? a.town ?? a.city ?? a.village ?? ''
  const titulo = calle || lugar.name || a.neighbourhood || distrito || ''
  const detalle = [a.neighbourhood, distrito, a.city ?? a.state]
    .filter((parte, i, todas) => parte && parte !== titulo && todas.indexOf(parte) === i).join(', ')
  return { titulo, detalle, distrito, latitud, longitud }
}

async function pedir(ruta, parametros, signal) {
  const respuesta = await fetch(`${NOMINATIM}/${ruta}?${new URLSearchParams({
    format: 'jsonv2', addressdetails: '1', 'accept-language': 'es', ...parametros,
  })}`, { signal, headers: { Accept: 'application/json' } })
  if (!respuesta.ok) throw new Error('Sin servicio de direcciones')
  return respuesta.json()
}

/** Lugares del Perú para lo que se escribió (calle y número, o distrito). */
export async function buscarDirecciones(texto, signal) {
  const consulta = texto.trim()
  if (consulta.length < 3) return []
  const lugares = await pedir('search', { q: consulta, countrycodes: 'pe', limit: '6' }, signal)
  const vistas = new Set()
  return (Array.isArray(lugares) ? lugares : []).map(aDireccion).filter((d) => {
    if (!d) return false
    const llave = `${d.titulo}|${d.detalle}`
    if (vistas.has(llave)) return false
    vistas.add(llave)
    return true
  })
}

/** El nombre de calle de un punto (GPS o mapa); conserva el punto exacto. Sin nombre: título vacío. */
export async function nombrarPunto([latitud, longitud], signal) {
  try {
    const lugar = await pedir('reverse', { lat: String(latitud), lon: String(longitud), zoom: '18' }, signal)
    const direccion = aDireccion(lugar)
    if (direccion) return { ...direccion, latitud, longitud }
  } catch (error) {
    if (error?.name === 'AbortError') throw error
  }
  return { titulo: '', detalle: '', distrito: '', latitud, longitud }
}

/** «Mi dirección» en este navegador (como en Perfil de la app); se borra al cerrar sesión. */
export function leerMiDireccion() {
  try {
    const guardada = JSON.parse(localStorage.getItem(CLAVE_MI_DIRECCION) ?? 'null')
    return guardada && Number.isFinite(guardada.latitud) && Number.isFinite(guardada.longitud) ? guardada : null
  } catch {
    return null
  }
}

export function guardarMiDireccion(direccion) {
  try {
    if (direccion) localStorage.setItem(CLAVE_MI_DIRECCION, JSON.stringify(direccion))
    else localStorage.removeItem(CLAVE_MI_DIRECCION)
  } catch {
    // Sin almacenamiento (modo privado): la búsqueda funciona igual, solo no se recuerda.
  }
}
