import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { enviarJson, erroresPorCampo } from './api.js'
import './RegistroPrestador.css'
import './Cuenta.css'
import './Consentimiento.css'

export default function Consentimiento() {
  const [datos, setDatos] = useState(null)
  const [acepto, setAcepto] = useState(false)
  const [ocupado, setOcupado] = useState(false)
  const [confirmarRevocacion, setConfirmarRevocacion] = useState(false)
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    const abort = new AbortController()
    fetch('/api/verificacion/consentimiento/', { signal: abort.signal })
      .then(async (r) => {
        if (!r.ok) throw new Error('No pudimos consultar tu consentimiento. Revisa tu sesión e inténtalo de nuevo.')
        return r.json()
      })
      .then(setDatos)
      .catch((e) => { if (e.name !== 'AbortError') setError(e.message) })
    return () => abort.abort()
  }, [revision])

  const guardar = async (revocar = false) => {
    setOcupado(true)
    setError('')
    setAviso('')
    try {
      const r = await enviarJson(
        revocar ? '/api/verificacion/consentimiento/revocar/' : '/api/verificacion/consentimiento/',
        'POST',
        revocar ? undefined : { acepto: true, version_texto: datos.version_texto },
      )
      const cuerpo = await r.json().catch(() => ({}))
      if (!r.ok) {
        const mensajes = erroresPorCampo(cuerpo)
        throw new Error(mensajes.version_texto || mensajes.acepto || mensajes.detail || 'No pudimos guardar el cambio.')
      }
      setDatos(cuerpo)
      setAcepto(false)
      setConfirmarRevocacion(false)
      setAviso(revocar ? 'Consentimiento revocado. Nuevos procesamientos del DNI están bloqueados.' : 'Consentimiento registrado correctamente.')
    } catch (e) {
      setError(e.message || 'No hay conexión con el servidor.')
    } finally {
      setOcupado(false)
    }
  }

  const consultar = () => {
    setError('')
    setAviso('')
    setDatos(null)
    setAcepto(false)
    setConfirmarRevocacion(false)
    setRevision((r) => r + 1)
  }

  return (
    <section className="registro cuenta consentimiento">
      <h2>Consentimiento para verificar mi DNI</h2>
      <p><Link to="/eliminar-datos">Solicitar o consultar la eliminación de datos del DNI</Link></p>
      <p>Decide si autorizas el tratamiento de las fotografías de tu DNI antes de capturarlas.</p>
      <p className="aviso-provisional">Demostración: texto provisional pendiente de aprobación del PO. No es un consentimiento legal definitivo.</p>
      {error && <p className="error caja" role="alert">{error}</p>}
      {aviso && <p className="confirmacion caja" role="status">{aviso}</p>}
      {!datos && !error && <p role="status">Consultando consentimiento…</p>}
      {datos && (
        <>
          <dl className="detalle-consentimiento">
            <dt>Finalidad</dt><dd>Verificación del DNI</dd>
            <dt>Versión</dt><dd>{datos.version_texto}</dd>
            <dt>Estado</dt><dd>{({ vigente: 'Vigente', ausente: 'Sin aceptación', revocado: 'Revocado', desactualizado: 'Versión anterior' })[datos.estado]}</dd>
          </dl>
          <div className="texto-consentimiento" aria-label="Texto de consentimiento">{datos.texto}</div>
          {!datos.vigente ? (
            <form onSubmit={(e) => { e.preventDefault(); if (acepto && !ocupado) guardar() }}>
              <label className="aceptacion-explicita">
                <input type="checkbox" checked={acepto} onChange={(e) => setAcepto(e.target.checked)} disabled={ocupado} />
                <span>He leído y acepto este texto para la verificación de mi DNI.</span>
              </label>
              <button className="boton-principal" type="submit" disabled={!acepto || ocupado}>
                {ocupado ? 'Guardando…' : 'Aceptar consentimiento'}
              </button>
            </form>
          ) : (
            <>
              <Link className="continuar-dni" to="/verificar-dni">Continuar a verificar mi DNI</Link>
              {!confirmarRevocacion ? (
                <button type="button" onClick={() => setConfirmarRevocacion(true)}>Revocar consentimiento</button>
              ) : (
                <div className="confirmar-revocacion caja" role="group" aria-label="Confirmar revocación">
                  <p>¿Revocar el consentimiento? Se bloquearán nuevos procesamientos. Las imágenes existentes no se eliminan en esta operación.</p>
                  <button type="button" disabled={ocupado} onClick={() => guardar(true)}>Confirmar revocación</button>
                  <button type="button" disabled={ocupado} onClick={() => setConfirmarRevocacion(false)}>Cancelar</button>
                </div>
              )}
            </>
          )}
        </>
      )}
      <button className="consultar-consentimiento" type="button" disabled={ocupado} onClick={consultar}>Consultar estado actual</button>
      <p className="ayuda">Esta finalidad no autoriza reconocimiento facial. Puedes solicitar la eliminación por separado; tu cuenta y el DNI registrado se conservan.</p>
    </section>
  )
}
