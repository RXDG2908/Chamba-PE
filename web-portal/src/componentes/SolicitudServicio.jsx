import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { enviarJson } from '../nucleo/api.js'
import Seguimiento from './Seguimiento.jsx'
import { ESTADOS_FINALES } from '../nucleo/etapasSeguimiento.js'
import { LogIn, Send } from 'lucide-react'
import IconoEstado from './IconoEstado.jsx'

const ESTADOS = {
  pendiente: 'Pendiente de respuesta',
  aceptada: 'El prestador confirmó tu solicitud.',
  rechazada: 'El prestador rechazó tu solicitud.',
  en_camino: 'El prestador está en camino.',
  en_servicio: 'El prestador está realizando el servicio.',
  finalizada: 'El servicio terminó. Puedes calificarlo en «Mis servicios».',
}

// La API responde en inglés cuando falta la sesión (DRF); se explica en español.
const SIN_SESION = 'Authentication credentials were not provided.'

function mensajeError(datos, alternativa) {
  if (datos?.detail === SIN_SESION) return 'Tu sesión venció. Inicia sesión de nuevo para enviar la solicitud.'
  if (typeof datos?.detail === 'string') return datos.detail
  const mensajes = Object.values(datos || {})
    .flatMap((valor) => Array.isArray(valor) ? valor : [valor])
    .filter((valor) => typeof valor === 'string')
  return mensajes.join(' ') || alternativa
}

function SolicitudServicio({ prestador, rubro, ubicacion, usuario }) {
  const [datosCliente, setDatosCliente] = useState({
    cliente_nombre: '',
    cliente_telefono: '',
    descripcion: '',
  })
  const [solicitud, setSolicitud] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState('')
  // HU-11: el detalle solo lo ven el cliente y el prestador; sin cuenta, el código lo identifica.
  const codigo = useRef('')

  useEffect(() => {
    // HU-11: se sigue consultando hasta que el servicio termine o sea rechazado.
    if (!solicitud?.id || ESTADOS_FINALES.includes(solicitud.estado)) return undefined

    const controlador = new AbortController()
    let temporizador
    async function consultarEstado() {
      try {
        const consulta = codigo.current ? `?codigo=${encodeURIComponent(codigo.current)}` : ''
        const respuesta = await fetch(`/api/servicios/solicitudes/${solicitud.id}/${consulta}`, {
          signal: controlador.signal,
        })
        const resultado = await respuesta.json()
        if (!respuesta.ok) {
          throw new Error(mensajeError(resultado, 'No se pudo consultar el estado de la solicitud.'))
        }
        if (!resultado || typeof resultado.estado !== 'string') {
          throw new Error('El servidor devolvió un estado de solicitud inválido.')
        }
        setSolicitud(resultado)
        setError('')
        if (!ESTADOS_FINALES.includes(resultado.estado)) {
          temporizador = setTimeout(consultarEstado, 5000)
        }
      } catch (fallo) {
        if (!controlador.signal.aborted) {
          setError(fallo instanceof TypeError || fallo instanceof SyntaxError
            ? 'No pudimos consultar el estado. Reintentaremos automáticamente.'
            : `${fallo.message} Reintentaremos automáticamente.`)
          temporizador = setTimeout(consultarEstado, 5000)
        }
      }
    }

    temporizador = setTimeout(consultarEstado, 5000)
    return () => {
      controlador.abort()
      clearTimeout(temporizador)
    }
  }, [solicitud?.id, solicitud?.estado])

  async function enviar(evento) {
    evento.preventDefault()
    const nombre = datosCliente.cliente_nombre.normalize('NFC')
    if (!/\p{L}/u.test(nombre) || /[^\p{L} '\u2019-]/u.test(nombre) || nombre.length > 100) {
      setError('Ingresa un nombre con al menos una letra; solo letras, espacios, guiones y apóstrofes (máximo 100 caracteres).')
      return
    }
    if (datosCliente.cliente_telefono.length !== 9 || !/^[0-9]{9}$/.test(datosCliente.cliente_telefono)) {
      setError('Ingresa exactamente 9 dígitos del 0 al 9, sin +51 ni espacios.')
      return
    }
    if (!datosCliente.descripcion.trim() || datosCliente.descripcion.length > 500) {
      setError('Describe el trabajo, sin dejarlo en blanco (máximo 500 caracteres).')
      return
    }
    setEnviando(true)
    setError('')
    try {
      // Con CSRF: si el cliente tiene sesión, la solicitud queda a su nombre y podrá calificarla (HU-12).
      const respuesta = await enviarJson('/api/servicios/solicitudes/', 'POST', {
        ...datosCliente,
        prestador: prestador.id,
        rubro: Number(rubro),
        latitud: ubicacion[0].toFixed(6),
        longitud: ubicacion[1].toFixed(6),
      })
      const resultado = await respuesta.json()
      if (!respuesta.ok) {
        throw new Error(mensajeError(resultado, 'No se pudo enviar la solicitud.'))
      }
      if (!resultado || !resultado.id || typeof resultado.estado !== 'string') {
        throw new Error('El servidor devolvió una solicitud inválida.')
      }
      codigo.current = typeof resultado.codigo_seguimiento === 'string' ? resultado.codigo_seguimiento : ''
      setSolicitud(resultado)
    } catch (fallo) {
      setError(fallo instanceof TypeError || fallo instanceof SyntaxError
        ? 'No pudimos comunicarnos con el servidor. Inténtalo de nuevo.'
        : fallo.message)
    } finally {
      setEnviando(false)
    }
  }

  // Solo un cliente con sesión puede solicitar (la API responde 403 a los demás).
  if (!solicitud && usuario?.rol !== 'cliente') {
    return (
      <div className="solicitud-servicio solicitud-sin-sesion">
        <h5>Solicitar este servicio</h5>
        {usuario === undefined ? (
          <p>Comprobando tu sesión…</p>
        ) : usuario === null ? (
          <>
            <p>Para solicitar un servicio necesitas una cuenta de cliente.</p>
            <p className="acciones-sin-sesion">
              <Link className="boton" to="/ingresar"
                state={{ desde: '/resultados', volverCon: rubro ? { rubro: Number(rubro) } : undefined }}>
                <LogIn className="icono" size={18} aria-hidden="true" />Inicia sesión para solicitar
              </Link>
              <Link to="/crear-cuenta">Crear cuenta de cliente</Link>
            </p>
          </>
        ) : (
          <p>Solo las cuentas de cliente pueden solicitar servicios.</p>
        )}
      </div>
    )
  }

  return (
    <div className="solicitud-servicio">
      {!solicitud ? (
        <form onSubmit={enviar} noValidate>
          <h5>Solicitar este servicio</h5>
          <label>
            <span>Tu nombre</span>
            <input
              required
              maxLength="100"
              value={datosCliente.cliente_nombre}
              onChange={(evento) => setDatosCliente({ ...datosCliente, cliente_nombre: evento.target.value })}
            />
          </label>
          <label>
            <span>Teléfono</span>
            <input
              required
              type="tel"
              inputMode="numeric"
              aria-label="Teléfono: 9 dígitos sin +51"
              value={datosCliente.cliente_telefono}
              onChange={(evento) => setDatosCliente({ ...datosCliente, cliente_telefono: evento.target.value })}
            />
          </label>
          <label>
            <span>Describe el trabajo</span>
            <textarea
              required
              maxLength="500"
              rows="3"
              value={datosCliente.descripcion}
              onChange={(evento) => setDatosCliente({ ...datosCliente, descripcion: evento.target.value })}
            />
          </label>
          {error && <p className="error-solicitud" role="alert">{error}</p>}
          <button type="submit" disabled={enviando}>
            <Send className="icono" size={18} aria-hidden="true" />
            {enviando ? 'Enviando solicitud…' : 'Enviar solicitud'}
          </button>
        </form>
      ) : (
        <div aria-live="polite" role="status">
          <h5>Solicitud #{solicitud.id}</h5>
          <p className={`estado-solicitud estado-${solicitud.estado}`}>
            <IconoEstado estado={solicitud.estado} />
            {ESTADOS[solicitud.estado] || solicitud.estado_display || solicitud.estado}
          </p>
          <Seguimiento estado={solicitud.estado} historial={solicitud.historial} />
          {!ESTADOS_FINALES.includes(solicitud.estado) && <p>Consultaremos el estado automáticamente cada 5 segundos.</p>}
          {error && <p className="error-solicitud" role="alert">{error}</p>}
        </div>
      )}
    </div>
  )
}

export default SolicitudServicio
