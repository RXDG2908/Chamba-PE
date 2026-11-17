import { useEffect, useState } from 'react'
import { enviarJson } from './api.js'
import './RegistroPrestador.css'
import './Cuenta.css'

const AVISO_VERIFICACION = {
  pendiente: 'Tu perfil está pendiente de verificación: aparecerás en las búsquedas cuando administración lo apruebe.',
  rechazado: 'Tu verificación fue rechazada: sube un nuevo documento para que administración vuelva a revisarte.',
}

function Disponibilidad() {
  const [datos, setDatos] = useState(null)
  const [estado, setEstado] = useState('cargando') // cargando | listo | guardando | fallo

  useEffect(() => {
    fetch('/api/usuarios/disponibilidad/')
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then((cuerpo) => {
        setDatos(cuerpo)
        setEstado('listo')
      })
      .catch(() => setEstado('fallo'))
  }, [])

  const alternar = async () => {
    setEstado('guardando')
    try {
      const respuesta = await enviarJson('/api/usuarios/disponibilidad/', 'PATCH', { disponible: !datos.disponible })
      if (!respuesta.ok) throw new Error()
      setDatos(await respuesta.json())
      setEstado('listo')
    } catch {
      setEstado('fallo')
    }
  }

  if (estado === 'cargando') return <p className="registro cuenta" role="status">Cargando tu disponibilidad…</p>
  if (!datos) {
    return <p className="registro cuenta error" role="alert">No pudimos cargar tu disponibilidad. Recarga la página.</p>
  }

  return (
    <section className="registro cuenta disponibilidad">
      <h2>Mi disponibilidad</h2>
      <p className="ayuda">Activa tu disponibilidad cuando estés listo para recibir solicitudes.</p>

      <label className={`interruptor ${datos.disponible ? 'activo' : ''}`}>
        <input
          type="checkbox"
          role="switch"
          checked={datos.disponible}
          onChange={alternar}
          disabled={estado === 'guardando'}
        />
        <span className="pista" aria-hidden="true" />
        <span className="texto-interruptor">{datos.disponible ? 'Activo' : 'Inactivo'}</span>
      </label>

      <p className="resumen-disponibilidad" role="status">
        {!datos.disponible && 'No apareces en las búsquedas ni recibes solicitudes.'}
        {datos.disponible && datos.estado === 'verificado' && 'Apareces en las búsquedas y puedes recibir solicitudes.'}
        {datos.disponible && datos.estado !== 'verificado' && 'Quedas disponible, pero aún no recibes solicitudes.'}
      </p>
      {AVISO_VERIFICACION[datos.estado] && <p className="caja aviso-verificacion">{AVISO_VERIFICACION[datos.estado]}</p>}
      {estado === 'fallo' && (
        <p className="error caja" role="alert">No se pudo guardar el cambio. Inténtalo de nuevo.</p>
      )}
    </section>
  )
}

export default Disponibilidad
