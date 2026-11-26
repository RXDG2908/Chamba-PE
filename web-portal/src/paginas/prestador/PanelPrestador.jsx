import { Link } from 'react-router-dom'
import useConsultas from '../../nucleo/useConsultas.js'
import '../../estilos/Paneles.css'
import { Inbox, ShieldCheck, ToggleRight } from 'lucide-react'
import { formatoEntero } from '../../nucleo/formatos.js'

const EN_CURSO = ['aceptada', 'en_camino', 'en_servicio']

function textoVerificacion(consentimiento, dni, rostro) {
  if (rostro.datos?.decision === 'verificado') return ['Identidad verificada', 'ok']
  if (dni.datos?.resultado === 'validado') return ['Falta comparar tu rostro', 'pendiente']
  if (dni.datos) return ['DNI en revisión manual', 'pendiente']
  if (consentimiento.datos?.vigente) return ['Falta escanear tu DNI', 'pendiente']
  return ['Empieza aceptando el consentimiento', 'pendiente']
}

// Inicio del prestador: lo que tiene que atender hoy y lo que le falta para que confíen en él.
export default function PanelPrestador({ usuario }) {
  const { disponibilidad, solicitudes, consentimiento, dni, rostro } = useConsultas({
    disponibilidad: '/api/usuarios/disponibilidad/',
    solicitudes: '/api/servicios/solicitudes/recibidas/',
    consentimiento: '/api/verificacion/consentimiento/',
    dni: '/api/verificacion/dni/',
    rostro: '/api/verificacion/facial/',
  })
  const lista = Array.isArray(solicitudes.datos) ? solicitudes.datos : []
  const pendientes = lista.filter((s) => s.estado === 'pendiente').length
  const enCurso = lista.filter((s) => EN_CURSO.includes(s.estado)).length
  const cargando = [disponibilidad, solicitudes, consentimiento, dni, rostro].some((c) => c.estado === 'cargando')
  const [verificacion, tonoVerificacion] = textoVerificacion(consentimiento, dni, rostro)
  const activo = disponibilidad.datos?.disponible
  const verificado = disponibilidad.datos?.estado === 'verificado'

  return (
    <main className="panel contenedor" aria-busy={cargando}>
      <header className="panel-cabecera">
        <p className="etiqueta">Mi panel</p>
        <h2>Hola{usuario?.nombres ? `, ${usuario.nombres}` : ''}</h2>
        <p>Atiende tus solicitudes y mantén tu perfil al día para recibir más trabajos.</p>
      </header>

      {!verificado && disponibilidad.estado === 'listo' && (
        <p className="panel-aviso" role="note">
          Administración todavía no verificó tu perfil: aún no apareces en la búsqueda.
          Sube un certificado y completa la verificación para acelerar la revisión.
        </p>
      )}

      <section className="panel-tarjetas" aria-label="Resumen">
        <Link to="/solicitudes" className="panel-tarjeta">
          <span className="panel-tarjeta-icono" aria-hidden="true"><Inbox size={20} /></span>
          <span className="panel-dato">{cargando ? '—' : formatoEntero.format(pendientes)}</span>
          <span className="panel-titulo">Solicitudes por responder</span>
          <span className="panel-detalle">{enCurso ? `${enCurso} en curso` : 'Ninguna en curso'}</span>
        </Link>
        <Link to="/disponibilidad" className="panel-tarjeta">
          <span className="panel-tarjeta-icono" aria-hidden="true"><ToggleRight size={20} /></span>
          <span className={`panel-estado ${activo ? 'ok' : 'apagado'}`}>{cargando ? '—' : activo ? 'Activo' : 'Inactivo'}</span>
          <span className="panel-titulo">Mi disponibilidad</span>
          <span className="panel-detalle">{activo ? 'Recibes solicitudes nuevas' : 'No recibes solicitudes nuevas'}</span>
        </Link>
        <Link to="/prestador/verificacion" className="panel-tarjeta">
          <span className="panel-tarjeta-icono" aria-hidden="true"><ShieldCheck size={20} /></span>
          <span className={`panel-estado ${tonoVerificacion}`}>{cargando ? '—' : verificacion}</span>
          <span className="panel-titulo">Verificación de identidad</span>
          <span className="panel-detalle">DNI, rostro y certificados</span>
        </Link>
      </section>

      {[disponibilidad, solicitudes].some((c) => c.estado === 'fallo') && (
        <p className="error-busqueda" role="alert">No pudimos cargar parte de tu panel. Recarga la página.</p>
      )}
    </main>
  )
}
