import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { enviarJson } from '../../nucleo/api.js'
import { activarNotificaciones, desactivarNotificaciones } from '../../nucleo/notificaciones.js'
import Seguimiento from '../../componentes/Seguimiento.jsx'
import { ACCION_SIGUIENTE } from '../../nucleo/etapasSeguimiento.js'
import '../../estilos/Paneles.css'
import './SolicitudesRecibidas.css'
import { Bell, BellOff, Check, Inbox, RotateCcw, X } from 'lucide-react'
import Cargando from '../../componentes/Cargando.jsx'
import IconoEstado from '../../componentes/IconoEstado.jsx'

export default function SolicitudesRecibidas({ usuario, alVencer }) {
  const { id } = useParams()
  const clave = id || ''
  const [vista, setVista] = useState(null)
  const [mensaje, setMensaje] = useState('')
  const [errorCarga, setErrorCarga] = useState(null)
  const [revision, setRevision] = useState(0)
  const [ocupado, setOcupado] = useState(false)
  const solicitudes = vista?.clave === clave ? vista.solicitudes : null
  const errorActual = errorCarga?.clave === clave ? errorCarga.mensaje : ''

  useEffect(() => {
    const abort = new AbortController()
    const ruta = '/api/servicios/solicitudes/recibidas/' + (id ? `${id}/` : '')
    fetch(ruta, { signal: abort.signal, cache: 'no-store' }).then(async (r) => {
      if (r.status === 403) { if (!abort.signal.aborted) alVencer(null); return }
      if (!r.ok) throw new Error('No se pudo consultar la solicitud o no te pertenece.')
      const datos = await r.json()
      if (!abort.signal.aborted) {
        setVista({ clave, solicitudes: id ? [datos] : datos })
        setErrorCarga(null)
      }
    }).catch((e) => {
      if (!abort.signal.aborted) setErrorCarga({ clave, mensaje: e.message })
    })
    return () => abort.abort()
  }, [id, clave, alVencer, revision])

  function recargar() {
    setVista(null)
    setErrorCarga(null)
    setRevision((r) => r + 1)
  }
  async function responder(solicitud, accion, cuerpo) {
    setOcupado(true)
    try {
      const r = await enviarJson(`/api/servicios/solicitudes/${solicitud.id}/${accion}/`, 'POST', cuerpo)
      if (r.status === 403) { alVencer(null); return }
      if (r.status === 409) {
        setMensaje(accion === 'avanzar'
          ? 'La solicitud cambió de estado en otra pestaña. Consultando su estado actualizado.'
          : 'La solicitud ya fue respondida. Consultando su estado actualizado.')
        // Retirar inmediatamente las acciones obsoletas, también si la recarga falla.
        recargar()
        return
      }
      if (!r.ok) throw new Error(accion === 'avanzar'
        ? 'No se pudo actualizar el estado del servicio.' : 'No se pudo responder la solicitud.')
      const dato = await r.json()
      setVista((anterior) => anterior?.clave === clave
        ? { ...anterior, solicitudes: anterior.solicitudes.map((s) => s.id === dato.id ? dato : s) }
        : anterior)
      setMensaje(accion === 'avanzar' ? `Estado actualizado: ${dato.estado_display || dato.estado}.` : 'Respuesta guardada.')
    } catch (e) { setMensaje(e.message) } finally { setOcupado(false) }
  }
  async function configurar(activar) {
    setOcupado(true)
    try {
      if (activar) await activarNotificaciones(usuario)
      else await desactivarNotificaciones()
      setMensaje(activar ? 'Notificaciones activadas en este navegador.' : 'Notificaciones desactivadas.')
    } catch (e) { setMensaje(e.message) } finally { setOcupado(false) }
  }
  return <main className="bandeja contenedor">
    <header className="panel-cabecera">
      <p className="etiqueta">Trabajo</p>
      <h2><span className="icono-titulo" aria-hidden="true"><Inbox size={22} /></span>Solicitudes recibidas</h2>
      <p>Responde rápido: el cliente ve tu respuesta y el avance del servicio al momento.</p>
    </header>
    <div className="bandeja-notificaciones">
      <span>Avisos en este navegador</span>
      <button type="button" className="boton-secundario" disabled={ocupado} onClick={() => configurar(true)}><Bell className="icono" size={18} aria-hidden="true" />Activar notificaciones</button>
      <button type="button" className="boton-texto" disabled={ocupado} onClick={() => configurar(false)}><BellOff className="icono" size={18} aria-hidden="true" />Desactivar notificaciones</button>
    </div>
    <p role="status" className="bandeja-mensaje">{mensaje}</p>
    {errorActual && <div role="alert" className="error-busqueda"><p>{errorActual}</p>
      <button type="button" className="boton-secundario" onClick={recargar}><RotateCcw className="icono" size={18} aria-hidden="true" />Reintentar consulta</button></div>}
    {solicitudes === null && !errorActual && <Cargando className="aviso" texto="Cargando solicitudes…" anunciar={false} />}
    {solicitudes?.length === 0 && <p className="aviso">Aún no tienes solicitudes. Mantén tu disponibilidad activa para recibirlas.</p>}
    <div className="bandeja-lista">
      {solicitudes?.map((s) => <article key={s.id} className={`solicitud-tarjeta estado-${s.estado}`}>
        <header className="solicitud-cabecera">
          <h3><Link to={`/solicitudes/${s.id}`}>Solicitud #{s.id} · {s.rubro}</Link></h3>
          <p className="solicitud-estado"><IconoEstado estado={s.estado} />Estado: {s.estado_display || s.estado}</p>
        </header>
        <p className="solicitud-descripcion">{s.descripcion}</p>
        <p className="solicitud-cliente">{s.cliente_nombre} · <a href={`tel:${s.cliente_telefono}`}>{s.cliente_telefono}</a></p>
        <Seguimiento estado={s.estado} historial={s.historial} />
        <div className="solicitud-acciones">
          {s.estado === 'pendiente' ? <>
            <button type="button" className="boton boton-principal" disabled={ocupado} onClick={() => responder(s, 'aceptar')}><Check className="icono" size={18} aria-hidden="true" />Aceptar</button>
            <button type="button" className="boton-secundario" disabled={ocupado} onClick={() => responder(s, 'rechazar')}><X className="icono" size={18} aria-hidden="true" />Rechazar</button>
          </> : ACCION_SIGUIENTE[s.siguiente_estado]
            ? <button type="button" className="boton boton-principal" disabled={ocupado}
              onClick={() => responder(s, 'avanzar', { estado: s.siguiente_estado })}>
              <IconoEstado estado={s.siguiente_estado} size={18} />
              {ACCION_SIGUIENTE[s.siguiente_estado]}
            </button>
            : <p className="solicitud-cerrada">{s.estado === 'finalizada' ? 'Servicio finalizado.' : 'Esta solicitud ya fue respondida.'}</p>}
        </div>
      </article>)}
    </div>
    {id && <Link className="volver" to="/solicitudes">← Volver a la bandeja</Link>}
  </main>
}
