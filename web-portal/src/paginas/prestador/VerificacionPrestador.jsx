import { Link } from 'react-router-dom'
import useConsultas from '../../nucleo/useConsultas.js'
import '../../estilos/Paneles.css'

// Resumen de la verificación: los pasos en orden, con su estado y un enlace a cada uno.
export default function VerificacionPrestador() {
  const { consentimiento, dni, rostro } = useConsultas({
    consentimiento: '/api/verificacion/consentimiento/',
    dni: '/api/verificacion/dni/',
    rostro: '/api/verificacion/facial/',
  })
  const cargando = [consentimiento, dni, rostro].some((c) => c.estado === 'cargando')
  const textoDni = { validado: 'DNI validado', revision_manual: 'En revisión manual' }
  const textoRostro = { verificado: 'Rostro verificado', revision_manual: 'En revisión manual', rechazado: 'No coincidió: repite la selfie' }

  const pasos = [
    {
      ruta: '/consentimiento', titulo: 'Acepta el uso de tus datos',
      detalle: 'Autoriza el tratamiento de las fotos de tu DNI. Puedes revocarlo cuando quieras.',
      estado: consentimiento.datos?.vigente ? ['Aceptado', 'ok'] : ['Pendiente', 'pendiente'],
    },
    {
      ruta: '/verificar-dni', titulo: 'Escanea tu DNI',
      detalle: 'Foto del frente y del reverso. Leemos tu número y nombres automáticamente.',
      estado: dni.datos ? [textoDni[dni.datos.resultado] ?? 'Enviado', dni.datos.resultado === 'validado' ? 'ok' : 'pendiente'] : ['Pendiente', 'pendiente'],
    },
    {
      ruta: '/verificar-rostro', titulo: 'Tómate una selfie',
      detalle: 'Parpadea frente a la cámara: comparamos tu rostro con la foto de tu DNI.',
      estado: rostro.datos ? [textoRostro[rostro.datos.decision] ?? 'Enviado', rostro.datos.decision === 'verificado' ? 'ok' : 'pendiente'] : ['Pendiente', 'pendiente'],
    },
    {
      ruta: '/certificaciones', titulo: 'Sube tus certificados',
      detalle: 'PDF o JPG de hasta 5 MB. Administración los revisa antes de mostrarte en la búsqueda.',
      estado: null,
    },
  ]

  return (
    <main className="panel contenedor" aria-busy={cargando}>
      <header className="panel-cabecera">
        <p className="etiqueta">Verificación</p>
        <h2>Demuestra que eres tú</h2>
        <p>Los clientes eligen antes a prestadores verificados. Completa los pasos en este orden.</p>
      </header>
      <ol className="pasos-verificacion">
        {pasos.map((paso, i) => (
          <li key={paso.ruta} className="verif-paso">
            <span className="verif-paso-numero" aria-hidden="true">{i + 1}</span>
            <div>
              <h3><Link to={paso.ruta}>{paso.titulo}</Link></h3>
              <p>{paso.detalle}</p>
            </div>
            {paso.estado && <span className={`panel-estado ${paso.estado[1]}`}>{cargando ? '—' : paso.estado[0]}</span>}
          </li>
        ))}
      </ol>
      <p className="panel-privacidad">
        ¿Quieres borrar tus fotos y datos del DNI? <Link to="/eliminar-datos">Solicita la eliminación</Link>.
      </p>
    </main>
  )
}
