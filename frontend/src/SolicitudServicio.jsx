import { useEffect, useState } from 'react'

const ESTADOS = {
  pendiente: 'Pendiente de respuesta',
  aceptada: 'El prestador aceptó tu solicitud.',
  rechazada: 'El prestador rechazó tu solicitud.',
}

function mensajeError(datos, alternativa) {
  if (typeof datos?.detail === 'string') return datos.detail
  const mensajes = Object.values(datos || {})
    .flatMap((valor) => Array.isArray(valor) ? valor : [valor])
    .filter((valor) => typeof valor === 'string')
  return mensajes.join(' ') || alternativa
}

function SolicitudServicio({ prestador, rubro, ubicacion }) {
  const [datosCliente, setDatosCliente] = useState({
    cliente_nombre: '',
    cliente_telefono: '',
    descripcion: '',
  })
  const [solicitud, setSolicitud] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!solicitud?.id || solicitud.estado !== 'pendiente') return undefined

    const controlador = new AbortController()
    let temporizador
    async function consultarEstado() {
      try {
        const respuesta = await fetch(`/api/servicios/solicitudes/${solicitud.id}/`, {
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
        if (resultado.estado === 'pendiente') {
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
      const respuesta = await fetch('/api/servicios/solicitudes/', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...datosCliente,
          prestador: prestador.id,
          rubro: Number(rubro),
          latitud: ubicacion[0].toFixed(6),
          longitud: ubicacion[1].toFixed(6),
        }),
      })
      const resultado = await respuesta.json()
      if (!respuesta.ok) {
        throw new Error(mensajeError(resultado, 'No se pudo enviar la solicitud.'))
      }
      if (!resultado || !resultado.id || typeof resultado.estado !== 'string') {
        throw new Error('El servidor devolvió una solicitud inválida.')
      }
      setSolicitud(resultado)
    } catch (fallo) {
      setError(fallo instanceof TypeError || fallo instanceof SyntaxError
        ? 'No pudimos comunicarnos con el servidor. Inténtalo de nuevo.'
        : fallo.message)
    } finally {
      setEnviando(false)
    }
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
            {enviando ? 'Enviando solicitud…' : 'Enviar solicitud'}
          </button>
        </form>
      ) : (
        <div aria-live="polite" role="status">
          <h5>Solicitud #{solicitud.id}</h5>
          <p className={`estado-solicitud estado-${solicitud.estado}`}>
            {solicitud.estado_display || ESTADOS[solicitud.estado] || solicitud.estado}
          </p>
          {solicitud.estado === 'pendiente' && <p>Consultaremos el estado automáticamente cada 5 segundos.</p>}
          {error && <p className="error-solicitud" role="alert">{error}</p>}
        </div>
      )}
    </div>
  )
}

export default SolicitudServicio
