import { lazy } from 'react'
import MapaConRespaldo from './MapaConRespaldo.jsx'

const Google = lazy(() => import('./MapaCoberturaGoogle.jsx'))
const Leaflet = lazy(() => import('./MapaCoberturaLeaflet.jsx'))

// Mapa del registro del prestador (HU-1): toca el mapa para fijar el centro y ve el radio.
export default function MapaCobertura(props) {
  return <MapaConRespaldo Google={Google} Leaflet={Leaflet} clase="mapa" {...props} />
}
