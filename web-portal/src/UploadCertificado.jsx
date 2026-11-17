import { useState } from 'react'
import { leerCookie } from './api.js'
import './UploadCertificado.css'

const MAX_FILE_SIZE = 5 * 1024 * 1024
const ALLOWED_TYPES = ['application/pdf', 'image/jpeg']

export default function UploadCertificado() {
  const [archivo, setArchivo] = useState(null)
  const [estadoCarga, setEstadoCarga] = useState('inicial')
  const [mensajeError, setMensajeError] = useState('')
  const [datosRespuesta, setDatosRespuesta] = useState(null)

  const manejarSeleccionArchivo = (evento) => {
    const file = evento.target.files?.[0] || null
    setMensajeError('')
    setDatosRespuesta(null)
    setEstadoCarga('inicial')

    if (!file) {
      setArchivo(null)
      return
    }

    if (file.size > MAX_FILE_SIZE) {
      setMensajeError('El archivo excede el límite permitido de 5 MB.')
      setArchivo(null)
      return
    }

    if (!ALLOWED_TYPES.includes(file.type)) {
      setMensajeError('Formato no válido. Solo se permiten archivos PDF o JPG.')
      setArchivo(null)
      return
    }

    setArchivo(file)
  }

  const enviarSolicitud = async (evento) => {
    evento.preventDefault()
    if (!archivo) {
      setMensajeError('Por favor selecciona un archivo válido antes de enviar.')
      return
    }

    setEstadoCarga('cargando')
    setMensajeError('')

    const formData = new FormData()
    formData.append('file', archivo)
    try {
      // S3-CERT-16: el certificado va al perfil de la sesión; el token CSRF es obligatorio.
      const respuesta = await fetch('/api/certificaciones/upload/', {
        method: 'POST',
        headers: { 'X-CSRFToken': leerCookie('csrftoken') },
        body: formData,
      })

      const resultado = await respuesta.json()

      if (respuesta.ok) {
        setDatosRespuesta(resultado)
        setEstadoCarga('exito')
      } else {
        setMensajeError(resultado.error || (respuesta.status === 403
          ? 'Tu sesión venció o no es de prestador. Vuelve a iniciar sesión.'
          : 'Ocurrió un error al procesar la solicitud.'))
        setEstadoCarga('error')
      }
    } catch {
      setMensajeError('No se pudo establecer conexión con el servidor.')
      setEstadoCarga('error')
    }
  }

  return (
    <main className="container-certificacion">
      <h2>Cargar certificado</h2>
      <p className="descripcion">Adjunta un documento PDF o JPG de hasta 5 MB. Se agrega a tu perfil de prestador.</p>

      <form onSubmit={enviarSolicitud} className="form-certificacion">
        <label htmlFor="archivo-certificado">Archivo del certificado</label>
        <input
          id="archivo-certificado"
          type="file"
          accept=".pdf,.jpg,.jpeg,application/pdf,image/jpeg"
          onChange={manejarSeleccionArchivo}
          required
        />

        <button type="submit" disabled={estadoCarga === 'cargando' || !archivo}>
          {estadoCarga === 'cargando' ? 'Enviando certificado…' : 'Enviar certificado'}
        </button>
      </form>

      {mensajeError && (
        <div className="certificacion-error" role="alert">
          {mensajeError}
        </div>
      )}

      {estadoCarga === 'exito' && datosRespuesta && (
        <section className="certificacion-exito" role="status">
          <h3>¡Certificado enviado!</h3>
          <p><strong>Archivo:</strong> {datosRespuesta.file_name}</p>
          <p><strong>Estado:</strong> {datosRespuesta.status}</p>
          <a href={datosRespuesta.file_url} target="_blank" rel="noopener noreferrer">
            Ver documento cargado
          </a>
        </section>
      )}
    </main>
  )
}