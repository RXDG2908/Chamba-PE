import { LoaderCircle } from 'lucide-react'

// Mensaje de carga con un indicador que gira (rápido: hace que la espera se sienta más corta).
// Conserva el texto de siempre, así que lectores de pantalla y pruebas lo siguen encontrando.
export default function Cargando({ texto, className = '', anunciar = true }) {
  return (
    <p className={`cargando ${className}`.trim()} role={anunciar ? 'status' : undefined}>
      <LoaderCircle className="icono girando" size={18} aria-hidden="true" />
      {texto}
    </p>
  )
}
