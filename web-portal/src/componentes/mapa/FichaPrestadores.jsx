import { formatoDistancia, nombrePublico, textoCalificacion } from '../../nucleo/formatos.js'

// Contenido de la ventana de un marcador: uno o varios prestadores en el mismo punto.
export default function FichaPrestadores({ coincidentes }) {
  return (
    <div className="contenido-marcador">
      {coincidentes.length > 1 && <p><strong>{coincidentes.length} prestadores en esta ubicación</strong></p>}
      {coincidentes.map((persona) => (
        <article key={persona.id}>
          <strong>{nombrePublico(persona)}</strong>
          {persona.nuevo === true && <p><span className="insignia-nuevo">Nuevo</span></p>}
          <p>{persona.rubros.map((r) => r.nombre).join(' · ')}</p>
          <p>{formatoDistancia.format(persona.distancia_km)} km</p>
          <p>{textoCalificacion(persona)}</p>
        </article>
      ))}
    </div>
  )
}
