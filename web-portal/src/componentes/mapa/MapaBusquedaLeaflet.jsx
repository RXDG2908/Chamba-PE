import { useEffect } from 'react'
import { CircleMarker, MapContainer, Marker, Popup, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import { divIcon, DomEvent } from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { coincidentesCon, LIMA } from '../../nucleo/formatos.js'
import FichaPrestadores from './FichaPrestadores.jsx'

// Mapa de respaldo (OpenStreetMap) cuando no hay clave de Google Maps; también lo usan las
// pruebas de navegador de HU-7, que corren sin red.

function UbicacionCliente({ ubicacion, onElegir }) {
  useMapEvents({
    click: ({ latlng, originalEvent }) => {
      if (originalEvent?.target?.closest('.leaflet-popup, .leaflet-interactive, .leaflet-marker-icon')) return
      // Normaliza la longitud cuando Leaflet muestra otra copia del mundo.
      const punto = latlng.wrap()
      if (punto.lat >= -90 && punto.lat <= 90) onElegir([punto.lat, punto.lng])
    },
  })

  return ubicacion ? (
    <CircleMarker center={ubicacion} radius={9} bubblingMouseEvents={false}
      pathOptions={{ color: '#fff', weight: 3, fillColor: '#2463a6', fillOpacity: 1 }}>
      <Popup>Tu ubicación para el servicio</Popup>
    </CircleMarker>
  ) : null
}

function VistaResultados({ ubicacion, prestadores }) {
  const mapa = useMap()

  useEffect(() => {
    if (!ubicacion) return
    const puntos = [ubicacion, ...prestadores.map((p) => [p.latitud, p.longitud])]
    // Aplicar cada resultado de inmediato: Leaflet ignora nuevos zooms mientras anima otro.
    mapa.fitBounds(puntos, { padding: [32, 32], maxZoom: 15, animate: false })
  }, [mapa, ubicacion, prestadores])

  return ubicacion && (
    <div className="volver-mapa" ref={(nodo) => {
      if (nodo) DomEvent.disableClickPropagation(nodo)
    }}>
      <button type="button" onClick={() => mapa.fitBounds(
        [ubicacion, ...prestadores.map((p) => [p.latitud, p.longitud])],
        { padding: [32, 32], maxZoom: 15, animate: false },
      )}>Ver ubicación y resultados</button>
    </div>
  )
}

function MarcadoresPrestadores({ prestadores }) {
  return prestadores.map((prestador, indice) => {
    // No desplazamos las coordenadas: la ventana permite consultar a todos
    // los prestadores superpuestos, incluido un punto compartido con el cliente.
    const coincidentes = coincidentesCon(prestador, prestadores)
    const titulo = coincidentes.length > 1
      ? `${coincidentes.length} prestadores en esta ubicación`
      : `${prestador.nombres} ${prestador.apellidos}`
    return (
      <Marker key={prestador.id} position={[prestador.latitud, prestador.longitud]}
        bubblingMouseEvents={false} title={titulo} alt={titulo}
        icon={divIcon({
          className: 'marcador-prestador',
          html: `<span>${indice + 1}${coincidentes.length > 1 ? '+' : ''}</span>`,
          iconSize: [36, 36], iconAnchor: [18, 36], popupAnchor: [0, -36],
        })}>
        <Popup maxWidth={240} maxHeight={200}>
          <FichaPrestadores coincidentes={coincidentes} />
        </Popup>
      </Marker>
    )
  })
}

export default function MapaBusquedaLeaflet({ ubicacion, prestadores, onElegir }) {
  return (
    <MapContainer center={LIMA} zoom={13} className="mapa-busqueda" scrollWheelZoom={false}>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <UbicacionCliente ubicacion={ubicacion} onElegir={onElegir} />
      <MarcadoresPrestadores prestadores={prestadores} />
      <VistaResultados ubicacion={ubicacion} prestadores={prestadores} />
    </MapContainer>
  )
}
