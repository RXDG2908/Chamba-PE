// Navegación por rol. La API protege los datos; el menú solo evita ofrecer lo que no le toca a cada quien.

// Pantalla de inicio de cada rol (logo, «Inicio» y destino al iniciar sesión).
export const INICIO_POR_ROL = {
  visitante: '/',
  cliente: '/',
  prestador: '/prestador',
  administrador: '/administracion',
}

// Rutas de la sección «Verificación» del prestador: comparten un submenú propio.
export const SUBMENU_VERIFICACION = [
  ['/prestador/verificacion', 'Resumen'],
  ['/consentimiento', 'Consentimiento'],
  ['/verificar-dni', 'Verificar mi DNI'],
  ['/verificar-rostro', 'Verificar mi rostro'],
  ['/certificaciones', 'Certificados'],
  ['/eliminar-datos', 'Eliminar datos'],
]
export const RUTAS_VERIFICACION = SUBMENU_VERIFICACION.map(([ruta]) => ruta)

// [ruta, texto, rutas que también marcan el enlace como activo]
export const MENU_POR_ROL = {
  visitante: [
    ['/', 'Inicio'],
    ['/resultados', 'Buscar servicios'],
    ['/registro-prestador', 'Ofrecer mis servicios'],
  ],
  cliente: [
    ['/', 'Inicio'],
    ['/resultados', 'Buscar servicios'],
    ['/mis-servicios', 'Mis servicios'],
  ],
  prestador: [
    ['/prestador', 'Mi panel'],
    ['/solicitudes', 'Solicitudes'],
    ['/disponibilidad', 'Mi disponibilidad'],
    ['/prestador/verificacion', 'Verificación', RUTAS_VERIFICACION],
  ],
  administrador: [
    ['/administracion', 'Panel'],
    ['/administracion/revisiones', 'Revisión de documentos'],
    ['/administracion/cuentas', 'Cuentas'],
  ],
}

export const NOMBRE_SECCION = {
  cliente: 'clientes',
  prestador: 'prestadores',
  administrador: 'administración',
}
