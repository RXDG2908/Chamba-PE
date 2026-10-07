import { useEffect, useRef, useState } from 'react'
import { CircleMarker, MapContainer, TileLayer, useMapEvents } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'

const LIMA = [-12.046374, -77.042793]
const formatoDistancia = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 2 })

function UbicacionCliente({ ubicacion, onElegir }) {
  const mapa = useMapEvents({
    click: ({ latlng }) => {
      // Normaliza la longitud cuando Leaflet muestra otra copia del mundo.
      const punto = latlng.wrap()
      if (punto.lat >= -90 && punto.lat <= 90) onElegir([punto.lat, punto.lng])
    },
  })

  useEffect(() => {
    if (ubicacion) mapa.setView(ubicacion, mapa.getZoom())
  }, [mapa, ubicacion])

  return ubicacion ? <CircleMarker center={ubicacion} radius={8} /> : null
}

function ResultadosPrestadores() {
  const [rubros, setRubros] = useState([])
  const [estadoRubros, setEstadoRubros] = useState('cargando')
  const [intentoRubros, setIntentoRubros] = useState(0)
  const [rubro, setRubro] = useState('')
  const [ubicacion, setUbicacion] = useState(null)
  const [coordenadas, setCoordenadas] = useState({ latitud: '', longitud: '' })
  const [prestadores, setPrestadores] = useState([])
  const [estado, setEstado] = useState('inicial')
  const [error, setError] = useState('')
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
      // Una petición cancelada nunca puede reemplazar una búsqueda más reciente.
      if (!controlador.signal.aborted && peticion.current === controlador) {
        setPrestadores(datos)
        setEstado(datos.length ? 'listo' : 'vacio')
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
          <MapContainer center={LIMA} zoom={13} className="mapa-busqueda" scrollWheelZoom={false}>
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <UbicacionCliente ubicacion={ubicacion} onElegir={elegirUbicacion} />
          </MapContainer>
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
        {estado === 'listo' && (
          <ul className="lista-prestadores">
            {prestadores.map((prestador, indice) => (
              <li key={prestador.id} className="tarjeta">
                <div className={`avatar ${['verde', 'azul', 'naranja'][indice % 3]}`} aria-hidden="true">
                  {prestador.nombres.charAt(0)}{prestador.apellidos.charAt(0)}
                </div>
                <div className="perfil">
                  <h4>{prestador.nombres} {prestador.apellidos}</h4>
                  <p className="rubros-tarjeta">{prestador.rubros.map((especialidad) => especialidad.nombre).join(' · ')}</p>
                </div>
                <dl className="detalles">
                  <div><dt>Distancia</dt><dd>{formatoDistancia.format(prestador.distancia_km)} km</dd></div>
                </dl>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}

export default ResultadosPrestadores
