import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// Publica solo resultados: sin argv, rutas absolutas, adjuntos, cookies ni logs.
const frontend = path.dirname(fileURLToPath(import.meta.url))
const output = path.resolve(frontend, '../docs/evidencias/hu7-local/resultados.json')

export default class Hu7EvidenceReporter {
  pruebas = []
  onBegin() { this.inicio = new Date().toISOString() }
  onTestEnd(test, result) {
    this.pruebas.push({
      titulo: test.title,
      archivo: path.relative(frontend, test.location.file).split(path.sep).join('/'),
      estadoEsperado: test.expectedStatus,
      resultados: [{ estado: result.status, duracionMs: result.duration, reintento: result.retry }],
    })
  }
  onEnd(result) {
    const expected = this.pruebas.filter((p) => p.resultados[0].estado !== 'skipped' && p.resultados[0].estado === p.estadoEsperado).length
    const skipped = this.pruebas.filter((p) => p.resultados[0].estado === 'skipped').length
    const retries = this.pruebas.filter((p) => p.resultados[0].reintento > 0).length
    fs.mkdirSync(path.dirname(output), { recursive: true })
    fs.writeFileSync(output, JSON.stringify({
      formato: 'hu7-resumen-publico-v1',
      entorno: 'Datos exclusivamente ficticios; Django y teselas OSM reales; sin Firebase.',
      estado: result.status,
      stats: { startTime: this.inicio, duration: result.duration, expected, skipped,
        unexpected: this.pruebas.length - expected - skipped, reintentos: retries },
      pruebas: this.pruebas,
    }, null, 2) + '\n')
  }
}
