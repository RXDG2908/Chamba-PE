import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { enviarJson, erroresPorCampo, leerCookie } from '../../nucleo/api.js'
import { cargarDetector, nuevoDetectorDeParpadeo, puntajeDe } from '../../nucleo/pruebaDeVida.js'
import { DECISIONES, MOTIVOS_FACIALES } from '../../nucleo/textosFaciales.js'
import '../publico/RegistroPrestador.css'
import '../../estilos/Cuenta.css'
import './VerificarDni.css'
import './VerificarRostro.css'
import { Check, RotateCcw, ScanFace } from 'lucide-react'

const API = '/api/verificacion/facial/'
const API_CONSENTIMIENTO = '/api/verificacion/facial/consentimiento/'

const PASOS = {
  cargando: 'Preparando la cámara y la prueba de vida…',
  sin_rostro: 'Pon tu rostro dentro del óvalo.',
  varios: 'Debe haber una sola persona frente a la cámara.',
  esperando_abiertos: 'Mira a la cámara con los ojos abiertos.',
  abiertos: 'Ahora parpadea una vez.',
  cerrados: 'Abre los ojos…',
  parpadeo: '¡Listo! Tomando la selfie…',
}

function mensajeDeError(estado, cuerpo) {
  const porCampo = erroresPorCampo(cuerpo)
  if (estado === 400) return porCampo.parpadeo || porCampo.selfie || 'Revisa la selfie e inténtalo de nuevo.'
  if (estado === 401 || (estado === 403 && !porCampo.detail)) return 'Tu sesión terminó. Vuelve a iniciar sesión.'
  if ([403, 409, 429].includes(estado)) return porCampo.detail || 'No se pudo comparar tu rostro.'
  if (estado >= 500) return 'El servidor tuvo un problema. Inténtalo de nuevo en unos minutos.'
  return 'No pudimos enviar la selfie. Inténtalo de nuevo.'
}

// Selfie en vivo: solo cámara frontal (no galería) y solo después de un parpadeo.
function SelfieEnVivo({ alCapturar }) {
  const video = useRef(null)
  const [paso, setPaso] = useState('cargando')
  const [error, setError] = useState('')

  useEffect(() => {
    let activo = true
    let flujo = null
    let detector = null
    let cuadro = 0
    const parpadeo = nuevoDetectorDeParpadeo()

    const capturar = () => {
      const v = video.current
      const lienzo = document.createElement('canvas')
      lienzo.width = v.videoWidth
      lienzo.height = v.videoHeight
      lienzo.getContext('2d').drawImage(v, 0, 0)
      lienzo.toBlob((blob) => activo && blob && alCapturar(blob), 'image/jpeg', 0.92)
    }

    const analizar = () => {
      if (!activo) return
      const v = video.current
      if (v && v.readyState >= 2) {
        const resultado = detector.detectForVideo(v, performance.now())
        const rostros = resultado.faceBlendshapes ?? []
        if (rostros.length === 0) {
          parpadeo.reiniciar()
          setPaso('sin_rostro')
        } else if (rostros.length > 1) {
          parpadeo.reiniciar()
          setPaso('varios')
        } else {
          const formas = rostros[0].categories
          const listo = parpadeo.actualizar(puntajeDe(formas, 'eyeBlinkLeft'), puntajeDe(formas, 'eyeBlinkRight'))
          setPaso(parpadeo.fase)
          if (listo) {
            // Un instante después, con los ojos ya abiertos.
            setTimeout(() => activo && capturar(), 250)
            return
          }
        }
      }
      cuadro = requestAnimationFrame(analizar)
    }

    const iniciar = async () => {
      if (!navigator.mediaDevices?.getUserMedia) {
        throw new Error('Este navegador no permite usar la cámara. Usa Chrome o Edge en tu celular.')
      }
      try {
        flujo = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false })
      } catch {
        throw new Error('Necesitamos permiso para usar la cámara frontal. Actívalo en tu navegador y vuelve a intentar.')
      }
      if (!activo) return
      video.current.srcObject = flujo
      await video.current.play()
      try {
        detector = await cargarDetector()
      } catch {
        throw new Error('No se pudo cargar la prueba de vida en este navegador.')
      }
      if (!activo) return
      setPaso('esperando_abiertos')
      cuadro = requestAnimationFrame(analizar)
    }

    iniciar().catch((e) => activo && setError(e.message))
    return () => {
      activo = false
      cancelAnimationFrame(cuadro)
      flujo?.getTracks().forEach((t) => t.stop())
      detector?.close()
    }
  }, [alCapturar])

  return (
    <div className="camara">
      <div className="visor visor-selfie">
        <video ref={video} playsInline muted aria-label="Vista de la cámara frontal" />
        <div className="marco-rostro" aria-hidden="true" />
      </div>
      {error
        ? <p className="error caja" role="alert">{error}</p>
        : <p className="paso-vida" role="status" aria-live="polite">{PASOS[paso]}</p>}
      <p className="ayuda">Busca buena luz, sin lentes de sol ni gorra. La foto se toma sola cuando parpadeas.</p>
    </div>
  )
}

function Resultado({ intento, alRepetir }) {
  const decision = DECISIONES[intento.decision] ?? DECISIONES.revision_manual
  return (
    <div className="resultado" role="status">
      <h3 className={decision.clase}>{decision.titulo}</h3>
      {intento.porcentaje !== null && <p className="similitud">Similitud con la foto de tu DNI: <strong>{intento.porcentaje} %</strong></p>}
      {intento.revision && <p>El administrador lo marcó como <strong>{intento.revision}</strong>.</p>}
      {intento.motivos.length > 0 && (
        <ul className="motivos">
          {intento.motivos.map((m) => <li key={m}>{MOTIVOS_FACIALES[m] ?? m}</li>)}
        </ul>
      )}
      <p className="ayuda">
        Esto no aprueba tu cuenta por sí solo: el administrador decide en la revisión.
        {' '}Registrado el {new Date(intento.creado).toLocaleString('es-PE')}
      </p>
      {intento.decision !== 'verificado' && (
        <button type="button" className="boton-principal" onClick={alRepetir}><RotateCcw className="icono" size={18} aria-hidden="true" />Intentar de nuevo</button>
      )}
    </div>
  )
}

function Consentimiento({ estado, alAceptar }) {
  const [marcado, setMarcado] = useState(false)
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  const aceptar = async () => {
    setEnviando(true)
    setError('')
    const respuesta = await enviarJson(API_CONSENTIMIENTO, 'POST', { acepto: true, version_texto: estado.version_texto })
      .catch(() => null)
    setEnviando(false)
    if (respuesta?.ok) alAceptar(await respuesta.json())
    else setError('No pudimos guardar tu aceptación. Inténtalo de nuevo.')
  }

  return (
    <div className="consentimiento-facial">
      <h3>Antes de empezar</h3>
      {estado.estado === 'revocado' && <p className="ayuda">Revocaste tu aceptación anterior; para comparar de nuevo debes aceptar otra vez.</p>}
      <p className="texto-consentimiento">{estado.texto}</p>
      <label className="casilla">
        <input type="checkbox" checked={marcado} onChange={(e) => setMarcado(e.target.checked)} />
        Leí y acepto el uso de mi selfie para esta comparación.
      </label>
      {error && <p className="error caja" role="alert">{error}</p>}
      <button type="button" className="boton-principal" disabled={!marcado || enviando} onClick={aceptar}>
        <Check className="icono" size={18} aria-hidden="true" />
        {enviando ? 'Guardando…' : 'Aceptar y continuar'}
      </button>
    </div>
  )
}

export default function VerificarRostro() {
  const [fase, setFase] = useState('cargando') // cargando | error | consentimiento | selfie | enviando | resultado
  const [consentimiento, setConsentimiento] = useState(null)
  const [intento, setIntento] = useState(null)
  const [error, setError] = useState('')
  const [borrado, setBorrado] = useState('')

  useEffect(() => {
    let activo = true
    Promise.all([
      fetch(API_CONSENTIMIENTO).then((r) => (r.ok ? r.json() : Promise.reject(r.status))),
      fetch(API).then((r) => (r.status === 404 ? null : r.ok ? r.json() : Promise.reject(r.status))),
    ])
      .then(([estado, ultimo]) => {
        if (!activo) return
        setConsentimiento(estado)
        setIntento(ultimo)
        setFase(!estado.vigente ? 'consentimiento' : ultimo ? 'resultado' : 'selfie')
      })
      .catch((estado) => {
        if (!activo) return
        setError(estado === 403 ? 'Esta pantalla es solo para prestadores con sesión iniciada.' : 'No pudimos cargar tu verificación. Revisa tu conexión.')
        setFase('error')
      })
    return () => {
      activo = false
    }
  }, [])

  const enviar = useCallback(async (blob) => {
    setFase('enviando')
    setError('')
    const datos = new FormData()
    datos.append('selfie', blob, 'selfie.jpg')
    datos.append('parpadeo', 'true')
    try {
      const respuesta = await fetch(API, { method: 'POST', headers: { 'X-CSRFToken': leerCookie('csrftoken') }, body: datos })
      const cuerpo = await respuesta.json().catch(() => ({}))
      if (!respuesta.ok) {
        setError(mensajeDeError(respuesta.status, cuerpo))
        setFase(respuesta.status === 403 && cuerpo.detail?.includes('aceptar') ? 'consentimiento' : 'selfie')
        return
      }
      setIntento(cuerpo)
      setFase('resultado')
    } catch {
      setError('No pudimos enviar la selfie. Revisa tu conexión.')
      setFase('selfie')
    }
  }, [])

  const borrarSelfies = async () => {
    if (!window.confirm('¿Borrar todas tus selfies? Se conservan solo el porcentaje, la decisión y la fecha.')) return
    const respuesta = await enviarJson(`${API}eliminar/`, 'POST', { confirmo: true }).catch(() => null)
    const cuerpo = await respuesta?.json().catch(() => ({}))
    setBorrado(respuesta?.ok ? `Se borraron ${cuerpo.borradas} selfie(s).` : 'No pudimos borrar todas tus selfies. Inténtalo de nuevo.')
  }

  const revocar = async () => {
    const respuesta = await enviarJson(`${API_CONSENTIMIENTO}revocar/`, 'POST').catch(() => null)
    if (respuesta?.ok) {
      setConsentimiento(await respuesta.json())
      setFase('consentimiento')
    }
  }

  return (
    <section className="registro cuenta verificar-dni verificar-rostro">
      <p className="etiqueta">VERIFICACIÓN DE IDENTIDAD</p>
      <h2><span className="icono-titulo" aria-hidden="true"><ScanFace size={22} /></span>Selfie y comparación con tu DNI</h2>
      <p>Comparamos tu rostro con la foto de tu DNI para confirmar que eres el titular. Primero debes <Link to="/verificar-dni">verificar tu DNI</Link>.</p>

      {fase === 'cargando' && <p className="ayuda" role="status">Cargando…</p>}
      {fase === 'error' && <p className="error caja" role="alert">{error}</p>}
      {fase === 'consentimiento' && consentimiento && (
        <Consentimiento estado={consentimiento} alAceptar={(estado) => {
          setConsentimiento(estado)
          setFase(intento ? 'resultado' : 'selfie')
        }} />
      )}
      {fase === 'selfie' && (
        <>
          {error && <p className="error caja" role="alert">{error}</p>}
          <SelfieEnVivo alCapturar={enviar} />
        </>
      )}
      {fase === 'enviando' && <p className="ayuda" role="status">Comparando tu rostro con la foto de tu DNI…</p>}
      {fase === 'resultado' && intento && <Resultado intento={intento} alRepetir={() => { setError(''); setFase('selfie') }} />}

      {consentimiento?.vigente && ['selfie', 'resultado'].includes(fase) && (
        <div className="datos-faciales">
          <h3>Tus datos</h3>
          <p className="ayuda">Puedes borrar tus selfies o dejar de autorizar nuevas comparaciones cuando quieras.</p>
          <div className="acciones">
            <button type="button" onClick={borrarSelfies}>Borrar mis selfies</button>
            <button type="button" onClick={revocar}>Revocar mi aceptación</button>
          </div>
          {borrado && <p role="status">{borrado}</p>}
        </div>
      )}
    </section>
  )
}
