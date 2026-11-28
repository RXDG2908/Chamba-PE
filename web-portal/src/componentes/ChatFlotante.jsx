import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Copy, MessageCircle, Minus, Phone, Send, X } from 'lucide-react'
import { enviarJson } from '../nucleo/api.js'
import './ChatFlotante.css'

const hora = new Intl.DateTimeFormat('es-PE', { hour: '2-digit', minute: '2-digit' })

// Celular o tablet (pantalla táctil): ahí «Llamar» abre el marcador. En la PC no hay marcador,
// así que solo se muestra el número para copiarlo y marcarlo desde el teléfono.
function esTactil() {
  try { return window.matchMedia('(pointer: coarse)').matches } catch { return false }
}

function chatValido(c) {
  return c && Number.isInteger(c.solicitud) && typeof c.activo === 'boolean' && Array.isArray(c.mensajes)
    && c.contraparte && typeof c.contraparte.nombre === 'string'
}

// Botón para abrir el chat desde la tarjeta de una solicitud.
export function BotonChat({ alAbrir }) {
  return <button type="button" className="boton-secundario boton-chat" onClick={alAbrir}>
    <MessageCircle className="icono" size={18} aria-hidden="true" />Chat
  </button>
}

/**
 * Ventana flotante abajo a la derecha, como la de Messenger: el cliente y el prestador coordinan.
 * Se actualiza sola cada 5 s mientras está abierta. El ícono del teléfono muestra el número con
 * «Copiar»; en el celular, además, «Llamar» abre el marcador.
 */
export default function ChatFlotante({ solicitudId, titulo, alCerrar, alVencer }) {
  const [chat, setChat] = useState(null)
  const [error, setError] = useState('')
  const [texto, setTexto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [minimizado, setMinimizado] = useState(false)
  const [verTelefono, setVerTelefono] = useState(false)
  const [copiado, setCopiado] = useState(false)
  const [revision, setRevision] = useState(0)
  const lista = useRef(null)
  const ruta = `/api/servicios/solicitudes/${solicitudId}/chat/`

  useEffect(() => {
    if (minimizado) return undefined
    const controlador = new AbortController()
    const consultar = () => fetch(ruta, { signal: controlador.signal, cache: 'no-store' })
      .then(async (r) => {
        if (r.status === 403) { alVencer?.(null); return }
        const cuerpo = await r.json().catch(() => null)
        if (!r.ok) throw new Error(cuerpo?.chat || 'No se pudo abrir el chat.')
        if (!chatValido(cuerpo)) throw new Error('No se pudo abrir el chat.')
        setChat(cuerpo)
        setError('')
      })
      .catch((e) => { if (!controlador.signal.aborted) setError(e.message) })
    consultar()
    const temporizador = setInterval(consultar, 5000)
    return () => { controlador.abort(); clearInterval(temporizador) }
  }, [ruta, minimizado, revision, alVencer])

  // Baja al último mensaje cuando llega uno nuevo.
  const cantidad = chat?.mensajes.length ?? 0
  useEffect(() => {
    if (lista.current) lista.current.scrollTop = lista.current.scrollHeight
  }, [cantidad, minimizado])

  async function enviar(evento) {
    evento.preventDefault()
    if (!texto.trim() || enviando) return
    setEnviando(true)
    try {
      const r = await enviarJson(ruta, 'POST', { texto })
      if (r.status === 403) { alVencer?.(null); return }
      const cuerpo = await r.json().catch(() => null)
      if (!r.ok) {
        setError(cuerpo?.chat || cuerpo?.texto?.[0] || 'No se pudo enviar el mensaje.')
        setRevision((n) => n + 1)
        return
      }
      setChat((actual) => actual && !actual.mensajes.some((m) => m.id === cuerpo.id)
        ? { ...actual, mensajes: [...actual.mensajes, cuerpo] } : actual)
      setTexto('')
      setError('')
    } catch {
      setError('No pudimos comunicarnos con el servidor. Inténtalo de nuevo.')
    } finally {
      setEnviando(false)
    }
  }

  async function copiar() {
    try {
      await navigator.clipboard.writeText(chat.contraparte.telefono)
      setCopiado(true)
      setTimeout(() => setCopiado(false), 2000)
    } catch { /* Sin permiso del portapapeles: el número sigue a la vista. */ }
  }

  const nombre = chat?.contraparte.nombre || titulo
  const telefono = chat?.contraparte.telefono
  // En el body: así queda fija en la pantalla aunque la página tenga animaciones con transform.
  return createPortal(<section className={`chat-flotante${minimizado ? ' minimizado' : ''}`} aria-label={`Chat con ${nombre}`}>
    <header className="chat-cabecera">
      <button type="button" className="chat-titulo" onClick={() => setMinimizado((m) => !m)}
        aria-expanded={!minimizado}>
        <span className="chat-avatar" aria-hidden="true">{nombre.trim().charAt(0).toUpperCase() || '?'}</span>
        <span className="chat-nombre">
          <strong>{nombre}</strong>
          <small>{chat ? `Solicitud #${chat.solicitud} · ${chat.estado_display}` : 'Conectando…'}</small>
        </span>
      </button>
      {telefono && <button type="button" className="chat-icono" onClick={() => { setMinimizado(false); setVerTelefono((v) => !v) }}
        aria-label={`Teléfono de ${nombre}`} aria-expanded={verTelefono} title="Teléfono">
        <Phone size={18} aria-hidden="true" />
      </button>}
      <button type="button" className="chat-icono" onClick={() => setMinimizado((m) => !m)}
        aria-label={minimizado ? 'Abrir chat' : 'Minimizar chat'} title={minimizado ? 'Abrir' : 'Minimizar'}>
        <Minus size={18} aria-hidden="true" />
      </button>
      <button type="button" className="chat-icono" onClick={alCerrar} aria-label="Cerrar chat" title="Cerrar">
        <X size={18} aria-hidden="true" />
      </button>
    </header>
    {!minimizado && <>
      {telefono && verTelefono && <div className="chat-telefono">
        <span>{telefono}</span>
        {esTactil() && <a className="boton boton-principal" href={`tel:${telefono}`}><Phone className="icono" size={16} aria-hidden="true" />Llamar</a>}
        <button type="button" className="boton-secundario" onClick={copiar}>
          <Copy className="icono" size={16} aria-hidden="true" />{copiado ? 'Copiado' : 'Copiar'}
        </button>
      </div>}
      <ol className="chat-mensajes" ref={lista} aria-live="polite">
        {chat?.mensajes.length === 0 && <li className="chat-vacio">
          Escribe para coordinar la hora, la dirección o lo que necesites.</li>}
        {chat?.mensajes.map((m) => <li key={m.id} className={`chat-burbuja ${m.de === chat.yo ? 'mia' : 'suya'}`}>
          <span className="solo-lector">{m.de === chat.yo ? 'Tú' : nombre}: </span>
          <p>{m.texto}</p>
          <time dateTime={m.enviado}>{hora.format(new Date(m.enviado))}</time>
        </li>)}
      </ol>
      {error && <p className="chat-error" role="alert">{error}</p>}
      {chat && (chat.activo
        ? <form className="chat-escribir" onSubmit={enviar}>
          <input value={texto} onChange={(e) => setTexto(e.target.value)} maxLength={500} placeholder="Escribe un mensaje"
            aria-label="Mensaje" disabled={enviando} />
          <button type="submit" className="chat-icono enviar" disabled={enviando || !texto.trim()} aria-label="Enviar">
            <Send size={18} aria-hidden="true" />
          </button>
        </form>
        : <p className="chat-cerrado">El servicio finalizó; el chat queda solo para leer.</p>)}
    </>}
  </section>, document.body)
}
