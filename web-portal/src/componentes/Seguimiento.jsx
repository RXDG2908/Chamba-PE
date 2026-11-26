import './Seguimiento.css'
import { Car, Check, CircleCheckBig, Handshake, Wrench } from 'lucide-react'

import { ETAPAS } from '../nucleo/etapasSeguimiento.js'

const ICONO_ETAPA = { aceptada: Handshake, en_camino: Car, en_servicio: Wrench, finalizada: CircleCheckBig }

const formatoHora = new Intl.DateTimeFormat('es-PE', { hour: '2-digit', minute: '2-digit' })

// Línea de etapas; solo se muestra cuando la solicitud ya fue confirmada.
export default function Seguimiento({ estado, historial = [] }) {
  const actual = ETAPAS.findIndex((e) => e.estado === estado)
  if (actual < 0) return null
  const fechas = Object.fromEntries((Array.isArray(historial) ? historial : [])
    .filter((h) => h && typeof h.estado === 'string' && typeof h.fecha === 'string')
    .map((h) => [h.estado, h.fecha]))
  return (
    <ol className="seguimiento" aria-label="Seguimiento del servicio">
      {ETAPAS.map((etapa, i) => {
        const clase = i < actual ? 'hecha' : i === actual ? 'actual' : 'pendiente'
        const fecha = fechas[etapa.estado] ? new Date(fechas[etapa.estado]) : null
        return (
          <li key={etapa.estado} className={`seg-etapa ${clase}`} aria-current={i === actual ? 'step' : undefined}>
            <span className="seg-punto" aria-hidden="true">
              {(() => {
                const Icono = i < actual || (i === actual && estado === 'finalizada') ? Check : ICONO_ETAPA[etapa.estado]
                return <Icono size={15} strokeWidth={2.6} />
              })()}
            </span>
            <span className="seg-nombre">{etapa.nombre}</span>
            {fecha && !Number.isNaN(fecha.getTime()) && i <= actual && <span className="seg-hora">{formatoHora.format(fecha)}</span>}
          </li>
        )
      })}
    </ol>
  )
}
