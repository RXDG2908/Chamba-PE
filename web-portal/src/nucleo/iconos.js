// Iconos de la interfaz (lucide-react): un solo trazo y grosor en toda la web.
import {
  BadgeCheck, Brush, Car, ClipboardList, Droplets, Flame, Hammer, House, Inbox, KeyRound, LayoutDashboard,
  Leaf, Paintbrush, Plug, Search, ShieldCheck, Sparkles, Store, ToggleRight, Truck, UserCog, Wrench, Zap,
} from 'lucide-react'

// Menú principal: icono por ruta.
export const ICONO_RUTA = {
  '/': House,
  '/resultados': Search,
  '/registro-prestador': Store,
  '/mis-servicios': ClipboardList,
  '/prestador': LayoutDashboard,
  '/solicitudes': Inbox,
  '/disponibilidad': ToggleRight,
  '/prestador/verificacion': ShieldCheck,
  '/administracion': LayoutDashboard,
  '/administracion/revisiones': BadgeCheck,
  '/administracion/cuentas': UserCog,
}

// Rubros: se elige por palabra clave del nombre; si no coincide, una llave.
const ICONO_RUBRO = [
  [/gasfiter|plomer|agua/i, Droplets],
  [/electric/i, Zap],
  [/pint/i, Paintbrush],
  [/carpinter/i, Hammer],
  [/cerraj/i, KeyRound],
  [/jardin/i, Leaf],
  [/limpie/i, Sparkles],
  [/mec[aá]nic|auto/i, Car],
  [/soldad/i, Flame],
  [/alba[ñn]il|constru/i, House],
  [/mudanz|flete/i, Truck],
  [/instal|enchuf/i, Plug],
  [/decora|tapiz/i, Brush],
]
export function iconoRubro(nombre = '') {
  return ICONO_RUBRO.find(([patron]) => patron.test(nombre))?.[1] ?? Wrench
}

