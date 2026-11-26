import { Circle, MapContainer, TileLayer, useMapEvents } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import { LIMA } from '../../nucleo/formatos.js'

function SeleccionarCentro({ onElegir }) {
  useMapEvents({ click: (e) => onElegir([e.latlng.lat, e.latlng.lng]) })
  return null
}

export default function MapaCoberturaLeaflet({ centro, radioKm, onElegir }) {
  return (
    <MapContainer center={LIMA} zoom={11} className="mapa" scrollWheelZoom>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <SeleccionarCentro onElegir={onElegir} />
      {centro && <Circle center={centro} radius={radioKm * 1000} />}
    </MapContainer>
  )
}
