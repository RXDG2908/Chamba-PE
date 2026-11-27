// Secciones de servicios, las mismas de la app móvil (ui/Rubros.kt): la persona elige por lo que
// necesita hacer (una urgencia, su casa, un edificio, su negocio) y cada sección tiene su color en
// la app y en la web (law-of-similarity). Un rubro puede estar en varias.
import { BellRing, BriefcaseBusiness, Building2, House, LayoutGrid } from 'lucide-react'

export function clave(nombre = '') {
  return String(nombre).trim().toLowerCase().normalize('NFD').replace(/\p{M}+/gu, '')
}

export const SECCIONES = [
  {
    clave: 'urgente', titulo: 'Urgente', descripcion: 'Algo se rompió y no puede esperar', Icono: BellRing,
    rubros: ['gasfiteria', 'cerrajeria', 'electricidad', 'vidrieria'],
  },
  {
    clave: 'hogar', titulo: 'Hogar', descripcion: 'Arreglos y cuidado de tu casa', Icono: House,
    rubros: ['limpieza', 'pintura', 'carpinteria', 'jardineria', 'electrodomesticos', 'fumigacion', 'mudanzas', 'gasfiteria', 'electricidad'],
  },
  {
    clave: 'residencial', titulo: 'Residencial', descripcion: 'Edificios, condominios y áreas comunes', Icono: Building2,
    rubros: ['albanileria', 'pintura', 'techos y drywall', 'jardineria', 'fumigacion', 'camaras de seguridad', 'limpieza'],
  },
  {
    clave: 'empresas', titulo: 'Empresas', descripcion: 'Oficinas, locales y talleres', Icono: BriefcaseBusiness,
    rubros: ['electricidad', 'aire acondicionado', 'camaras de seguridad', 'redes e internet', 'limpieza', 'soldadura', 'mecanica'],
  },
  { clave: 'otros', titulo: 'Más servicios', descripcion: 'Otros oficios disponibles', Icono: LayoutGrid, rubros: [] },
]

const CONOCIDOS = new Set(SECCIONES.flatMap((s) => s.rubros))

/** Los rubros de la base que van en la sección, en el orden de la sección. */
export function rubrosDe(seccion, todos) {
  if (seccion.clave === 'otros') return todos.filter((r) => !CONOCIDOS.has(clave(r.nombre)))
  const porClave = new Map(todos.map((r) => [clave(r.nombre), r]))
  return seccion.rubros.map((c) => porClave.get(c)).filter(Boolean)
}

/** La primera sección que contiene al rubro (para abrirlo desde el buscador). */
export function seccionDe(rubro) {
  return SECCIONES.find((s) => s.clave !== 'otros' && s.rubros.includes(clave(rubro.nombre))) ?? SECCIONES.at(-1)
}

export const POPULARES = ['gasfiteria', 'electricidad', 'limpieza', 'cerrajeria', 'pintura', 'carpinteria', 'camaras de seguridad']

export function populares(todos) {
  const porClave = new Map(todos.map((r) => [clave(r.nombre), r]))
  return POPULARES.map((c) => porClave.get(c)).filter(Boolean)
}

// Cómo pide la gente cada rubro (search-ux): «gasfitero», «plomero» o «agua» llevan a Gasfitería.
const SINONIMOS = {
  gasfiteria: ['gasfitero', 'plomero', 'plomeria', 'agua', 'cano', 'fuga', 'desague', 'inodoro', 'terma'],
  electricidad: ['electricista', 'luz', 'corto', 'enchufe', 'tomacorriente', 'cable'],
  carpinteria: ['carpintero', 'madera', 'mueble', 'puerta', 'closet'],
  pintura: ['pintor', 'pintar', 'pared'],
  albanileria: ['albanil', 'construccion', 'obra', 'pared', 'piso', 'mayolica'],
  cerrajeria: ['cerrajero', 'llave', 'chapa', 'cerradura'],
  limpieza: ['limpiar', 'aseo', 'limpiador'],
  jardineria: ['jardinero', 'jardin', 'pasto', 'plantas', 'poda'],
  soldadura: ['soldador', 'soldar', 'reja', 'metal'],
  mecanica: ['mecanico', 'auto', 'carro', 'moto'],
  'camaras de seguridad': ['camara', 'camaras', 'seguridad', 'cctv', 'vigilancia'],
  'aire acondicionado': ['aire', 'aire acondicionado', 'clima', 'frio'],
  fumigacion: ['fumigar', 'plaga', 'cucaracha', 'ratas', 'termitas'],
  'redes e internet': ['internet', 'wifi', 'red', 'router', 'redes'],
  mudanzas: ['mudanza', 'flete', 'mudar'],
  vidrieria: ['vidrio', 'vidriero', 'ventana', 'espejo'],
  electrodomesticos: ['refrigeradora', 'lavadora', 'cocina', 'microondas', 'electrodomestico'],
  'techos y drywall': ['techo', 'drywall', 'tabique', 'goteras'],
}

/** ¿El rubro coincide con lo que escribió la persona? Sin tildes, por nombre o por sinónimo. */
export function coincide(rubro, texto) {
  const buscado = clave(texto)
  if (buscado.length < 2) return false
  const nombre = clave(rubro.nombre)
  return nombre.includes(buscado) || (SINONIMOS[nombre] ?? []).some((s) => s.startsWith(buscado) || buscado.startsWith(s))
}
