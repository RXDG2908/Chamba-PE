// HU-20 (T20.1): prueba de vida con MediaPipe Face Landmarker, todo en el navegador.
// El modelo y el WebAssembly se sirven desde la propia web (sin CDN): nada sale del equipo
// hasta que se envía la selfie.

// Puntajes de MediaPipe (0 = ojo abierto, 1 = cerrado).
export const OJO_CERRADO = 0.5
export const OJO_ABIERTO = 0.25

// Un parpadeo es: ojos abiertos → cerrados → abiertos otra vez, con un solo rostro en cuadro.
export function nuevoDetectorDeParpadeo() {
  let fase = 'esperando_abiertos'
  return {
    get fase() {
      return fase
    },
    // Devuelve true cuando se completa el parpadeo.
    actualizar(izquierdo, derecho) {
      const cerrados = izquierdo >= OJO_CERRADO && derecho >= OJO_CERRADO
      const abiertos = izquierdo <= OJO_ABIERTO && derecho <= OJO_ABIERTO
      if (fase === 'esperando_abiertos' && abiertos) fase = 'abiertos'
      else if (fase === 'abiertos' && cerrados) fase = 'cerrados'
      else if (fase === 'cerrados' && abiertos) fase = 'parpadeo'
      return fase === 'parpadeo'
    },
    reiniciar() {
      fase = 'esperando_abiertos'
    },
  }
}

export function puntajeDe(categorias, nombre) {
  return categorias?.find((c) => c.categoryName === nombre)?.score ?? 0
}

export async function cargarDetector() {
  const { FaceLandmarker } = await import('@mediapipe/tasks-vision')
  const fileset = {
    wasmLoaderPath: new URL('../node_modules/@mediapipe/tasks-vision/wasm/vision_wasm_internal.js', import.meta.url).href,
    wasmBinaryPath: new URL('../node_modules/@mediapipe/tasks-vision/wasm/vision_wasm_internal.wasm', import.meta.url).href,
  }
  return FaceLandmarker.createFromOptions(fileset, {
    baseOptions: { modelAssetPath: new URL('./assets/face_landmarker.task', import.meta.url).href },
    runningMode: 'VIDEO',
    numFaces: 2,
    outputFaceBlendshapes: true,
  })
}
