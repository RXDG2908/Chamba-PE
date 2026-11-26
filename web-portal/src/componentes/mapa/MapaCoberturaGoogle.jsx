import { useEffect } from 'react'
import { AdvancedMarker, APIProvider, Map, useMap, useMapsLibrary } from '@vis.gl/react-google-maps'
import { CLAVE_GOOGLE_MAPS, LIMA } from '../../nucleo/formatos.js'
import VigilarCarga from './VigilarCarga.jsx'

const ID_MAPA = import.meta.env.VITE_GOOGLE_MAPS_MAP_ID || 'DEMO_MAP_ID'

// Círculo del área de cobertura (la librería no trae uno: se usa google.maps.Circle).
function Circulo({ centro, radioKm }) {
  const mapa = useMap()
  const libreria = useMapsLibrary('maps')
  useEffect(() => {
    if (!mapa || !libreria || !centro) return undefined
    const circulo = new libreria.Circle({
      map: mapa, center: { lat: centro[0], lng: centro[1] }, radius: radioKm * 1000, clickable: false,
      strokeColor: '#16734f', strokeWeight: 2, fillColor: '#16734f', fillOpacity: 0.12,
    })
    return () => circulo.setMap(null)
  }, [mapa, libreria, centro, radioKm])
  return null
}

export default function MapaCoberturaGoogle({ centro, radioKm, onElegir, onFallo }) {
  return (
    <APIProvider apiKey={CLAVE_GOOGLE_MAPS} language="es" region="PE">
      <VigilarCarga onFallo={onFallo} />
      <div className="mapa mapa-google">
        <Map defaultCenter={{ lat: LIMA[0], lng: LIMA[1] }} defaultZoom={11} mapId={ID_MAPA}
          gestureHandling="greedy" streetViewControl={false} fullscreenControl={false} clickableIcons={false}
          onClick={(e) => e.detail?.latLng && onElegir([e.detail.latLng.lat, e.detail.latLng.lng])}>
          {centro && (
            <AdvancedMarker position={{ lat: centro[0], lng: centro[1] }} title="Centro de tu zona">
              <span className="pin-cliente" aria-hidden="true" />
            </AdvancedMarker>
          )}
          <Circulo centro={centro} radioKm={radioKm} />
        </Map>
      </div>
    </APIProvider>
  )
}
