// Iconos de la interfaz (lucide-react): un solo trazo y grosor en toda la web.
import {
  AirVent, AppWindow, BadgeCheck, BrickWall, Brush, Bug, Car, Cctv, ClipboardList, Droplets, Flame, Hammer, House,
  Inbox, KeyRound, LayoutDashboard, Leaf, Paintbrush, Plug, Refrigerator, Router, Search, ShieldCheck, Sparkles, Store,
  ToggleRight, Truck, UserCog, UserRound, Wrench, Zap,
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
  '/perfil': UserRound,
  '/prestador/verificacion': ShieldCheck,
  '/administracion': LayoutDashboard,
  '/administracion/revisiones': BadgeCheck,
  '/administracion/cuentas': UserCog,
}

// Rubros: se elige por palabra clave del nombre; si no coincide, una llave.
// Mismos rubros que la app (ui/Rubros.kt), cada uno con su ícono; el orden importa (más específico antes).
const ICONO_RUBRO = [
  [/electrodom/i, Refrigerator],
  [/c[aá]mara|seguridad|cctv/i, Cctv],
  [/aire acond|climatiz/i, AirVent],
  [/fumig|plaga/i, Bug],
  [/redes|internet|wifi/i, Router],
  [/vidrier|vidrio/i, AppWindow],
  [/techo|drywall/i, House],
  [/gasfiter|plomer|agua/i, Droplets],
  [/electric/i, Zap],
  [/pint/i, Paintbrush],
  [/carpinter/i, Hammer],
  [/cerraj/i, KeyRound],
  [/jardin/i, Leaf],
  [/limpie/i, Sparkles],
  [/mec[aá]nic|auto/i, Car],
  [/soldad/i, Flame],
  [/alba[ñn]il|constru/i, BrickWall],
  [/mudanz|flete/i, Truck],
  [/instal|enchuf/i, Plug],
  [/decora|tapiz/i, Brush],
]
export function iconoRubro(nombre = '') {
  return ICONO_RUBRO.find(([patron]) => patron.test(nombre))?.[1] ?? Wrench
}

