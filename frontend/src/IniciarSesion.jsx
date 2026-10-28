import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { enviarJson } from './api.js'
import './RegistroPrestador.css'
import './Cuenta.css'

// Pantalla a la que va cada rol después de iniciar sesión.
const INICIO_POR_ROL = { cliente: '/resultados', prestador: '/resultados', administrador: '/resultados' }

function IniciarSesion({ alIniciar }) {
  const navegar = useNavigate()
  const { state } = useLocation()
  const [datos, setDatos] = useState({ username: '', password: '' })
  const [errores, setErrores] = useState({})
  const [estado, setEstado] = useState('inicial') // inicial | enviando | fallo

  const cambiar = (e) => setDatos({ ...datos, [e.target.name]: e.target.value })

  const enviar = async (evento) => {
    evento.preventDefault()
    const e = {}
    if (!datos.username.trim()) e.username = 'Ingresa tu usuario o correo.'
    if (!datos.password) e.password = 'Ingresa tu contraseña.'
    setErrores(e)
    if (Object.keys(e).length) return

    setEstado('enviando')
    try {
      const respuesta = await enviarJson('/api/usuarios/login/', 'POST', datos)
      const cuerpo = await respuesta.json()
      if (respuesta.ok) {
        alIniciar(cuerpo)
        navegar(state?.desde || INICIO_POR_ROL[cuerpo.rol] || '/', { replace: true })
        return
      }
      setErrores({ general: cuerpo.detail || 'No se pudo iniciar sesión.' })
      setEstado('inicial')
    } catch {
      setEstado('fallo')
    }
  }

  return (
    <form className="registro cuenta" onSubmit={enviar} noValidate>
      <h2>Iniciar sesión</h2>
      <p className="ayuda">Ingresa con tu usuario o tu correo.</p>

      {estado === 'fallo' && (
        <p className="error caja" role="alert">No pudimos comunicarnos con el servidor. Inténtalo de nuevo.</p>
      )}
      {errores.general && <p className="error caja" role="alert">{errores.general}</p>}

      <div className="rejilla una-columna">
        <label className="campo">
          <span>Usuario o correo</span>
          <input name="username" value={datos.username} onChange={cambiar} autoComplete="username" aria-invalid={!!errores.username} />
          {errores.username && <small className="error">{errores.username}</small>}
        </label>
        <label className="campo">
          <span>Contraseña</span>
          <input name="password" type="password" value={datos.password} onChange={cambiar} autoComplete="current-password" aria-invalid={!!errores.password} />
          {errores.password && <small className="error">{errores.password}</small>}
        </label>
      </div>

      <button type="submit" className="boton" disabled={estado === 'enviando'}>
        {estado === 'enviando' ? 'Ingresando…' : 'Ingresar'}
      </button>
      <p className="pie-cuenta">
        ¿No tienes cuenta? <Link to="/crear-cuenta">Crear cuenta de cliente</Link> o <Link to="/">regístrate como prestador</Link>.
      </p>
    </form>
  )
}

export default IniciarSesion
