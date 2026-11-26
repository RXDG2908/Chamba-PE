import { Suspense, useState } from 'react'
import { USA_GOOGLE_MAPS } from '../../nucleo/formatos.js'

// Elige Google Maps si hay clave (VITE_GOOGLE_MAPS_API_KEY) y, si no, OpenStreetMap con Leaflet.
// Si Google rechaza la clave (API sin habilitar, dominio no permitido, sin facturación), el mapa
// de Google avisa con onFallo y se pasa a OpenStreetMap en vez de dejarlo gris; se recuerda
// mientras dure la visita para no volver a intentarlo en cada pantalla.
let googleFallo = false

export default function MapaConRespaldo({ Google, Leaflet, clase, ...props }) {
  const [fallo, setFallo] = useState(googleFallo)
  const alFallar = () => {
    googleFallo = true
    setFallo(true)
  }
  const Mapa = USA_GOOGLE_MAPS && !fallo ? Google : Leaflet
  return (
    <Suspense fallback={<div className={`${clase} mapa-cargando`} role="status">Cargando mapa…</div>}>
      <Mapa {...props} onFallo={alFallar} />
    </Suspense>
  )
}
