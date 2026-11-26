import { Car, CircleCheckBig, CircleX, Handshake, Hourglass, Wrench } from 'lucide-react'

// Icono de cada estado de una solicitud (HU-4 y HU-11): el mismo en la web del cliente y del prestador.
const ICONOS = {
  pendiente: Hourglass,
  aceptada: Handshake,
  en_camino: Car,
  en_servicio: Wrench,
  finalizada: CircleCheckBig,
  rechazada: CircleX,
}

export default function IconoEstado({ estado, size = 15 }) {
  const Icono = ICONOS[estado]
  return Icono ? <Icono className="icono" size={size} aria-hidden="true" /> : null
}
