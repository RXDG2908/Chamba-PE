// Una cuenta para la web y la app: quien se registró en la app (Firebase) entra a la web con el
// mismo correo y contraseña. Firebase confirma la contraseña y Django abre su sesión de siempre
// (POST /api/usuarios/login-firebase/), así que el resto de la web no cambia. Se carga solo cuando
// hace falta (import dinámico), para no sumar Firebase Auth al primer pantallazo.
import { getApps, initializeApp } from 'firebase/app'
import { getAuth, signInWithEmailAndPassword, signOut } from 'firebase/auth'
import { firebaseConfig } from '../firebase-config.js'
import { leerCookie } from './api.js'

export const firebaseConfigurado = () =>
  Boolean(firebaseConfig.apiKey && firebaseConfig.authDomain && firebaseConfig.projectId && firebaseConfig.appId)

/**
 * Intenta entrar con la cuenta de la app. Devuelve la respuesta de Django (200 con el usuario,
 * 403 si está sancionada o sin vincular) o null si Firebase no reconoce el correo y la contraseña.
 */
export async function entrarConCuentaDeLaApp(correo, clave) {
  if (!firebaseConfigurado()) return null
  const app = getApps()[0] || initializeApp(firebaseConfig)
  const auth = getAuth(app)
  let token
  try {
    const credencial = await signInWithEmailAndPassword(auth, correo.trim(), clave)
    token = await credencial.user.getIdToken()
  } catch {
    return null // correo o contraseña que Firebase no reconoce: la web muestra su propio mensaje
  }
  try {
    return await fetch('/api/usuarios/login-firebase/', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRFToken': leerCookie('csrftoken'),
        Authorization: `Bearer ${token}`,
      },
      body: '{}',
    })
  } finally {
    // La web vive con la sesión de Django; la de Firebase no se guarda en el navegador.
    await signOut(auth).catch(() => {})
  }
}
