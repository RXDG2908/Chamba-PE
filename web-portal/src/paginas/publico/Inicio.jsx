import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './Inicio.css'
import { ArrowRight, MapPinned, Search, Send, ShieldCheck, Store, ToggleRight, UserRound, Upload } from 'lucide-react'
import { iconoRubro } from '../../nucleo/iconos.js'

// Botones de la portada según quién entra; coinciden con el menú de App.jsx.
const ACCIONES_POR_ROL = {
  visitante: [['/resultados', 'Buscar un servicio'], ['/registro-prestador', 'Quiero ofrecer mis servicios']],
  cliente: [['/resultados', 'Buscar un servicio'], ['/mis-servicios', 'Mis servicios']],
  prestador: [['/prestador', 'Ir a mi panel'], ['/certificaciones', 'Cargar un certificado']],
  administrador: [['/administracion', 'Ir al panel']],
}

const PASOS = [
  ['Elige el servicio', 'Gasfitería, electricidad, limpieza y más. Marca en el mapa dónde lo necesitas.'],
  ['Compara prestadores cercanos', 'Solo ves a quienes están disponibles, cubren tu zona y fueron verificados.'],
  ['Envía tu solicitud', 'El prestador la recibe en su bandeja y te responde si acepta el trabajo.'],
]
const ICONOS_PASOS = [Search, MapPinned, Send]
const ICONOS_PASOS_PRESTADOR = [UserRound, Upload, ToggleRight]

const PASOS_PRESTADOR = [
  ['Regístrate', 'Tus datos, tus rubros y la zona donde trabajas.'],
  ['Sube tu certificado', 'Administración lo revisa y te marca como verificado.'],
  ['Activa tu disponibilidad', 'Apareces en las búsquedas y recibes solicitudes.'],
]

function Inicio({ usuario }) {
  const [rubros, setRubros] = useState([])
  const rol = usuario?.rol ?? 'visitante'
  const acciones = usuario === undefined ? [] : ACCIONES_POR_ROL[rol] ?? []

  useEffect(() => {
    const controlador = new AbortController()
    fetch('/api/prestadores/rubros/', { signal: controlador.signal })
      .then((r) => (r.ok ? r.json() : []))
      .then((datos) => setRubros(Array.isArray(datos) ? datos : []))
      .catch(() => {})
    return () => controlador.abort()
  }, [])

  return (
    <main className="inicio">
      <section className="portada">
        <div className="contenedor portada-rejilla">
          <div className="portada-texto">
            <p className="insignia"><ShieldCheck className="icono" size={16} aria-hidden="true" />Prestadores verificados por administración</p>
            <h1>
              {usuario ? <>Hola, {usuario.nombres || usuario.username}.<br /></> : null}
              Encuentra quién te ayude, <span>cerca de ti</span>.
            </h1>
            <p className="portada-descripcion">
              ChambaPE conecta a vecinos que necesitan un servicio con prestadores de su zona: disponibles,
              verificados y a pocos kilómetros.
            </p>
            {acciones.length > 0 && (
              <div className="portada-acciones">
                {acciones.map(([ruta, texto], indice) => (
                  <Link key={ruta} to={ruta} className={indice === 0 ? 'boton-principal' : 'boton-contorno'}>
                    {texto}
                    {indice === 0 && <ArrowRight className="icono icono-flecha" size={18} aria-hidden="true" />}
                  </Link>
                ))}
              </div>
            )}
          </div>

          {/* Ilustración de una búsqueda; no son datos reales. */}
          <div className="portada-ilustracion" aria-hidden="true">
            <div className="ilustracion-mapa">
              <span className="punto punto-cliente" />
              <span className="punto punto-a">1</span>
              <span className="punto punto-b">2</span>
              <span className="punto punto-c">3</span>
              <span className="radio" />
            </div>
            <div className="ilustracion-tarjeta">
              <span className="ilustracion-avatar">G</span>
              <div>
                <strong>Gasfitería</strong>
                <small>Verificado · disponible</small>
              </div>
              <span className="ilustracion-distancia">1,2 km</span>
            </div>
          </div>
        </div>
      </section>

      <section className="contenedor seccion">
        <p className="etiqueta">CÓMO FUNCIONA</p>
        <h3 className="seccion-titulo">Tu servicio en tres pasos</h3>
        <ol className="pasos">
          {PASOS.map(([titulo, texto], indice) => {
            const Icono = ICONOS_PASOS[indice]
            return (
            <li key={titulo} className="paso">
              <span className="paso-icono" aria-hidden="true"><Icono size={22} /></span>
              <span className="visualmente-oculto">Paso {indice + 1}: </span>
              <h4>{titulo}</h4>
              <p>{texto}</p>
            </li>
            )
          })}
        </ol>
      </section>

      {rubros.length > 0 && (
        <section className="contenedor seccion">
          <p className="etiqueta">RUBROS</p>
          <h3 className="seccion-titulo">¿Qué necesitas hoy?</h3>
          <ul className="rubros-inicio">
            {rubros.map((rubro) => (
              <li key={rubro.id}>
                <Link to="/resultados" state={{ rubro: rubro.id }}>
                  {(() => { const Icono = iconoRubro(rubro.nombre); return <Icono className="icono" size={18} aria-hidden="true" /> })()}
                  {rubro.nombre}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {rol === 'visitante' && usuario !== undefined && (
        <section className="contenedor seccion">
          <div className="banda-prestador">
            <div>
              <p className="etiqueta">PARA PRESTADORES</p>
              <h3 className="seccion-titulo">¿Ofreces un servicio? Consigue clientes en tu zona</h3>
              <ol className="pasos-prestador">
                {PASOS_PRESTADOR.map(([titulo, texto], indice) => {
                  const Icono = ICONOS_PASOS_PRESTADOR[indice]
                  return <li key={titulo}><Icono className="icono" size={16} aria-hidden="true" /> <strong>{titulo}.</strong> {texto}</li>
                })}
              </ol>
            </div>
            <Link to="/registro-prestador" className="boton-principal">
              <Store className="icono" size={18} aria-hidden="true" />Registrarme como prestador
            </Link>
          </div>
        </section>
      )}
    </main>
  )
}

export default Inicio
