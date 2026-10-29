import { useEffect, useState } from 'react'
import './RevisionDocumentos.css'

const API = '/api/administracion/revisiones/'
const RUTA_PANEL = '/administracion/revisiones'
const LOGIN_ADMIN = `/admin/login/?next=${encodeURIComponent(RUTA_PANEL)}`

const ETIQUETAS = { aprobado: 'Aprobado', rechazado: 'Rechazado' }

function leerCookie(nombre) {
  const fila = document.cookie.split('; ').find((cookie) => cookie.startsWith(`${nombre}=`))
  return fila ? decodeURIComponent(fila.split('=')[1]) : ''
}

function primerError(datos) {
  if (!datos || typeof datos !== 'object') return ''
  const valor = datos.detail ?? Object.values(datos)[0]
  return Array.isArray(valor) ? valor[0] : String(valor ?? '')
}

function formatearFecha(iso) {
  return new Date(iso).toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' })
}

function TarjetaRevision({ revision, onResuelta }) {
  const [rechazando, setRechazando] = useState(false)
  const [comentario, setComentario] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState('')
  const resuelta = revision.estado !== 'pendiente'
  const { prestador } = revision

  const decidir = async (estado) => {
    if (estado === 'rechazado' && !comentario.trim()) {
      setError('Escribe el motivo del rechazo.')
      return
    }
    setEnviando(true)
    setError('')
    try {
      const respuesta = await fetch(`${API}${revision.id}/`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-CSRFToken': leerCookie('csrftoken') },
        body: JSON.stringify(estado === 'rechazado' ? { estado, comentario: comentario.trim() } : { estado }),
      })
      const datos = await respuesta.json().catch(() => null)
      if (respuesta.ok) {
        onResuelta(datos)
      } else if (respuesta.status === 403) {
        setError('Tu sesión de administrador expiró. Vuelve a iniciar sesión.')
      } else {
        setError(primerError(datos) || 'No se pudo guardar la decisión.')
      }
    } catch {
      setError('No se pudo establecer conexión con el servidor.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <li className={`revision-tarjeta${resuelta ? ` revision-${revision.estado}` : ''}`}>
      <div className="revision-cabecera">
        <div className="revision-datos">
          <h3>{prestador ? prestador.nombre : 'Prestador no asociado'}</h3>
          <dl>
            <div><dt>DNI</dt><dd>{prestador?.dni ?? '—'}</dd></div>
            <div>
              <dt>Documento</dt>
              <dd>{revision.tipo_documento}{revision.formato ? ` · ${revision.formato}` : ''}</dd>
            </div>
            <div><dt>Recibido</dt><dd>{formatearFecha(revision.creado)}</dd></div>
          </dl>
        </div>
        {revision.archivo_url ? (
          <a className="revision-ver" href={revision.archivo_url} target="_blank" rel="noopener noreferrer">
            Ver documento
          </a>
        ) : (
          <span className="revision-sin-archivo">Sin archivo</span>
        )}
      </div>

      {resuelta ? (
        <p className="revision-resultado" role="status">
          <strong>{ETIQUETAS[revision.estado]}.</strong>{' '}
          {prestador && `El prestador quedó «${prestador.estado_display}».`}
          {revision.comentario && <span className="revision-motivo">Motivo: {revision.comentario}</span>}
        </p>
      ) : (
        <div className="revision-acciones">
          {rechazando ? (
            <form
              className="revision-rechazo"
              onSubmit={(evento) => {
                evento.preventDefault()
                decidir('rechazado')
              }}
            >
              <label htmlFor={`motivo-${revision.id}`}>Motivo del rechazo</label>
              <textarea
                id={`motivo-${revision.id}`}
                rows="3"
                required
                autoFocus
                value={comentario}
                onChange={(evento) => {
                  setComentario(evento.target.value)
                  setError('')
                }}
                placeholder="Ej.: el documento está vencido o no se lee."
              />
              <div className="revision-botones">
                <button type="submit" className="boton-rechazar" disabled={enviando || !comentario.trim()}>
                  {enviando ? 'Enviando…' : 'Confirmar rechazo'}
                </button>
                <button
                  type="button"
                  className="boton-secundario"
                  disabled={enviando}
                  onClick={() => {
                    setRechazando(false)
                    setComentario('')
                    setError('')
                  }}
                >
                  Cancelar
                </button>
              </div>
            </form>
          ) : (
            <div className="revision-botones">
              <button type="button" className="boton-aprobar" disabled={enviando} onClick={() => decidir('aprobado')}>
                {enviando ? 'Enviando…' : 'Aprobar'}
              </button>
              <button type="button" className="boton-rechazar" disabled={enviando} onClick={() => setRechazando(true)}>
                Rechazar
              </button>
            </div>
          )}
        </div>
      )}

      {error && <p className="revision-error" role="alert">{error}</p>}
    </li>
  )
}

export default function RevisionDocumentos() {
  const [revisiones, setRevisiones] = useState([])
  const [estado, setEstado] = useState('cargando')
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    const controlador = new AbortController()
    const cargar = async () => {
      try {
        const respuesta = await fetch(`${API}?estado=pendiente`, { signal: controlador.signal })
        if (respuesta.status === 401 || respuesta.status === 403) {
          setEstado('sin-permiso')
          return
        }
        if (!respuesta.ok) throw new Error(`HTTP ${respuesta.status}`)
        const datos = await respuesta.json()
        setRevisiones(datos)
        setEstado('listo')
      } catch {
        if (!controlador.signal.aborted) setEstado('error')
      }
    }
    cargar()
    return () => controlador.abort()
  }, [intento])

  const actualizar = () => {
    setEstado('cargando')
    setIntento((actual) => actual + 1)
  }

  const marcarResuelta = (actualizada) => {
    setRevisiones((actuales) => actuales.map((r) => (r.id === actualizada.id ? actualizada : r)))
  }

  const pendientes = revisiones.filter((r) => r.estado === 'pendiente').length

  return (
    <main className="contenedor revision">
      <p className="etiqueta">ADMINISTRACIÓN</p>
      <div className="revision-titulo">
        <div>
          <h2>Revisión de documentos</h2>
          <p>Aprueba o rechaza los certificados. Solo los prestadores con un documento aprobado quedan «Verificado» y aparecen en la búsqueda.</p>
        </div>
        <button type="button" className="boton-secundario" onClick={actualizar} disabled={estado === 'cargando'}>
          Actualizar
        </button>
      </div>

      {estado === 'cargando' && <p className="revision-aviso" role="status">Cargando documentos…</p>}

      {estado === 'sin-permiso' && (
        <div className="revision-aviso revision-aviso-error" role="alert">
          <p>Esta pantalla es solo para administradores.</p>
          <a href={LOGIN_ADMIN}>Iniciar sesión como administrador</a>
        </div>
      )}

      {estado === 'error' && (
        <div className="revision-aviso revision-aviso-error" role="alert">
          <p>No se pudieron cargar los documentos. Revisa que Django esté encendido.</p>
          <button type="button" className="boton-secundario" onClick={actualizar}>Reintentar</button>
        </div>
      )}

      {estado === 'listo' && (
        <>
          <p className="revision-resumen" aria-live="polite">
            {pendientes === 1 ? '1 documento pendiente' : `${pendientes} documentos pendientes`}
          </p>
          {revisiones.length === 0 ? (
            <p className="revision-aviso">No hay documentos por revisar.</p>
          ) : (
            <ul className="revision-lista">
              {revisiones.map((revision) => (
                <TarjetaRevision key={revision.id} revision={revision} onResuelta={marcarResuelta} />
              ))}
            </ul>
          )}
        </>
      )}
    </main>
  )
}
