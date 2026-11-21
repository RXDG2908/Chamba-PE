import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { enviarJson } from './api.js'
import './Consentimiento.css'
import './EliminarDatos.css'

const nombres = { pendiente: 'Pendiente', procesando: 'Procesando', parcial: 'Parcial', fallida: 'Fallida', atendida: 'Atendida' }
const errores = {
  almacenamiento: 'Hay archivos que no se pudieron eliminar.',
  inventario: 'No se pudo comprobar todo el alcance de la solicitud.',
  persistencia: 'No se pudo guardar todo el resultado de la eliminación.',
  verificacion: 'La comprobación de eliminación todavía no está completa.',
}
const fecha = (valor) => valor ? new Date(valor).toLocaleString('es-PE') : '—'
const solicitudValida = (s) => s && typeof s.id === 'string' && Object.hasOwn(nombres, s.estado)
  && typeof s.creada === 'string' && Number.isInteger(s.pendientes) && s.pendientes >= 0
  && Array.isArray(s.eventos) && s.eventos.every((e) => e && Object.hasOwn(nombres, e.estado)
    && typeof e.fecha === 'string' && Number.isInteger(e.ejecucion))

export default function EliminarDatos() {
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [confirmo, setConfirmo] = useState(false)
  const [ocupado, setOcupado] = useState(false)
  const [revision, setRevision] = useState(0)
  const [consultando, setConsultando] = useState(true)
  const clave = useRef(null)
  const secuencia = useRef(0)
  const consulta = useRef(null)
  const escribiendo = useRef(false)

  useEffect(() => {
    if (escribiendo.current) return undefined
    const abort = new AbortController()
    const version = ++secuencia.current
    consulta.current = abort
    const vigente = () => !abort.signal.aborted && version === secuencia.current
    fetch('/api/verificacion/eliminaciones/', { signal: abort.signal })
      .then(async (r) => {
        if (!r.ok) throw new Error(r.status === 403 ? 'Revisa tu sesión y vuelve a ingresar.' : 'No pudimos consultar tus solicitudes.')
        const cuerpo = await r.json()
        if (!Array.isArray(cuerpo.solicitudes) || !cuerpo.solicitudes.every(solicitudValida) || typeof cuerpo.bloqueada !== 'boolean') throw new Error('La respuesta no permite comprobar el estado.')
        if (vigente()) setDatos(cuerpo)
      })
      .catch((e) => { if (vigente() && e.name !== 'AbortError') { setError(e instanceof TypeError ? 'No hay conexión. Vuelve a consultar el estado.' : e.message); setDatos(null) } })
      .finally(() => { if (vigente()) setConsultando(false) })
    return () => {
      abort.abort()
      if (consulta.current === abort) consulta.current = null
    }
  }, [revision])

  const registrar = (solicitud) => {
    setDatos((anterior) => {
      const solicitudes = [solicitud, ...(anterior?.solicitudes || []).filter((s) => s.id !== solicitud.id)]
      return { solicitudes, bloqueada: solicitudes.some((s) => s.estado !== 'atendida') }
    })
  }

  const enviar = async (solicitud) => {
    if (escribiendo.current) return
    escribiendo.current = true
    // Una respuesta o un error de lectura anteriores ya no pueden reemplazar este POST.
    secuencia.current++
    consulta.current?.abort()
    setConsultando(false)
    setOcupado(true)
    setError('')
    setAviso('')
    try {
      if (!solicitud && !clave.current) clave.current = crypto.randomUUID()
      const url = solicitud ? `/api/verificacion/eliminaciones/${solicitud.id}/reintentar/` : '/api/verificacion/eliminaciones/'
      const respuesta = await enviarJson(url, 'POST', solicitud ? undefined : { clave_idempotencia: clave.current, confirmo: true })
      const cuerpo = await respuesta.json()
      if (respuesta.status === 409 && cuerpo.solicitud) {
        if (!solicitudValida(cuerpo.solicitud)) throw new Error('No pudimos comprobar la solicitud existente.')
        registrar(cuerpo.solicitud)
        setAviso('Ya tienes una solicitud sin resolver; se conserva su historial.')
      } else {
        if (!respuesta.ok) throw new Error(cuerpo.detail || 'No pudimos registrar la operación. Puedes volver a intentarlo.')
        if (!solicitudValida(cuerpo)) throw new Error('No pudimos comprobar la solicitud registrada. Consulta su estado.')
        registrar(cuerpo)
        setAviso(cuerpo.estado === 'atendida' ? 'La solicitud ya fue atendida.' : 'Solicitud registrada, pendiente de atención. Consulta su estado para comprobar el resultado.')
      }
      setConfirmo(false)
    } catch (e) {
      setError(e instanceof TypeError ? 'No hay conexión. Puedes repetir sin duplicar la solicitud.' : (e.message || 'No pudimos registrar la operación.'))
    } finally {
      escribiendo.current = false
      setOcupado(false)
    }
  }

  return (
    <section className="registro cuenta consentimiento eliminacion-dni">
      <h2>Eliminar datos de verificación del DNI</h2>
      <p>Esta solicitud elimina las fotografías del DNI, el recorte del titular y los datos leídos durante su verificación.</p>
      <p><strong>Conserva tu cuenta y el DNI registrado en tu perfil.</strong> No elimina certificados ni solicitudes de servicios. Se conserva un historial mínimo sin fotografías ni datos leídos.</p>
      <p>El consentimiento DNI no autoriza reconocimiento facial. Su revocación se gestiona por separado.</p>
      {error && <p className="error caja" role="alert">{error}</p>}
      {aviso && <p className="confirmacion caja" role="status">{aviso}</p>}
      <button type="button" disabled={ocupado || consultando} onClick={() => { setError(''); setConsultando(true); setRevision((r) => r + 1) }}>Consultar estado</button>
      {consultando && <p role="status">Consultando solicitudes…</p>}
      {datos && (
        <>
          {datos.bloqueada ? <p className="aviso-provisional" role="status">Hay una eliminación sin resolver. Las nuevas capturas y envíos del DNI están bloqueados, incluso si aceptas nuevamente el consentimiento.</p> : (
            <div className="confirmacion-eliminacion">
              <label><input type="checkbox" checked={confirmo} disabled={ocupado} onChange={(e) => { setConfirmo(e.target.checked); clave.current = null }} /> Confirmo que quiero solicitar la eliminación de estos datos.</label>
              <button type="button" disabled={!confirmo || ocupado} onClick={() => enviar()}>{ocupado ? 'Registrando…' : 'Solicitar eliminación'}</button>
            </div>
          )}
          <h3>Mis solicitudes</h3>
          {datos.solicitudes.length === 0 && <p>Todavía no tienes solicitudes de eliminación.</p>}
          <ol className="solicitudes-eliminacion">
            {datos.solicitudes.map((s) => (
              <li key={s.id}>
                <h4>{nombres[s.estado] || 'Estado desconocido'}</h4>
                <p className="identificador">Solicitud: {s.id}</p>
                <p>Registrada: {fecha(s.creada)}</p>
                {s.estado === 'atendida' ? <p>Eliminación comprobada: {fecha(s.atendida_en)}</p> : <p>La eliminación todavía no está comprobada como completa.</p>}
                {s.codigo_error && <p>{errores[s.codigo_error] || 'Hay una incidencia pendiente de resolver.'}</p>}
                {!!s.pendientes && <p>Comprobaciones pendientes: {s.pendientes}.</p>}
                {['parcial', 'fallida'].includes(s.estado) && <button type="button" disabled={ocupado} onClick={() => enviar(s)}>Reintentar eliminación</button>}
                <details><summary>Historial de la solicitud</summary>
                  <ol>{(s.eventos || []).map((e, i) => <li key={i}>{fecha(e.fecha)} · {nombres[e.estado] || e.estado} · Ejecución {e.ejecucion}</li>)}</ol>
                </details>
              </li>
            ))}
          </ol>
        </>
      )}
      <div className="acciones-consentimiento"><Link to="/consentimiento">Consultar consentimiento</Link><Link to="/verificar-dni">Verificar mi DNI</Link></div>
    </section>
  )
}
