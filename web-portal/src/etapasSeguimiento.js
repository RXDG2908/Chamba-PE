// HU-11: etapas del servicio después de que el prestador acepta la solicitud.
export const ETAPAS = [
  { estado: 'aceptada', nombre: 'Confirmado' },
  { estado: 'en_camino', nombre: 'En camino' },
  { estado: 'en_servicio', nombre: 'En servicio' },
  { estado: 'finalizada', nombre: 'Finalizada' },
]
export const ESTADOS_FINALES = ['rechazada', 'finalizada']
export const ACCION_SIGUIENTE = {
  en_camino: 'Voy en camino',
  en_servicio: 'Empecé el servicio',
  finalizada: 'Finalizar servicio',
}
