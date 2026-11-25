import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { CircleMarker, MapContainer, Marker, Popup, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import { divIcon, DomEvent } from 'leaflet'
import 'leaflet/dist/leaflet.css'
import SolicitudServicio from './SolicitudServicio.jsx'

const LIMA = [-12.046374, -77.042793]
const formatoDistancia = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 2 })
const formatoPromedio = new Intl.NumberFormat('es-PE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

// HU-12: promedio y total de calificaciones; sin datos válidos se mantiene «Sin calificaciones».
function textoCalificacion(prestador) {
  const total = prestador.calificaciones_total
  const promedio = prestador.calificacion_promedio
  if (!Number.isInteger(total) || total < 1 || !Number.isFinite(promedio)) return 'Sin calificaciones'
  return `★ ${formatoPromedio.format(promedio)} · ${total} ${total === 1 ? 'calificación' : 'calificaciones'}`
}

// Validar el contrato antes de renderizar tarjetas y marcadores con los mismos datos.
function prestadorValido(prestador) {
  return prestador && Number.isSafeInteger(prestador.id) && prestador.id > 0
    && typeof prestador.nombres === 'string' && typeof prestador.apellidos === 'string'
    && Number.isFinite(prestador.distancia_km) && prestador.distancia_km >= 0
    && Array.isArray(prestador.rubros) && prestador.rubros.every((rubro) =>
      rubro && Number.isSafeInteger(rubro.id) && rubro.id > 0 && typeof rubro.nombre === 'string')
}

// HU-10: la recomendación solo se muestra si es coherente con los resultados de la búsqueda.
function recomendacionValida(datos, prestadores) {
  const r = datos?.recomendado
  return prestadorValido(r) && typeof r.motivo === 'string' && r.motivo.length > 0
    && Number.isInteger(r.tiempo_estimado_min) && r.tiempo_estimado_min >= 0
    && prestadores.some((p) => p.id === r.id)
}

function Recomendado({ prestador }) {
  return (
    <aside className="recomendado" aria-labelledby="titulo-recomendado">
      <p className="etiqueta-recomendado">✨ Recomendado para ti</p>
      <h4 id="titulo-recomendado">{prestador.nombres} {prestador.apellidos}</h4>
      <p className="motivo-recomendado">{prestador.motivo}</p>
      <p className="tiempo-recomendado">
        Llegada estimada: <strong>≈ {prestador.tiempo_estimado_min} min</strong>
      </p>
      <a href={`#prestador-${prestador.id}`}>Ver y solicitar</a>
    </aside>
  )
}

function UbicacionCliente({ ubicacion, onElegir }) {
  useMapEvents({
    click: ({ latlng, originalEvent }) => {
      if (originalEvent?.target?.closest('.leaflet-popup, .leaflet-interactive, .leaflet-marker-icon')) return
      // Normaliza la longitud cuando Leaflet muestra otra copia del mundo.
      const punto = latlng.wrap()
      if (punto.lat >= -90 && punto.lat <= 90) onElegir([punto.lat, punto.lng])
    },
  })

  return ubicacion ? (
    <CircleMarker center={ubicacion} radius={9} bubblingMouseEvents={false}
      pathOptions={{ color: '#fff', weight: 3, fillColor: '#2463a6', fillOpacity: 1 }}>
      <Popup>Tu ubicación para el servicio</Popup>
    </CircleMarker>
  ) : null
}

function VistaResultados({ ubicacion, prestadores }) {
  const mapa = useMap()

  useEffect(() => {
    if (!ubicacion) return
    const puntos = [ubicacion, ...prestadores.map((p) => [p.latitud, p.longitud])]
    // Aplicar cada resultado de inmediato: Leaflet ignora nuevos zooms mientras anima otro.
    mapa.fitBounds(puntos, { padding: [32, 32], maxZoom: 15, animate: false })
  }, [mapa, ubicacion, prestadores])

  return ubicacion && (
    <div className="volver-mapa" ref={(nodo) => {
      if (nodo) DomEvent.disableClickPropagation(nodo)
    }}>
      <button type="button" onClick={() => mapa.fitBounds(
        [ubicacion, ...prestadores.map((p) => [p.latitud, p.longitud])],
        { padding: [32, 32], maxZoom: 15, animate: false },
      )}>Ver ubicación y resultados</button>
    </div>
  )
}

function MarcadoresPrestadores({ prestadores }) {
  return prestadores.map((prestador, indice) => {
    // No desplazamos las coordenadas: la ventana permite consultar a todos
    // los prestadores superpuestos, incluido un punto compartido con el cliente.
    const coincidentes = prestadores.filter((otro) => otro.latitud === prestador.latitud
      && otro.longitud === prestador.longitud)
    const titulo = coincidentes.length > 1
      ? `${coincidentes.length} prestadores en esta ubicación`
      : `${prestador.nombres} ${prestador.apellidos}`
    return (
      <Marker key={prestador.id} position={[prestador.latitud, prestador.longitud]}
        bubblingMouseEvents={false} title={titulo} alt={titulo}
        icon={divIcon({
          className: 'marcador-prestador',
          html: `<span>${indice + 1}${coincidentes.length > 1 ? '+' : ''}</span>`,
          iconSize: [36, 36], iconAnchor: [18, 36], popupAnchor: [0, -36],
        })}>
        <Popup maxWidth={240} maxHeight={200}>
          <div className="contenido-marcador">
            {coincidentes.length > 1 && <p><strong>{titulo}</strong></p>}
            {coincidentes.map((persona) => (
              <article key={persona.id}>
                <strong>{persona.nombres} {persona.apellidos}</strong>
                <p>{persona.rubros.map((r) => r.nombre).join(' · ')}</p>
                <p>{formatoDistancia.format(persona.distancia_km)} km</p>
                <p>{textoCalificacion(persona)}</p>
              </article>
            ))}
          </div>
        </Popup>
      </Marker>
    )
  })
}

function ResultadosPrestadores() {
  const { state } = useLocation()
  const [rubros, setRubros] = useState([])
  const [estadoRubros, setEstadoRubros] = useState('cargando')
  const [intentoRubros, setIntentoRubros] = useState(0)
  // La portada puede llegar con un rubro elegido; se confirma al cargar la lista.
  const [rubro, setRubro] = useState(() => String(state?.rubro ?? ''))
  const [ubicacion, setUbicacion] = useState(null)
  const [coordenadas, setCoordenadas] = useState({ latitud: '', longitud: '' })
  const [prestadores, setPrestadores] = useState([])
  const [estado, setEstado] = useState('inicial')
  const [error, setError] = useState('')
  const [recomendado, setRecomendado] = useState(null)
  const peticion = useRef(null)

  useEffect(() => {
    const controlador = new AbortController()
    async function cargarRubros() {
      try {
        const respuesta = await fetch('/api/prestadores/rubros/', { signal: controlador.signal })
        if (!respuesta.ok) throw new Error('No se pudieron cargar los rubros.')
        const datos = await respuesta.json()
        if (!Array.isArray(datos)) throw new Error('Respuesta inválida.')
        if (!controlador.signal.aborted) {
          setRubros(datos)
          setRubro((actual) => (datos.some((r) => String(r.id) === actual) ? actual : ''))
          setEstadoRubros(datos.length ? 'listo' : 'vacio')
        }
      } catch {
        if (!controlador.signal.aborted) setEstadoRubros('error')
      }
    }
    cargarRubros()
    return () => controlador.abort()
  }, [intentoRubros])

  useEffect(() => () => peticion.current?.abort(), [])

  function limpiarBusqueda() {
    peticion.current?.abort()
    setPrestadores([])
    setRecomendado(null)
    setEstado('inicial')
    setError('')
  }

  function elegirUbicacion(punto) {
    limpiarBusqueda()
    setUbicacion(punto)
    setCoordenadas({ latitud: punto[0].toFixed(6), longitud: punto[1].toFixed(6) })
  }

  function editarCoordenada(evento) {
    limpiarBusqueda()
    setUbicacion(null)
    setCoordenadas({ ...coordenadas, [evento.target.name]: evento.target.value })
  }

  function usarCoordenadas() {
    limpiarBusqueda()
    const latitud = Number(coordenadas.latitud)
    const longitud = Number(coordenadas.longitud)
    if (!coordenadas.latitud.trim() || !coordenadas.longitud.trim()
      || !Number.isFinite(latitud) || !Number.isFinite(longitud)
      || Math.abs(latitud) > 90 || Math.abs(longitud) > 180) {
      setError('Ingresa latitud entre -90 y 90 y longitud entre -180 y 180, usando punto decimal.')
      setEstado('error')
      return
    }
    elegirUbicacion([latitud, longitud])
  }

  // HU-10: si la recomendación falla, la búsqueda se muestra igual, solo sin el recuadro.
  async function cargarRecomendacion(parametros, controlador, datos) {
    try {
      const respuesta = await fetch(`/api/ia/recomendacion/?${parametros}`, { signal: controlador.signal })
      const cuerpo = respuesta.ok ? await respuesta.json() : null
      if (!controlador.signal.aborted && peticion.current === controlador && recomendacionValida(cuerpo, datos)) {
        setRecomendado(cuerpo.recomendado)
      }
    } catch {
      // Sin recomendación: la lista de la búsqueda sigue siendo útil.
    }
  }

  async function buscar(evento) {
    evento.preventDefault()
    limpiarBusqueda()
    if (!rubro || !ubicacion) {
      setError(!rubro ? 'Elige un rubro para buscar.' : 'Elige tu ubicación en el mapa o confirma las coordenadas.')
      setEstado('error')
      return
    }
    const controlador = new AbortController()
    peticion.current = controlador
    setEstado('cargando')
    const parametros = new URLSearchParams({
      rubro, latitud: ubicacion[0], longitud: ubicacion[1],
    })
    try {
      const respuesta = await fetch(`/api/prestadores/buscar/?${parametros}`, { signal: controlador.signal })
      const datos = await respuesta.json()
      if (!respuesta.ok) {
        const mensajes = Object.values(datos).flat().filter((mensaje) => typeof mensaje === 'string')
        throw new Error(mensajes.join(' ') || 'No se pudo realizar la búsqueda. Inténtalo de nuevo.')
      }
      if (!Array.isArray(datos)) throw new Error('El servidor devolvió una respuesta inesperada. Inténtalo de nuevo.')
      if (datos.some((p) => !Number.isFinite(p?.latitud) || Math.abs(p.latitud) > 90
        || !Number.isFinite(p?.longitud) || Math.abs(p.longitud) > 180)) {
        throw new Error('El servidor devolvió coordenadas inválidas. Inténtalo de nuevo.')
      }
      if (!datos.every(prestadorValido) || new Set(datos.map((p) => p.id)).size !== datos.length) {
        throw new Error('El servidor devolvió datos de prestadores inválidos. Inténtalo de nuevo.')
      }
      // Una petición cancelada nunca puede reemplazar una búsqueda más reciente.
      if (!controlador.signal.aborted && peticion.current === controlador) {
        setPrestadores(datos)
        setEstado(datos.length ? 'listo' : 'vacio')
        if (datos.length) cargarRecomendacion(parametros, controlador, datos)
      }
    } catch (fallo) {
      if (!controlador.signal.aborted && peticion.current === controlador) {
        setError(fallo instanceof TypeError || fallo instanceof SyntaxError
          ? 'No pudimos comunicarnos con el servidor. Inténtalo de nuevo.' : fallo.message)
        setEstado('error')
      }
    }
  }

  return (
    <main className="contenedor resultados">
      <div className="introduccion">
        <p className="etiqueta">PRESTADORES DE SERVICIOS</p>
        <h2>Encuentra una mano para tu hogar</h2>
        <p>Elige un servicio y tu ubicación para encontrar prestadores disponibles.</p>
      </div>

      <form className="busqueda" onSubmit={buscar} noValidate>
        <label className="campo-busqueda" htmlFor="rubro-busqueda">
          <span>Rubro</span>
          <select id="rubro-busqueda" value={rubro} disabled={estadoRubros !== 'listo'} onChange={(evento) => {
            limpiarBusqueda()
            setRubro(evento.target.value)
          }}>
            <option value="">Elige un rubro</option>
            {rubros.map((opcion) => <option key={opcion.id} value={opcion.id}>{opcion.nombre}</option>)}
          </select>
        </label>
        {estadoRubros === 'cargando' && <p role="status">Cargando rubros…</p>}
        {estadoRubros === 'vacio' && <p role="status">No hay rubros disponibles para buscar.</p>}
        {estadoRubros === 'error' && (
          <div className="error-busqueda" role="alert">
            <p>No pudimos cargar los rubros. Comprueba la conexión e inténtalo de nuevo.</p>
            <button type="button" onClick={() => {
              setEstadoRubros('cargando')
              setIntentoRubros((actual) => actual + 1)
            }}>Reintentar carga de rubros</button>
          </div>
        )}

        <fieldset>
          <legend>Tu ubicación</legend>
          <p id="ayuda-ubicacion">Toca el mapa para elegir dónde necesitas el servicio o introduce tus coordenadas.</p>
          <div role="region" aria-label="Mapa de prestadores y ubicación">
          <MapContainer center={LIMA} zoom={13} className="mapa-busqueda" scrollWheelZoom={false}>
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <UbicacionCliente ubicacion={ubicacion} onElegir={elegirUbicacion} />
            <MarcadoresPrestadores prestadores={prestadores} />
            <VistaResultados ubicacion={ubicacion} prestadores={prestadores} />
          </MapContainer>
          </div>
          <p className="leyenda-mapa">● Azul: tu ubicación · Marcadores verdes numerados: prestadores (centro de cobertura). El signo + indica varios en el mismo punto; abre la ventana para verlos todos.</p>
          <div className="coordenadas-busqueda">
            <label className="campo-busqueda">
              <span>Latitud</span>
              <input name="latitud" type="number" step="any" min="-90" max="90" value={coordenadas.latitud} onChange={editarCoordenada} aria-describedby="ayuda-ubicacion" />
            </label>
            <label className="campo-busqueda">
              <span>Longitud</span>
              <input name="longitud" type="number" step="any" min="-180" max="180" value={coordenadas.longitud} onChange={editarCoordenada} aria-describedby="ayuda-ubicacion" />
            </label>
            <button type="button" onClick={usarCoordenadas}>Usar coordenadas</button>
          </div>
          <p className="ubicacion-elegida" role="status">
            {ubicacion ? `Ubicación elegida: ${ubicacion[0].toFixed(6)}, ${ubicacion[1].toFixed(6)}.` : 'Todavía no elegiste una ubicación.'}
          </p>
        </fieldset>
        <button className="boton-buscar" type="submit" disabled={estadoRubros !== 'listo'}>
          {estado === 'cargando' ? 'Buscar de nuevo' : 'Buscar'}
        </button>
      </form>

      <section aria-labelledby="titulo-resultados" aria-busy={estado === 'cargando'}>
        <div className="resumen">
          <h3 id="titulo-resultados">Lista de prestadores</h3>
          {estado === 'listo' && <span>{prestadores.length} {prestadores.length === 1 ? 'prestador' : 'prestadores'}</span>}
        </div>
        <div aria-live="polite" role="status">
          {estado === 'inicial' && <p className="aviso">Elige un rubro y tu ubicación, y pulsa Buscar.</p>}
          {estado === 'cargando' && <p className="aviso">Buscando prestadores disponibles…</p>}
          {estado === 'vacio' && <p className="aviso">No encontramos prestadores disponibles de ese rubro con cobertura en tu ubicación.</p>}
          {estado === 'listo' && <p className="resultado-anuncio">Búsqueda completada: {prestadores.length} {prestadores.length === 1 ? 'prestador encontrado' : 'prestadores encontrados'}.</p>}
        </div>
        {estado === 'error' && <p className="error-busqueda" role="alert">{error}</p>}
        {estado === 'listo' && recomendado && <Recomendado prestador={recomendado} />}
        {estado === 'listo' && (
          <ul className="lista-prestadores">
            {prestadores.map((prestador, indice) => (
              <li key={prestador.id} id={`prestador-${prestador.id}`}
                className={`tarjeta${recomendado?.id === prestador.id ? ' tarjeta-recomendada' : ''}`}>
                <div className="cabecera-prestador">
                <div className={`avatar ${['verde', 'azul', 'naranja'][indice % 3]}`} aria-hidden="true">
                  {prestador.nombres.charAt(0)}{prestador.apellidos.charAt(0)}
                </div>
                <div className="perfil">
                  <h4>
                    {prestador.nombres} {prestador.apellidos}
                    {recomendado?.id === prestador.id && <span className="insignia-recomendado"> ✨ Recomendado</span>}
                  </h4>
                  <p className="rubros-tarjeta">{prestador.rubros.map((especialidad) => especialidad.nombre).join(' · ')}</p>
                  <p className="rubros-tarjeta">{textoCalificacion(prestador)}</p>
                </div>
                <dl className="detalles">
                  <div><dt>Distancia</dt><dd>{formatoDistancia.format(prestador.distancia_km)} km</dd></div>
                </dl>
                </div>
                <SolicitudServicio
                  prestador={prestador}
                  rubro={rubro}
                  ubicacion={ubicacion}
                />
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}

export default ResultadosPrestadores
