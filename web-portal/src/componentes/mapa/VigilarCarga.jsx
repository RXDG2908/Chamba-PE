import { useEffect } from 'react'
import { APILoadingStatus, useApiLoadingStatus } from '@vis.gl/react-google-maps'

// Dentro de un APIProvider: avisa si Google rechazó la clave o no cargó.
export default function VigilarCarga({ onFallo }) {
  const estado = useApiLoadingStatus()
  useEffect(() => {
    if (estado === APILoadingStatus.AUTH_FAILURE || estado === APILoadingStatus.FAILED) onFallo?.()
  }, [estado, onFallo])
  return null
}
