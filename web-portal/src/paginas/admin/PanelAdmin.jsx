import { Link } from 'react-router-dom'
import useConsultas from '../../nucleo/useConsultas.js'
import '../../estilos/Paneles.css'
import { FileCheck2, IdCard, ScanFace, UserX } from 'lucide-react'

function cantidad(consulta) {
  if (consulta.estado === 'cargando') return '—'
  return Array.isArray(consulta.datos) ? consulta.datos.length : '?'
}

// Inicio de administración: las colas que esperan una decisión.
export default function PanelAdmin({ usuario }) {
  const c = useConsultas({
    documentos: '/api/administracion/revisiones/?estado=pendiente',
    dni: '/api/verificacion/dni/?resultado=revision_manual',
    rostros: '/api/verificacion/facial/?decision=revision_manual',
    suspendidas: '/api/administracion/cuentas/?estado=suspendida',
    bloqueadas: '/api/administracion/cuentas/?estado=bloqueada',
  })
  const cargando = Object.values(c).some((x) => x.estado === 'cargando')
  const tarjetas = [
    ['/administracion/revisiones', c.documentos, 'Certificados por revisar', 'Aprueba o rechaza con motivo', FileCheck2],
    ['/administracion/revisiones', c.dni, 'DNI en revisión manual', 'El sistema no pudo validarlos solo', IdCard],
    ['/administracion/revisiones', c.rostros, 'Rostros en duda', 'Compara la selfie con la foto del DNI', ScanFace],
    ['/administracion/cuentas', c.suspendidas, 'Cuentas suspendidas', `${cantidad(c.bloqueadas)} bloqueadas`, UserX],
  ]
  return (
    <main className="panel contenedor" aria-busy={cargando}>
      <header className="panel-cabecera">
        <p className="etiqueta">Administración</p>
        <h2>Hola{usuario?.nombres ? `, ${usuario.nombres}` : ''}</h2>
        <p>Esto es lo que espera tu decisión. Los prestadores solo aparecen en la búsqueda cuando los apruebas.</p>
      </header>
      <section className="panel-tarjetas" aria-label="Pendientes">
        {tarjetas.map(([ruta, consulta, titulo, detalle, Icono]) => (
          <Link key={titulo} to={ruta} className="panel-tarjeta">
            <span className="panel-tarjeta-icono" aria-hidden="true"><Icono size={20} /></span>
            <span className="panel-dato">{cantidad(consulta)}</span>
            <span className="panel-titulo">{titulo}</span>
            <span className="panel-detalle">{detalle}</span>
          </Link>
        ))}
      </section>
      {Object.values(c).some((x) => x.estado === 'fallo') && (
        <p className="error-busqueda" role="alert">No pudimos cargar parte del panel. Recarga la página.</p>
      )}
    </main>
  )
}
