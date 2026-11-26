import { useCallback, useEffect, useState } from 'react'
import { AdvancedMarker, APIProvider, InfoWindow, Map, useAdvancedMarkerRef, useMap } from '@vis.gl/react-google-maps'
import { CLAVE_GOOGLE_MAPS, coincidentesCon, LIMA } from '../../nucleo/formatos.js'
import FichaPrestadores from './FichaPrestadores.jsx'
import VigilarCarga from './VigilarCarga.jsx'
import { Focus } from 'lucide-react'

// Mapa de Google Maps para la búsqueda: el cliente toca el mapa para fijar su ubicación y ve a
// los prestadores numerados en el mismo orden que la lista. `DEMO_MAP_ID` habilita los marcadores
// avanzados sin configurar un estilo propio en Google Cloud.
const ID_MAPA = import.meta.env.VITE_GOOGLE_MAPS_MAP_ID || 'DEMO_MAP_ID'
const centro = { lat: LIMA[0], lng: LIMA[1] }

function encuadrar(mapa, ubicacion, prestadores) {
  if (!mapa || !ubicacion || !window.google) return
  const limites = new window.google.maps.LatLngBounds()
  limites.extend({ lat: ubicacion[0], lng: ubicacion[1] })
  prestadores.forEach((p) => limites.extend({ lat: p.latitud, lng: p.longitud }))
  mapa.fitBounds(limites, 48)
  // Con un solo punto fitBounds acerca demasiado: igual que en Leaflet, como máximo zoom 15.
  window.google.maps.event.addListenerOnce(mapa, 'idle', () => {
    if (mapa.getZoom() > 15) mapa.setZoom(15)
  })
}

function Encuadre({ ubicacion, prestadores }) {
  const mapa = useMap()
  useEffect(() => encuadrar(mapa, ubicacion, prestadores), [mapa, ubicacion, prestadores])
  return ubicacion && (
    <div className="volver-mapa">
      <button type="button" onClick={() => encuadrar(mapa, ubicacion, prestadores)}><Focus className="icono" size={18} aria-hidden="true" />Ver ubicación y resultados</button>
    </div>
  )
}

function MarcadorPrestador({ prestador, indice, prestadores, abierto, onAbrir, onCerrar }) {
  const [referencia, marcador] = useAdvancedMarkerRef()
  const coincidentes = coincidentesCon(prestador, prestadores)
  const titulo = coincidentes.length > 1
    ? `${coincidentes.length} prestadores en esta ubicación`
    : `${prestador.nombres} ${prestador.apellidos}`
  return (
    <>
      <AdvancedMarker ref={referencia} position={{ lat: prestador.latitud, lng: prestador.longitud }}
        title={titulo} onClick={onAbrir} clickable>
        <span className="pin-prestador">{indice + 1}{coincidentes.length > 1 ? '+' : ''}</span>
      </AdvancedMarker>
      {abierto && (
        <InfoWindow anchor={marcador} onCloseClick={onCerrar} maxWidth={260} headerDisabled={false}>
          <FichaPrestadores coincidentes={coincidentes} />
        </InfoWindow>
      )}
    </>
  )
}

export default function MapaBusquedaGoogle({ ubicacion, prestadores, onElegir, onFallo }) {
  // La ventana abierta pertenece a una búsqueda: con resultados nuevos se cierra sola.
  const [ventana, setVentana] = useState(null)
  const abierto = ventana?.prestadores === prestadores ? ventana.id : null
  const setAbierto = useCallback((id) => setVentana(id === null ? null : { id, prestadores }), [prestadores])

  const alTocar = useCallback((evento) => {
    const punto = evento.detail?.latLng
    if (!punto) return
    setAbierto(null)
    onElegir([punto.lat, punto.lng])
  }, [onElegir, setAbierto])

  return (
    <APIProvider apiKey={CLAVE_GOOGLE_MAPS} language="es" region="PE">
      <VigilarCarga onFallo={onFallo} />
      <div className="mapa-busqueda mapa-google">
        <Map defaultCenter={centro} defaultZoom={13} mapId={ID_MAPA} gestureHandling="cooperative"
          disableDefaultUI={false} streetViewControl={false} fullscreenControl={false}
          clickableIcons={false} onClick={alTocar}>
          {ubicacion && (
            <AdvancedMarker position={{ lat: ubicacion[0], lng: ubicacion[1] }} title="Tu ubicación para el servicio">
              <span className="pin-cliente" aria-hidden="true" />
            </AdvancedMarker>
          )}
          {prestadores.map((prestador, indice) => (
            <MarcadorPrestador key={prestador.id} prestador={prestador} indice={indice} prestadores={prestadores}
              abierto={abierto === prestador.id} onAbrir={() => setAbierto(prestador.id)}
              onCerrar={() => setAbierto(null)} />
          ))}
          <Encuadre ubicacion={ubicacion} prestadores={prestadores} />
        </Map>
      </div>
    </APIProvider>
  )
}
