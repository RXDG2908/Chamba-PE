import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { enviarJson } from './api.js'
import { activarNotificaciones, desactivarNotificaciones } from './notificaciones.js'

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
  async function responder(solicitud, accion) {
    setOcupado(true)
    try {
      const r = await enviarJson(`/api/servicios/solicitudes/${solicitud.id}/${accion}/`, 'POST')
      if (r.status === 403) { alVencer(null); return }
      if (r.status === 409) {
        setMensaje('La solicitud ya fue respondida. Consultando su estado actualizado.')
        // Retirar inmediatamente las acciones obsoletas, también si la recarga falla.
        recargar()
        return
      }
      if (!r.ok) throw new Error('No se pudo responder la solicitud.')
      const dato = await r.json()
      setVista((anterior) => anterior?.clave === clave
        ? { ...anterior, solicitudes: anterior.solicitudes.map((s) => s.id === dato.id ? dato : s) }
        : anterior)
      setMensaje('Respuesta guardada.')
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
  return <main className="registro cuenta">
    <h2>Solicitudes recibidas</h2>
    <button disabled={ocupado} onClick={() => configurar(true)}>Activar notificaciones</button>{' '}
    <button disabled={ocupado} onClick={() => configurar(false)}>Desactivar notificaciones</button>
    <p role="status">{mensaje}</p>
    {errorActual && <div role="alert"><p>{errorActual}</p><button onClick={recargar}>Reintentar consulta</button></div>}
    {solicitudes === null && !errorActual && <p>Cargando solicitudes…</p>}
    {solicitudes?.length === 0 && <p>Aún no tienes solicitudes.</p>}
    {solicitudes?.map((s) => <article key={s.id} className="caja">
      <h3><Link to={`/solicitudes/${s.id}`}>Solicitud #{s.id} · {s.rubro}</Link></h3>
      <p>{s.descripcion}</p><p>{s.cliente_nombre} · {s.cliente_telefono}</p>
      <p>Estado: {s.estado}</p>
      {s.estado === 'pendiente' ? <>
        <button disabled={ocupado} onClick={() => responder(s, 'aceptar')}>Aceptar</button>{' '}
        <button disabled={ocupado} onClick={() => responder(s, 'rechazar')}>Rechazar</button>
      </> : <p>Esta solicitud ya fue respondida.</p>}
    </article>)}
    {id && <Link to="/solicitudes">Volver a la bandeja</Link>}
  </main>
}
