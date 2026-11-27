import { createElement, useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import SolicitudServicio from '../../componentes/SolicitudServicio.jsx'
import MapaBusqueda from '../../componentes/mapa/MapaBusqueda.jsx'
import PanelUbicacion, { BarraUbicacion } from '../../componentes/UbicacionServicio.jsx'
import { formatoDistancia, formatoEntero, iniciales, textoCalificacion } from '../../nucleo/formatos.js'
import { leerMiDireccion, nombrarPunto, textoCorto } from '../../nucleo/direcciones.js'
import { iconoRubro } from '../../nucleo/iconos.js'
import { seccionDe } from '../../nucleo/secciones.js'
import { ArrowRight, Clock, List, Map as IconoMapa, MapPin, MousePointerClick, Search, Sparkles } from 'lucide-react'
import Cargando from '../../componentes/Cargando.jsx'
import '../../componentes/Secciones.css'
import './ResultadosPrestadores.css'

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

// Igual que la tarjeta «Recomendado para ti» de la app: motivo de la IA, llegada estimada y enlace.
function Recomendado({ prestador }) {
  return (
    <aside className="recomendado" aria-labelledby="titulo-recomendado">
      <p className="etiqueta-recomendado"><Sparkles size={16} aria-hidden="true" /> Recomendado para ti</p>
      <h4 id="titulo-recomendado">{prestador.nombres} {prestador.apellidos}</h4>
      <p className="motivo-recomendado">{prestador.motivo}</p>
      <div className="pie-recomendado">
        <p className="tiempo-recomendado">
          <Clock size={16} aria-hidden="true" /> Llegada estimada: <strong>≈ {prestador.tiempo_estimado_min} min</strong>
        </p>
        <a href={`#prestador-${prestador.id}`}>Ver y solicitar <ArrowRight size={16} aria-hidden="true" /></a>
      </div>
    </aside>
  )
}

const aPunto = (direccion) => (direccion ? [direccion.latitud, direccion.longitud] : null)

function ResultadosPrestadores({ usuario }) {
  const { state } = useLocation()
  const [rubros, setRubros] = useState([])
  const [estadoRubros, setEstadoRubros] = useState('cargando')
  const [intentoRubros, setIntentoRubros] = useState(0)
  // La portada puede llegar con un rubro elegido; se confirma al cargar la lista.
  const [rubro, setRubro] = useState(() => String(state?.rubro ?? ''))
  // Como en la app: la búsqueda empieza con «Mi dirección» si hay una guardada en este navegador.
  const [direccion, setDireccion] = useState(() => leerMiDireccion())
  const ubicacion = aPunto(direccion)
  const [coordenadas, setCoordenadas] = useState(() => {
    const punto = aPunto(direccion)
    return punto ? { latitud: punto[0].toFixed(6), longitud: punto[1].toFixed(6) } : { latitud: '', longitud: '' }
  })
  const [panelAbierto, setPanelAbierto] = useState(false)
  const [eligiendoEnMapa, setEligiendoEnMapa] = useState(false)
  const [vista, setVista] = useState('lista') // en pantallas angostas: lista | mapa
  const [prestadores, setPrestadores] = useState([])
  const [estado, setEstado] = useState('inicial')
  const [error, setError] = useState('')
  const [recomendado, setRecomendado] = useState(null)
  const peticion = useRef(null)
  const nombrando = useRef(null)
  const zonaMapa = useRef(null)
  const busquedaInicial = useRef(Boolean(state?.rubro) && Boolean(direccion))

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

  useEffect(() => () => {
    peticion.current?.abort()
    nombrando.current?.abort()
  }, [])

  function limpiarBusqueda() {
    peticion.current?.abort()
    setPrestadores([])
    setRecomendado(null)
    setEstado('inicial')
    setError('')
  }

  // Lugar elegido en el panel (escrito, GPS o «Mi dirección»): ya trae su nombre de calle.
  function elegirDireccion(nueva) {
    limpiarBusqueda()
    nombrando.current?.abort()
    setDireccion(nueva)
    setCoordenadas({ latitud: nueva.latitud.toFixed(6), longitud: nueva.longitud.toFixed(6) })
    setPanelAbierto(false)
    setEligiendoEnMapa(false)
  }

  // Clic en el mapa (o coordenadas a mano): se usa enseguida y se le pone nombre de calle cuando llega.
  function elegirUbicacion(punto) {
    limpiarBusqueda()
    nombrando.current?.abort()
    const [latitud, longitud] = punto
    setDireccion({ titulo: '', detalle: '', distrito: '', latitud, longitud })
    setCoordenadas({ latitud: latitud.toFixed(6), longitud: longitud.toFixed(6) })
    setEligiendoEnMapa(false)
    const controlador = new AbortController()
    nombrando.current = controlador
    nombrarPunto(punto, controlador.signal).then((nombrada) => {
      if (controlador.signal.aborted || !nombrada.titulo) return
      // Solo si la persona no eligió otro lugar mientras tanto.
      setDireccion((actual) => (actual?.latitud === latitud && actual?.longitud === longitud ? nombrada : actual))
    }).catch(() => {})
  }

  function editarCoordenada(evento) {
    limpiarBusqueda()
    nombrando.current?.abort()
    setDireccion(null)
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

  function elegirEnMapa() {
    setPanelAbierto(false)
    setEligiendoEnMapa(true)
    setVista('mapa')
    requestAnimationFrame(() => zonaMapa.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }))
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
    evento?.preventDefault()
    limpiarBusqueda()
    if (!rubro || !ubicacion) {
      setError(!rubro ? 'Elige un rubro para buscar.' : 'Elige dónde necesitas el servicio: escribe tu dirección, usa tu ubicación o márcala en el mapa.')
      setEstado('error')
      return
    }
    const controlador = new AbortController()
    peticion.current = controlador
    setEstado('cargando')
    setVista('lista')
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

  // Desde Inicio con un rubro y «Mi dirección» guardada: se busca sola, como en la app.
  useEffect(() => {
    if (busquedaInicial.current && estadoRubros === 'listo' && rubro) {
      busquedaInicial.current = false
      buscar()
    }
  })

  const rubroElegido = rubros.find((r) => String(r.id) === rubro)
  return (
    <main className="contenedor resultados">
      <div className="introduccion">
        <p className="etiqueta">PRESTADORES DE SERVICIOS</p>
        <h2><span className="icono-titulo" aria-hidden="true"><Search size={22} /></span>Encuentra una mano para tu hogar</h2>
        <p>Dinos dónde y qué necesitas: te mostramos a los prestadores verificados cerca de ti.</p>
      </div>

      <form className="busqueda" onSubmit={buscar} noValidate>
        <div className="busqueda-fila">
          <BarraUbicacion direccion={direccion} onAbrir={() => setPanelAbierto(true)} />
          <label className="campo-busqueda campo-rubro" htmlFor="rubro-busqueda">
            <span>Rubro</span>
            <select id="rubro-busqueda" value={rubro} disabled={estadoRubros !== 'listo'} onChange={(evento) => {
              limpiarBusqueda()
              setRubro(evento.target.value)
            }}>
              <option value="">Elige un rubro</option>
              {rubros.map((opcion) => <option key={opcion.id} value={opcion.id}>{opcion.nombre}</option>)}
            </select>
          </label>
          <button className="boton-buscar" type="submit" disabled={estadoRubros !== 'listo'}>
            <Search className="icono" size={18} aria-hidden="true" />
            {estado === 'cargando' ? 'Buscar de nuevo' : 'Buscar'}
          </button>
        </div>

        {estadoRubros === 'listo' && (
          <ul className="chips-rubros" aria-label="Elegir rubro">
            {rubros.map((opcion) => {
              const elegido = String(opcion.id) === rubro
              return (
                <li key={opcion.id}>
                  <button type="button" aria-pressed={elegido}
                    className={`chip-rubro seccion-${seccionDe(opcion).clave}${elegido ? ' elegido' : ''}`}
                    onClick={() => { limpiarBusqueda(); setRubro(String(opcion.id)) }}>
                    <span className="chip-rubro-icono" aria-hidden="true">{createElement(iconoRubro(opcion.nombre), { size: 16 })}</span>
                    {opcion.nombre}
                  </button>
                </li>
              )
            })}
          </ul>
        )}
        {estadoRubros === 'cargando' && <Cargando texto="Cargando rubros…" />}
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
        <p className="ubicacion-elegida" role="status">
          {ubicacion ? `Ubicación elegida: ${textoCorto(direccion)}.` : 'Todavía no elegiste una ubicación.'}
        </p>
      </form>

      <PanelUbicacion abierto={panelAbierto} actual={direccion} onElegir={elegirDireccion}
        onCerrar={() => setPanelAbierto(false)} onElegirEnMapa={elegirEnMapa} />

      {/* En pantallas angostas, con resultados: Lista o Mapa, como en la app (en PC se ven juntos). */}
      {estado === 'listo' && (
        <div className="selector-vista" role="group" aria-label="Ver resultados como">
          <button type="button" aria-pressed={vista === 'lista'} onClick={() => setVista('lista')}><List size={18} aria-hidden="true" />Lista</button>
          <button type="button" aria-pressed={vista === 'mapa'} onClick={() => setVista('mapa')}><IconoMapa size={18} aria-hidden="true" />Mapa</button>
        </div>
      )}

      <div className={`resultados-rejilla vista-${estado === 'listo' || eligiendoEnMapa ? vista : 'ambas'}`}>
        <section className="columna-lista" aria-labelledby="titulo-resultados" aria-busy={estado === 'cargando'}>
          <div className="resumen">
            <h3 id="titulo-resultados">Lista de prestadores</h3>
            {estado === 'listo' && <span>{formatoEntero.format(prestadores.length)} {prestadores.length === 1 ? 'prestador' : 'prestadores'}</span>}
          </div>
          <div aria-live="polite" role="status">
            {estado === 'inicial' && (
              <p className="aviso">
                {!ubicacion ? 'Primero dinos dónde necesitas el servicio.' : !rubro ? 'Elige un rubro y pulsa Buscar.' : `Pulsa Buscar para ver quién atiende ${rubroElegido?.nombre ?? ''} cerca.`}
              </p>
            )}
            {estado === 'cargando' && <p className="aviso">Buscando prestadores disponibles…</p>}
            {estado === 'vacio' && <p className="aviso">No encontramos prestadores disponibles de ese rubro con cobertura en tu ubicación.</p>}
            {estado === 'listo' && <p className="resultado-anuncio">Búsqueda completada: {formatoEntero.format(prestadores.length)} {prestadores.length === 1 ? 'prestador encontrado' : 'prestadores encontrados'}.</p>}
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
                    {iniciales(prestador.nombres, prestador.apellidos)}
                  </div>
                  <div className="perfil">
                    <h4>{prestador.nombres} {prestador.apellidos}</h4>
                    {/* La insignia va fuera del título: el título es solo el nombre (lectores de pantalla y pruebas). */}
                    {recomendado?.id === prestador.id && <p className="insignia-recomendado">✨ Recomendado</p>}
                    <p className="rubros-tarjeta">
                      {createElement(iconoRubro(rubroElegido?.nombre ?? prestador.rubros[0]?.nombre ?? ''), { size: 15, className: 'icono', 'aria-hidden': true })}
                      {prestador.rubros.map((especialidad) => especialidad.nombre).join(' · ')}
                    </p>
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
                    usuario={usuario}
                  />
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className={`columna-mapa${eligiendoEnMapa ? ' eligiendo' : ''}`} ref={zonaMapa} aria-label="Mapa">
          {eligiendoEnMapa && (
            <p className="aviso-elegir-mapa" role="status">
              <MousePointerClick size={18} aria-hidden="true" />Haz clic en el mapa donde necesitas el servicio.
            </p>
          )}
          <div role="region" aria-label="Mapa de prestadores y ubicación">
            <MapaBusqueda ubicacion={ubicacion} prestadores={prestadores} onElegir={elegirUbicacion} />
          </div>
          <p className="leyenda-mapa">● Azul: dónde necesitas el servicio · Verdes numerados: prestadores (centro de su zona). El signo + indica varios en el mismo punto.</p>
          <details className="coordenadas-avanzado">
            <summary><MapPin size={16} aria-hidden="true" />Ingresar coordenadas (avanzado)</summary>
            <div className="coordenadas-busqueda">
              <label className="campo-busqueda">
                <span>Latitud</span>
                <input name="latitud" type="number" step="any" min="-90" max="90" value={coordenadas.latitud} onChange={editarCoordenada} />
              </label>
              <label className="campo-busqueda">
                <span>Longitud</span>
                <input name="longitud" type="number" step="any" min="-180" max="180" value={coordenadas.longitud} onChange={editarCoordenada} />
              </label>
              <button type="button" onClick={usarCoordenadas}><MapPin className="icono" size={18} aria-hidden="true" />Usar coordenadas</button>
            </div>
          </details>
        </section>
      </div>
    </main>
  )
}

export default ResultadosPrestadores
