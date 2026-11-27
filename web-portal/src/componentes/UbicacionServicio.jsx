import { useEffect, useRef, useState } from 'react'
import { ChevronDown, Crosshair, House, LoaderCircle, Map as IconoMapa, MapPin, MapPinOff, Search, Star, X } from 'lucide-react'
import { buscarDirecciones, guardarMiDireccion, leerMiDireccion, nombrarPunto, textoCorto } from '../nucleo/direcciones.js'
import './UbicacionServicio.css'

/**
 * La ubicación arriba, como la dirección de entrega de una app de delivery (y como la app de
 * Chamba): «Servicio en Av. Arequipa 1200, Lince ▾». Un clic abre el panel para cambiarla.
 */
export function BarraUbicacion({ direccion, onAbrir }) {
  const texto = direccion ? textoCorto(direccion) : 'Elige dónde lo necesitas'
  return (
    <button type="button" className="barra-ubicacion" onClick={onAbrir}
      aria-label={`Ubicación del servicio: ${texto}. Cambiar ubicación`}>
      <MapPin className="barra-ubicacion-pin" size={22} aria-hidden="true" />
      <span className="barra-ubicacion-textos">
        <small>Servicio en</small>
        <strong className={direccion ? '' : 'sin-direccion'}>{texto}</strong>
      </span>
      <ChevronDown size={20} aria-hidden="true" />
    </button>
  )
}

/**
 * Panel para elegir el lugar (search-ux + navigation-patterns): escribir la calle o el distrito
 * con sugerencias, usar la ubicación del navegador, «Mi dirección» o marcar en el mapa. Es un
 * <dialog> nativo: atrapa el foco, Esc lo cierra y el lector de pantalla lo anuncia.
 */
export default function PanelUbicacion({ abierto, actual, onElegir, onCerrar, onElegirEnMapa }) {
  const dialogo = useRef(null)
  const entrada = useRef(null)
  const [texto, setTexto] = useState('')
  const [estado, setEstado] = useState('inicial') // inicial | buscando | lista | vacio | error
  const [sugerencias, setSugerencias] = useState([])
  const [ubicando, setUbicando] = useState(false)
  const [sinGps, setSinGps] = useState('')
  // «Mi dirección» se lee al dibujar (es localStorage); guardar solo vuelve a dibujar.
  const [, setGuardadas] = useState(0)
  const miDireccion = leerMiDireccion()
  const corto = texto.trim().length < 3
  const vista = corto ? 'inicial' : estado

  useEffect(() => {
    const nodo = dialogo.current
    if (!nodo) return
    if (abierto && !nodo.open) {
      nodo.showModal()
      entrada.current?.focus() // se escribe enseguida, sin buscar el campo
    }
    else if (!abierto && nodo.open) nodo.close()
  }, [abierto])

  // Al cerrarse (Esc, la X, un clic fuera o al elegir) se deja limpio para la próxima vez.
  function alCerrar() {
    setTexto('')
    setEstado('inicial')
    setSinGps('')
    onCerrar()
  }

  // Espera a que la persona deje de escribir (no se consulta por cada letra).
  useEffect(() => {
    if (corto) return undefined
    const controlador = new AbortController()
    const espera = setTimeout(async () => {
      setEstado('buscando')
      try {
        const lista = await buscarDirecciones(texto, controlador.signal)
        if (!controlador.signal.aborted) {
          setSugerencias(lista)
          setEstado(lista.length ? 'lista' : 'vacio')
        }
      } catch {
        if (!controlador.signal.aborted) setEstado('error')
      }
    }, 450)
    return () => {
      clearTimeout(espera)
      controlador.abort()
    }
  }, [texto, corto])

  function usarMiUbicacion() {
    if (!('geolocation' in navigator)) {
      setSinGps('Este navegador no comparte tu ubicación. Escribe tu dirección o márcala en el mapa.')
      return
    }
    setUbicando(true)
    setSinGps('')
    navigator.geolocation.getCurrentPosition(async ({ coords }) => {
      const direccion = await nombrarPunto([coords.latitude, coords.longitude])
      setUbicando(false)
      onElegir(direccion)
    }, () => {
      setUbicando(false)
      setSinGps('No pudimos obtener tu ubicación. Escribe tu dirección o márcala en el mapa.')
    }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 })
  }

  const guardarComoMia = () => {
    guardarMiDireccion(actual)
    setGuardadas((n) => n + 1)
  }
  const esMia = actual && miDireccion && actual.latitud === miDireccion.latitud && actual.longitud === miDireccion.longitud

  return (
    <dialog ref={dialogo} className="panel-ubicacion" aria-labelledby="titulo-panel-ubicacion" onClose={alCerrar}
      onClick={(e) => { if (e.target === dialogo.current) dialogo.current.close() }}>
      <div className="panel-ubicacion-contenido">
        <div className="panel-ubicacion-cabecera">
          <h3 id="titulo-panel-ubicacion">¿Dónde necesitas el servicio?</h3>
          <button type="button" className="panel-cerrar" onClick={() => dialogo.current?.close()} aria-label="Cerrar"><X size={20} aria-hidden="true" /></button>
        </div>

        <label className="buscador-direccion">
          <Search size={20} aria-hidden="true" />
          <span className="visualmente-oculto">Calle y número, o distrito</span>
          <input type="search" value={texto} onChange={(e) => setTexto(e.target.value.slice(0, 120))}
            placeholder="Calle y número, o distrito" autoComplete="street-address" ref={entrada} />
        </label>
        <p className="ayuda-direccion">Por ejemplo: Av. Arequipa 1200, Lince</p>

        <ul className="opciones-ubicacion" aria-live="polite">
          {vista === 'buscando' && <li className="opcion-estado"><LoaderCircle className="girando" size={18} aria-hidden="true" />Buscando direcciones…</li>}
          {vista === 'lista' && sugerencias.map((d) => (
            <li key={`${d.latitud},${d.longitud},${d.titulo}`}>
              <button type="button" className="opcion-ubicacion" onClick={() => onElegir(d)}>
                <MapPin size={20} aria-hidden="true" />
                <span><strong>{d.titulo || 'Punto sin nombre'}</strong>{d.detalle && <small>{d.detalle}</small>}</span>
              </button>
            </li>
          ))}
          {vista === 'vacio' && (
            <li>
              <button type="button" className="opcion-ubicacion" onClick={onElegirEnMapa}>
                <MapPinOff size={20} aria-hidden="true" />
                <span><strong>No encontramos «{texto.trim()}»</strong><small>Prueba con la calle y el distrito, o márcala en el mapa.</small></span>
              </button>
            </li>
          )}
          {vista === 'error' && <li className="opcion-estado">No pudimos buscar direcciones ahora. Usa tu ubicación o márcala en el mapa.</li>}
        </ul>

        <ul className="opciones-ubicacion opciones-fijas">
          <li>
            <button type="button" className="opcion-ubicacion destacada" onClick={usarMiUbicacion} disabled={ubicando}>
              {ubicando ? <LoaderCircle className="girando" size={20} aria-hidden="true" /> : <Crosshair size={20} aria-hidden="true" />}
              <span><strong>{ubicando ? 'Ubicando…' : 'Usar mi ubicación actual'}</strong>
                <small className={sinGps ? 'error' : ''}>{sinGps || 'Con la ubicación de tu navegador'}</small></span>
            </button>
          </li>
          {miDireccion && (
            <li>
              <button type="button" className="opcion-ubicacion" onClick={() => onElegir(miDireccion)}>
                <House size={20} aria-hidden="true" />
                <span><strong>Mi dirección</strong><small>{textoCorto(miDireccion)}</small></span>
              </button>
            </li>
          )}
          <li>
            <button type="button" className="opcion-ubicacion" onClick={onElegirEnMapa}>
              <IconoMapa size={20} aria-hidden="true" />
              <span><strong>Elegir en el mapa</strong><small>Haz clic en el mapa, en tu puerta</small></span>
            </button>
          </li>
        </ul>

        {actual && !esMia && (
          <button type="button" className="guardar-mi-direccion" onClick={guardarComoMia}>
            <Star size={16} aria-hidden="true" />Guardar «{textoCorto(actual)}» como mi dirección
          </button>
        )}
        {actual && esMia && (
          <p className="es-mi-direccion"><Star size={16} aria-hidden="true" />Es tu dirección guardada: la búsqueda empieza con ella.</p>
        )}
      </div>
    </dialog>
  )
}
