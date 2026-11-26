import { lazy } from 'react'
import MapaConRespaldo from './MapaConRespaldo.jsx'

// Cada versión se carga aparte: la que no se usa no pesa en la página.
const Google = lazy(() => import('./MapaBusquedaGoogle.jsx'))
const Leaflet = lazy(() => import('./MapaBusquedaLeaflet.jsx'))

// Mapa de la búsqueda (HU-3/HU-7): el cliente marca su ubicación y ve a los prestadores numerados.
export default function MapaBusqueda(props) {
  return <MapaConRespaldo Google={Google} Leaflet={Leaflet} clase="mapa-busqueda" {...props} />
}
