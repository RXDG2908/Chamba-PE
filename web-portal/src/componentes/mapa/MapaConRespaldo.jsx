import { Suspense, useState } from 'react'
import { USA_GOOGLE_MAPS } from '../../nucleo/formatos.js'
import LimiteDeError from '../LimiteDeError.jsx'

// Elige Google Maps si hay clave (VITE_GOOGLE_MAPS_API_KEY) y, si no, OpenStreetMap con Leaflet.
// Si Google rechaza la clave (API sin habilitar, dominio no permitido, sin facturación), el mapa
// de Google avisa con onFallo y se pasa a OpenStreetMap en vez de dejarlo gris; se recuerda
// mientras dure la visita para no volver a intentarlo en cada pantalla.
// Además, si el mapa de Google lanza un error al dibujarse (con «ApiNotActivatedMapError» un
// marcador fallaba y dejaba toda la web en blanco), el límite de error cambia a OpenStreetMap.
let googleFallo = false

export default function MapaConRespaldo({ Google, Leaflet, clase, ...props }) {
  const [fallo, setFallo] = useState(googleFallo)
  const alFallar = () => {
    googleFallo = true
    setFallo(true)
  }
  const usaGoogle = USA_GOOGLE_MAPS && !fallo
  const Mapa = usaGoogle ? Google : Leaflet
  const cargando = <div className={`${clase} mapa-cargando`} role="status">Cargando mapa…</div>
  return (
    <LimiteDeError key={usaGoogle ? 'google' : 'osm'} alternativa={cargando} onError={usaGoogle ? alFallar : undefined}>
      <Suspense fallback={cargando}>
        <Mapa {...props} onFallo={alFallar} />
      </Suspense>
    </LimiteDeError>
  )
}
