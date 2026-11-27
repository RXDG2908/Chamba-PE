import { createElement, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Search, SearchX, X } from 'lucide-react'
import { iconoRubro } from '../nucleo/iconos.js'
import { coincide, populares, rubrosDe, SECCIONES, seccionDe } from '../nucleo/secciones.js'
import { useRubros } from '../nucleo/useRubros.js'
import './Secciones.css'

/** El ícono de un rubro (lucide); se crea con createElement para no definir componentes al dibujar. */
function IconoDe({ nombre, ...props }) {
  return createElement(iconoRubro(nombre), props)
}

/** Ficha de un rubro con el color de su sección; lleva a los resultados con ese rubro elegido. */
export function FichaRubro({ rubro, seccion = seccionDe(rubro) }) {
  return (
    <Link to="/resultados" state={{ rubro: rubro.id }} className={`ficha-rubro seccion-${seccion.clave}`}>
      <span className="ficha-rubro-icono" aria-hidden="true"><IconoDe nombre={rubro.nombre} size={26} /></span>
      <span className="ficha-rubro-nombre">{rubro.nombre}</span>
    </Link>
  )
}

/**
 * Lo mismo que el Inicio de la app (patrón hub): un buscador que entiende «gasfitero» o «wifi», una
 * tarjeta grande por sección y «Lo más pedido». Cada tarjeta abre su sección en /seccion/:clave.
 */
export default function ExplorarServicios() {
  const rubros = useRubros()
  const [texto, setTexto] = useState('')
  const encontrados = useMemo(
    () => (texto.trim() && rubros ? rubros.filter((r) => coincide(r, texto)) : []),
    [rubros, texto],
  )
  if (rubros === null || rubros.length === 0) return null
  const secciones = SECCIONES.filter((s) => rubrosDe(s, rubros).length > 0)
  const masPedidos = populares(rubros)

  return (
    <section className="contenedor seccion explorar" aria-labelledby="titulo-explorar">
      <h3 id="titulo-explorar" className="seccion-titulo">¿Qué necesitas hoy?</h3>
      <label className="buscador-servicios">
        <Search className="icono" size={20} aria-hidden="true" />
        <span className="visualmente-oculto">Buscar un servicio</span>
        <input
          type="search" value={texto} maxLength={40} autoComplete="off"
          placeholder="Busca: gasfitero, pintor, wifi…" onChange={(e) => setTexto(e.target.value)}
        />
        {texto && (
          <button type="button" className="buscador-borrar" onClick={() => setTexto('')} aria-label="Borrar búsqueda">
            <X size={18} aria-hidden="true" />
          </button>
        )}
      </label>

      {texto.trim() ? (
        encontrados.length === 0 ? (
          <p className="sin-coincidencias" role="status">
            <SearchX className="icono" size={20} aria-hidden="true" />
            No encontramos «{texto.trim()}». Prueba con otra palabra o entra a una sección.
          </p>
        ) : (
          <ul className="resultados-rubros" aria-live="polite">
            {encontrados.map((rubro) => {
              const seccion = seccionDe(rubro)
              return (
                <li key={rubro.id}>
                  <Link to="/resultados" state={{ rubro: rubro.id }} className={`resultado-rubro seccion-${seccion.clave}`}>
                    <span className="ficha-rubro-icono" aria-hidden="true"><IconoDe nombre={rubro.nombre} size={22} /></span>
                    <span><strong>{rubro.nombre}</strong><small>En {seccion.titulo}</small></span>
                    <ArrowRight className="icono" size={18} aria-hidden="true" />
                  </Link>
                </li>
              )
            })}
          </ul>
        )
      ) : (
        <>
          <ul className="tarjetas-secciones">
            {secciones.map((seccion, indice) => {
              const propios = rubrosDe(seccion, rubros)
              const { Icono } = seccion
              // Primera y cuarta, anchas: dos filas parejas de 3 columnas (y la última sola, también ancha).
              const ancha = indice === 0 || indice === 3 || (indice === secciones.length - 1 && indice % 2 === 0)
              return (
                <li key={seccion.clave} className={`tarjeta-seccion seccion-${seccion.clave}${ancha ? ' ancha' : ''}`}>
                  <Link to={`/seccion/${seccion.clave}`} aria-label={`${seccion.titulo}: ${seccion.descripcion}. ${propios.length} servicios.`}>
                    <Icono className="tarjeta-seccion-fondo" size={150} aria-hidden="true" />
                    <span className="tarjeta-seccion-titulo"><Icono size={22} aria-hidden="true" />{seccion.titulo}</span>
                    <span className="tarjeta-seccion-descripcion">{seccion.descripcion}</span>
                    <span className="tarjeta-seccion-pie">
                      <span className="tarjeta-seccion-fichas" aria-hidden="true">
                        {propios.slice(0, 4).map((r) => <span key={r.id}><IconoDe nombre={r.nombre} size={18} /></span>)}
                      </span>
                      <span className="tarjeta-seccion-cuantos">
                        {propios.length} {propios.length === 1 ? 'servicio' : 'servicios'}
                        <ArrowRight size={16} aria-hidden="true" />
                      </span>
                    </span>
                  </Link>
                </li>
              )
            })}
          </ul>
          {masPedidos.length > 0 && (
            <>
              <h4 className="subtitulo-explorar">Lo más pedido</h4>
              <ul className="fichas-rubros">
                {masPedidos.map((rubro) => <li key={rubro.id}><FichaRubro rubro={rubro} /></li>)}
              </ul>
            </>
          )}
        </>
      )}
    </section>
  )
}
