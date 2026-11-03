// La sesión viaja en la cookie de Django; los cambios llevan el token CSRF en la cabecera.
export function leerCookie(nombre) {
  const fila = document.cookie.split('; ').find((c) => c.startsWith(`${nombre}=`))
  return fila ? decodeURIComponent(fila.split('=')[1]) : ''
}

// El token cambia al iniciar o cerrar sesión, por eso se lee en cada petición.
export function enviarJson(url, metodo, datos) {
  return fetch(url, {
    method: metodo,
    headers: { 'Content-Type': 'application/json', 'X-CSRFToken': leerCookie('csrftoken') },
    body: datos === undefined ? undefined : JSON.stringify(datos),
  })
}

// Convierte {"campo": ["mensaje"]} de Django en {campo: "mensaje"} para el formulario.
export function erroresPorCampo(cuerpo) {
  const errores = {}
  for (const [campo, mensajes] of Object.entries(cuerpo || {})) {
    errores[campo] = [].concat(mensajes).join(' ')
  }
  return errores
}

export const NOMBRE_ROL = { cliente: 'Cliente', prestador: 'Prestador', administrador: 'Administrador' }
