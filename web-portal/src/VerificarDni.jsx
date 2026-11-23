import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { erroresPorCampo, leerCookie } from './api.js'
import './RegistroPrestador.css'
import './Cuenta.css'
import './VerificarDni.css'

const LADOS = { anverso: 'del frente (anverso)', reverso: 'de la parte de atrás (reverso)' }

const MOTIVOS = {
  mrz_ilegible: 'No pudimos leer las líneas de abajo del reverso. Prueba con más luz y sin reflejos.',
  mrz_invalida: 'Las líneas del reverso se leyeron mal. Toma la foto de nuevo con el DNI completo.',
  dni_distinto: 'El número del DNI no es el que registraste en tu perfil.',
  dni_vencido: 'Tu DNI figura como vencido.',
  fuente_no_disponible: 'No pudimos confirmar tus nombres con la fuente externa.',
  nombres_distintos: 'Tus nombres no coinciden con los de la fuente externa.',
  sin_foto: 'No pudimos recortar la foto del DNI.',
  lector_no_disponible: 'El lector del DNI no está disponible por ahora.',
}

// Texto para el prestador según la respuesta del servidor.
function mensajeDeError(estado, cuerpo) {
  const porCampo = erroresPorCampo(cuerpo)
  if (estado === 400) {
    const lados = ['anverso', 'reverso'].filter((l) => porCampo[l])
    if (lados.length) {
      return lados.map((l) => `${l === 'anverso' ? 'Frente' : 'Reverso'}: ${porCampo[l]}`).join(' ')
    }
  }
  if (estado === 401 || (estado === 403 && !porCampo.detail)) return 'Tu sesión terminó. Vuelve a iniciar sesión.'
  if (estado === 403 || estado === 429) return porCampo.detail || 'No tienes permiso para esta acción.'
  if (estado >= 500) return 'El servidor tuvo un problema. Inténtalo de nuevo en unos minutos.'
  return 'No pudimos enviar las fotos. Inténtalo de nuevo.'
}

function comprobarEstadoConsentimiento(datos) {
  if (!datos || typeof datos.vigente !== 'boolean' || typeof datos.eliminacion_pendiente !== 'boolean') {
    throw new Error('No pudimos comprobar el estado de tu consentimiento y eliminación. Vuelve a consultar.')
  }
  return datos
}

// Foto desde la cámara; si no hay cámara o permiso, se usa el selector del celular.
function Camara({ alCapturar }) {
  const video = useRef(null)
  const [camara, setCamara] = useState('iniciando') // iniciando | lista | sin

  useEffect(() => {
    let flujo = null
    let activo = true
    if (!navigator.mediaDevices?.getUserMedia) {
      Promise.resolve().then(() => activo && setCamara('sin'))
      return undefined
    }
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
      .then((medio) => {
        if (!activo) {
          medio.getTracks().forEach((t) => t.stop())
          return
        }
        flujo = medio
        video.current.srcObject = medio
        setCamara('lista')
      })
      .catch(() => activo && setCamara('sin'))
    return () => {
      activo = false
      flujo?.getTracks().forEach((t) => t.stop())
    }
  }, [])

  const tomarFoto = () => {
    const imagen = video.current
    const lienzo = document.createElement('canvas')
    lienzo.width = imagen.videoWidth
    lienzo.height = imagen.videoHeight
    lienzo.getContext('2d').drawImage(imagen, 0, 0)
    lienzo.toBlob((blob) => alCapturar(blob), 'image/jpeg', 0.92)
  }

  return (
    <div className="camara">
      {camara !== 'sin' && (
        <div className="visor">
          <video ref={video} autoPlay playsInline muted aria-label="Vista de la cámara" />
          <div className="marco-dni" aria-hidden="true" />
        </div>
      )}
      {camara === 'lista' && (
        <button type="button" className="boton-principal" onClick={tomarFoto}>Tomar foto</button>
      )}
      {camara === 'iniciando' && <p role="status">Abriendo la cámara…</p>}
      {camara === 'sin' && (
        <div className="caja">
          <p>No pudimos usar la cámara. Toma la foto con el botón y se abrirá la cámara de tu celular.</p>
          <label className="boton-archivo">
            Abrir cámara
            <input
              type="file"
              accept="image/*"
              capture="environment"
              onChange={(e) => e.target.files[0] && alCapturar(e.target.files[0])}
            />
          </label>
        </div>
      )}
      <p className="ayuda">Busca buena luz, evita los reflejos y deja el DNI completo dentro del marco.</p>
    </div>
  )
}

function Resultado({ intento, alReintentar }) {
  const validado = intento.resultado === 'validado'
  const motivos = intento.motivos.filter((m) => MOTIVOS[m])
  return (
    <div className="resultado" role="status">
      <h3 className={validado ? 'ok' : 'revision'}>{validado ? 'DNI validado' : 'En revisión manual'}</h3>
      {intento.lectura.dni && (
        <dl className="lectura">
          <dt>DNI</dt><dd>{intento.lectura.dni}</dd>
          <dt>Apellidos</dt><dd>{intento.lectura.apellidos}</dd>
          <dt>Nombres</dt><dd>{intento.lectura.nombres}</dd>
          <dt>Vence</dt><dd>{intento.lectura.fecha_vencimiento}</dd>
        </dl>
      )}
      {motivos.length > 0 && (
        <ul className="motivos">
          {motivos.map((m) => <li key={m}>{MOTIVOS[m]}</li>)}
        </ul>
      )}
      <p className="ayuda">
        {validado
          ? 'Administración revisará tus documentos antes de activar tu perfil.'
          : 'Un administrador revisará tu caso. También puedes volver a intentarlo.'}
      </p>
      <button type="button" onClick={alReintentar}>Volver a intentar</button>
    </div>
  )
}

function VerificarDni() {
  const navegar = useNavigate()
  const [revision, setRevision] = useState(0)
  const urls = useRef(new Set())
  const [paso, setPaso] = useState('cargando') // cargando | anverso | reverso | enviar | enviando | resultado
  const [fotos, setFotos] = useState({}) // lado → { blob, url }
  const [intento, setIntento] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    const guardadas = urls.current
    return () => { guardadas.forEach((url) => URL.revokeObjectURL(url)); guardadas.clear() }
  }, [])

  useEffect(() => {
    const abort = new AbortController()
    const cargar = async () => {
      try {
        const permiso = await fetch('/api/verificacion/consentimiento/', { signal: abort.signal })
        if (!permiso.ok) throw new Error('No pudimos consultar tu consentimiento. Revisa tu sesión e inténtalo de nuevo.')
        const consentimiento = comprobarEstadoConsentimiento(await permiso.json())
        if (consentimiento.eliminacion_pendiente === true) {
          setPaso('eliminacion')
          return
        }
        if (consentimiento.vigente === false) {
          navegar('/consentimiento', { replace: true })
          return
        }
        if (revision > 0) { setPaso('anverso'); return }
        const r = await fetch('/api/verificacion/dni/', { signal: abort.signal })
        if (r.status === 404) { setPaso('anverso'); return }
        if (!r.ok) throw new Error('No pudimos consultar tu último intento. Inténtalo de nuevo.')
        setIntento(await r.json())
        setPaso('resultado')
      } catch (e) {
        if (e.name !== 'AbortError') {
          setError(e instanceof TypeError ? 'No hay conexión con el servidor. Revisa tu internet.' : e.message)
          setPaso('error')
        }
      }
    }
    cargar()
    return () => abort.abort()
  }, [navegar, revision])

  const guardarFoto = (lado, blob) => {
    if (!blob) { setError('No pudimos capturar la imagen. Inténtalo de nuevo.'); return }
    setError('')
    if (fotos[lado]) { URL.revokeObjectURL(fotos[lado].url); urls.current.delete(fotos[lado].url) }
    const url = URL.createObjectURL(blob)
    urls.current.add(url)
    setFotos((antes) => ({ ...antes, [lado]: { blob, url } }))
    setPaso(lado === 'anverso' ? 'reverso' : 'enviar')
  }

  const repetir = (lado) => {
    setFotos((antes) => {
      URL.revokeObjectURL(antes[lado].url)
      urls.current.delete(antes[lado].url)
      const resto = { ...antes }
      delete resto[lado]
      return resto
    })
    setPaso(lado)
  }

  const limpiarFotos = () => {
    urls.current.forEach((url) => URL.revokeObjectURL(url))
    urls.current.clear()
    setFotos({})
  }

  const reintentar = () => {
    limpiarFotos()
    setError('')
    setPaso('cargando')
    setRevision((r) => r + 1)
  }

  const enviar = async () => {
    let permisoComprobado = false
    setPaso('enviando')
    setError('')
    const datos = new FormData()
    for (const lado of ['anverso', 'reverso']) {
      datos.append(lado, fotos[lado].blob, `${lado}.${fotos[lado].blob.type === 'image/png' ? 'png' : 'jpg'}`)
    }
    try {
      const permiso = await fetch('/api/verificacion/consentimiento/')
      if (!permiso.ok) throw new Error('No pudimos comprobar tu consentimiento.')
      const consentimiento = comprobarEstadoConsentimiento(await permiso.json())
      if (consentimiento.eliminacion_pendiente === true) {
        limpiarFotos()
        setPaso('eliminacion')
        return
      }
      if (consentimiento.vigente === false) { navegar('/consentimiento', { replace: true }); return }
      permisoComprobado = true
      const respuesta = await fetch('/api/verificacion/dni/', {
        method: 'POST',
        headers: { 'X-CSRFToken': leerCookie('csrftoken') },
        body: datos,
      })
      const cuerpo = await respuesta.json().catch(() => ({}))
      if (respuesta.status === 409) {
        limpiarFotos()
        setPaso('eliminacion')
        return
      }
      if (respuesta.ok) {
        setIntento(cuerpo)
        setPaso('resultado')
        return
      }
      if (respuesta.status === 403) {
        permisoComprobado = false
        const actual = await fetch('/api/verificacion/consentimiento/')
        if (!actual.ok) throw new Error('No pudimos comprobar tu consentimiento.')
        const estado = comprobarEstadoConsentimiento(await actual.json())
        if (estado.eliminacion_pendiente === true) {
          limpiarFotos()
          setPaso('eliminacion')
          return
        }
        if (estado.vigente === false) { navegar('/consentimiento', { replace: true }); return }
        permisoComprobado = true
      }
      setError(mensajeDeError(respuesta.status, cuerpo))
    } catch (e) {
      setError(e.message || 'No hay conexión con el servidor. Inténtalo de nuevo.')
      if (!permisoComprobado) {
        limpiarFotos()
        setPaso('error')
        return
      }
    }
    setPaso('enviar')
  }

  return (
    <section className="registro cuenta verificar-dni">
      <h2>Verificar mi DNI</h2>

      {paso === 'cargando' && <p role="status">Cargando…</p>}
      {paso === 'eliminacion' && <div role="alert"><p>Tu captura está bloqueada por una solicitud de eliminación. No se enviarán nuevas fotografías.</p><Link to="/eliminar-datos">Consultar eliminación</Link></div>}
      {paso === 'error' && (
        <div>
          <p className="error caja" role="alert">{error}</p>
          <button type="button" onClick={reintentar}>Reintentar consulta</button>
          <Link to="/consentimiento">Consultar consentimiento</Link>
        </div>
      )}

      {(paso === 'anverso' || paso === 'reverso') && (
        <>
          {error && <p className="error caja" role="alert">{error}</p>}
          <p className="ayuda">Paso {paso === 'anverso' ? 1 : 2} de 2: toma una foto {LADOS[paso]}.</p>
          <Camara key={paso} alCapturar={(blob) => guardarFoto(paso, blob)} />
        </>
      )}

      {(paso === 'enviar' || paso === 'enviando') && (
        <>
          <p className="ayuda">Revisa que se lea bien. Si no, repite la foto.</p>
          <div className="vistas">
            {['anverso', 'reverso'].map((lado) => (
              <figure key={lado}>
                <img src={fotos[lado].url} alt={`Foto del ${lado} del DNI`} />
                <figcaption>
                  {lado === 'anverso' ? 'Frente' : 'Reverso'}{' '}
                  <button type="button" onClick={() => repetir(lado)} disabled={paso === 'enviando'}>Repetir</button>
                </figcaption>
              </figure>
            ))}
          </div>
          {error && <p className="error caja" role="alert">{error}</p>}
          <button type="button" className="boton-principal" onClick={enviar} disabled={paso === 'enviando'}>
            {paso === 'enviando' ? 'Validando…' : 'Enviar para validar'}
          </button>
        </>
      )}

      {paso === 'resultado' && intento && <Resultado intento={intento} alReintentar={reintentar} />}
    </section>
  )
}

export default VerificarDni
