import { useEffect, useState } from 'react'

// Pide varias rutas de la API a la vez para los paneles. Cada resultado es
// { estado: 'cargando' | 'listo' | 'vacio' | 'fallo', datos }; un 404 cuenta como «vacio»
// (por ejemplo, «todavía no verificaste tu DNI»).
export default function useConsultas(rutas) {
  const clave = JSON.stringify(rutas)
  const [resultados, setResultados] = useState({ clave: null, valores: {} })

  useEffect(() => {
    const controlador = new AbortController()
    const lista = JSON.parse(clave)
    Promise.all(Object.entries(lista).map(async ([nombre, ruta]) => {
      try {
        const respuesta = await fetch(ruta, { signal: controlador.signal })
        if (respuesta.status === 404) return [nombre, { estado: 'vacio', datos: null }]
        if (!respuesta.ok) return [nombre, { estado: 'fallo', datos: null }]
        return [nombre, { estado: 'listo', datos: await respuesta.json() }]
      } catch {
        return [nombre, { estado: 'fallo', datos: null }]
      }
    })).then((pares) => {
      if (!controlador.signal.aborted) setResultados({ clave, valores: Object.fromEntries(pares) })
    })
    return () => controlador.abort()
  }, [clave])

  const listos = resultados.clave === clave
  return Object.fromEntries(Object.keys(rutas).map((nombre) => [
    nombre, listos ? resultados.valores[nombre] : { estado: 'cargando', datos: null },
  ]))
}
