import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { enviarJson } from '../../nucleo/api.js'
import Seguimiento from '../../componentes/Seguimiento.jsx'
import { ESTADOS_CON_CHAT, ESTADOS_FINALES } from '../../nucleo/etapasSeguimiento.js'
import '../publico/RegistroPrestador.css'
import '../../estilos/Cuenta.css'
import './MisServicios.css'
import { ClipboardList, Star } from 'lucide-react'
import Cargando from '../../componentes/Cargando.jsx'
import IconoEstado from '../../componentes/IconoEstado.jsx'
import ChatFlotante, { BotonChat } from '../../componentes/ChatFlotante.jsx'

const ESTRELLAS = [1, 2, 3, 4, 5]
const formatoFecha = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' })

// Solo se aceptan datos con la forma esperada; lo demás se trata como fallo de la API.
function servicioValido(s) {
  return s && Number.isInteger(s.id) && typeof s.estado === 'string' && typeof s.estado_display === 'string'
    && s.prestador && typeof s.prestador.nombre === 'string' && typeof s.puede_calificar === 'boolean'
    && (s.calificacion === null || Number.isInteger(s.calificacion?.estrellas))
}

function Estrellas({ cantidad }) {
  return (
    <span className="estrellas-fijas" role="img" aria-label={`${cantidad} de 5 estrellas`}>
      {ESTRELLAS.map((n) => <Star key={n} size={16} aria-hidden="true" className={n <= cantidad ? 'llena' : ''} fill="currentColor" />)}
    </span>
  )
}

function FormularioCalificacion({ servicio, alCalificar }) {
  const [estrellas, setEstrellas] = useState(0)
  const [comentario, setComentario] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState('')

  const enviar = async (evento) => {
    evento.preventDefault()
    if (!estrellas) {
      setError('Elige de 1 a 5 estrellas.')
      return
    }
    setEnviando(true)
    setError('')
    try {
      const respuesta = await enviarJson('/api/calificaciones/', 'POST', {
        solicitud: servicio.id, estrellas, comentario: comentario.trim(),
      })
      const cuerpo = await respuesta.json().catch(() => null)
      if (!respuesta.ok) {
        const mensaje = cuerpo?.detail || cuerpo?.estrellas?.[0] || cuerpo?.comentario?.[0]
        setError(mensaje || 'No se pudo guardar tu calificación. Inténtalo de nuevo.')
        return
      }
      if (!Number.isInteger(cuerpo?.calificacion?.estrellas)) throw new Error()
      alCalificar(servicio.id, cuerpo.calificacion)
    } catch {
      setError('No pudimos comunicarnos con el servidor. Inténtalo de nuevo.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form className="formulario-calificacion" onSubmit={enviar} noValidate>
      <fieldset className="selector-estrellas">
        <legend>¿Cómo fue el servicio?</legend>
        {ESTRELLAS.map((n) => (
          <label key={n} className={n <= estrellas ? 'llena' : ''}>
            <input type="radio" name={`estrellas-${servicio.id}`} value={n} checked={estrellas === n}
              onChange={() => setEstrellas(n)} disabled={enviando} />
            <Star className="estrella" size={30} aria-hidden="true" fill="currentColor" strokeWidth={1.5} />
            <span className="solo-lector">{n === 1 ? '1 estrella' : `${n} estrellas`}</span>
          </label>
        ))}
      </fieldset>
      <label className="campo-comentario">
        Comentario (opcional)
        <textarea value={comentario} maxLength={500} rows={3} disabled={enviando}
          onChange={(e) => setComentario(e.target.value)}
          placeholder="Cuenta cómo te atendió para ayudar a otros clientes." />
        <small>{comentario.length}/500</small>
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <button type="submit" className="boton" disabled={enviando}>
        <Star className="icono" size={18} aria-hidden="true" />
        {enviando ? 'Enviando…' : 'Enviar calificación'}
      </button>
    </form>
  )
}

function MisServicios() {
  const [servicios, setServicios] = useState([])
  const [estado, setEstado] = useState('cargando') // cargando | listo | fallo
  const [chat, setChat] = useState(null)

  const enCurso = servicios.some((s) => !ESTADOS_FINALES.includes(s.estado))

  useEffect(() => {
    fetch('/api/calificaciones/mis-servicios/')
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then((cuerpo) => {
        if (!Array.isArray(cuerpo) || !cuerpo.every(servicioValido)) throw new Error()
        setServicios(cuerpo)
        setEstado('listo')
      })
      .catch(() => setEstado('fallo'))
  }, [])

  // HU-11: mientras algún servicio siga en curso, se consulta su estado cada 5 segundos.
  useEffect(() => {
    if (estado !== 'listo' || !enCurso) return undefined
    const controlador = new AbortController()
    const temporizador = setInterval(() => {
      fetch('/api/calificaciones/mis-servicios/', { signal: controlador.signal, cache: 'no-store' })
        .then((r) => (r.ok ? r.json() : Promise.reject()))
        .then((cuerpo) => {
          // Una consulta fallida no borra lo que ya se ve; se reintenta en la siguiente vuelta.
          if (Array.isArray(cuerpo) && cuerpo.every(servicioValido)) setServicios(cuerpo)
        })
        .catch(() => {})
    }, 5000)
    return () => {
      controlador.abort()
      clearInterval(temporizador)
    }
  }, [estado, enCurso])

  const registrarCalificacion = (id, calificacion) => {
    setServicios((actuales) => actuales.map((s) => (
      s.id === id ? { ...s, calificacion, puede_calificar: false } : s
    )))
  }

  if (estado === 'cargando') return <Cargando className="registro cuenta" texto="Cargando tus servicios…" />
  if (estado === 'fallo') {
    return <p className="registro cuenta error" role="alert">No pudimos cargar tus servicios. Recarga la página.</p>
  }

  return (
    <section className="registro cuenta mis-servicios">
      <h2><span className="icono-titulo" aria-hidden="true"><ClipboardList size={22} /></span>Mis servicios</h2>
      <p className="ayuda">
        Aquí ves las solicitudes que enviaste con tu cuenta y en qué etapa va cada servicio (se actualiza
        sola cada 5 segundos). Cuando un servicio queda Finalizado puedes calificarlo
        una sola vez, de 1 a 5 estrellas.
      </p>
      {servicios.length === 0 && (
        <p className="vacio">
          Aún no tienes solicitudes. <Link to="/resultados">Busca un servicio</Link> con tu sesión iniciada para
          poder calificarlo después.
        </p>
      )}
      <ul className="lista-servicios">
        {servicios.map((s) => (
          <li key={s.id} className="servicio">
            <div className="servicio-cabecera">
              <div>
                <h3>{s.prestador.nombre}</h3>
                <p className="servicio-detalle">
                  {s.rubro} · {formatoFecha.format(new Date(s.creada))}
                </p>
              </div>
              <span className={`etiqueta-estado estado-${s.estado}`}><IconoEstado estado={s.estado} />{s.estado_display}</span>
            </div>
            {s.descripcion && <p className="servicio-descripcion">{s.descripcion}</p>}
            <Seguimiento estado={s.estado} historial={s.historial} />
            {ESTADOS_CON_CHAT.includes(s.estado) && (
              <BotonChat alAbrir={() => setChat({ id: s.id, titulo: s.prestador.nombre })} />
            )}
            {s.calificacion && (
              <div className="calificacion-dada">
                <p><strong>Tu calificación:</strong> <Estrellas cantidad={s.calificacion.estrellas} /></p>
                {s.calificacion.comentario && <p className="comentario-dado">«{s.calificacion.comentario}»</p>}
              </div>
            )}
            {s.puede_calificar && <FormularioCalificacion servicio={s} alCalificar={registrarCalificacion} />}
            {!s.calificacion && !s.puede_calificar && (
              <p className="nota-calificar">Podrás calificarlo cuando el servicio esté Finalizado.</p>
            )}
          </li>
        ))}
      </ul>
      {chat && <ChatFlotante key={chat.id} solicitudId={chat.id} titulo={chat.titulo} alCerrar={() => setChat(null)} />}
    </section>
  )
}

export default MisServicios
