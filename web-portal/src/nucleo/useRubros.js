import { useEffect, useState } from 'react'

/** Rubros de la API (/api/prestadores/rubros/), compartidos por Inicio y la página de cada sección. */
export function useRubros() {
  const [rubros, setRubros] = useState(null)
  useEffect(() => {
    const controlador = new AbortController()
    fetch('/api/prestadores/rubros/', { signal: controlador.signal })
      .then((r) => (r.ok ? r.json() : []))
      .then((datos) => setRubros(Array.isArray(datos) ? datos : []))
      .catch(() => { if (!controlador.signal.aborted) setRubros([]) })
    return () => controlador.abort()
  }, [])
  return rubros
}
