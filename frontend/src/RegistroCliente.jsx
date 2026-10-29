import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { enviarJson, erroresPorCampo } from './api.js'
import './RegistroPrestador.css'
import './Cuenta.css'

const VACIO = { nombres: '', apellidos: '', username: '', email: '', password: '' }

function RegistroCliente({ alIniciar }) {
  const navegar = useNavigate()
  const [datos, setDatos] = useState(VACIO)
  const [errores, setErrores] = useState({})
  const [estado, setEstado] = useState('inicial') // inicial | enviando | fallo

  const cambiar = (e) => setDatos({ ...datos, [e.target.name]: e.target.value })

  // Validaciones del lado del cliente (el backend vuelve a validar todo).
  const validar = () => {
    const e = {}
    if (!datos.nombres.trim()) e.nombres = 'Ingresa tus nombres.'
    if (!datos.apellidos.trim()) e.apellidos = 'Ingresa tus apellidos.'
    if (!datos.username.trim()) e.username = 'Elige un nombre de usuario.'
    if (!/^\S+@\S+\.\S+$/.test(datos.email)) e.email = 'Ingresa un correo válido.'
    if (datos.password.length < 8) e.password = 'La contraseña debe tener al menos 8 caracteres.'
    return e
  }

  const enviar = async (evento) => {
    evento.preventDefault()
    const e = validar()
    setErrores(e)
    if (Object.keys(e).length) return

    setEstado('enviando')
    try {
      const respuesta = await enviarJson('/api/usuarios/registro/', 'POST', datos)
      const cuerpo = await respuesta.json()
      if (respuesta.ok) {
        // El registro ya deja la sesión iniciada.
        alIniciar(cuerpo)
        navegar('/resultados', { replace: true })
        return
      }
      setErrores(erroresPorCampo(cuerpo))
      setEstado('inicial')
    } catch {
      setEstado('fallo')
    }
  }

  const campo = (nombre, etiqueta, props = {}) => (
    <label className="campo">
      <span>{etiqueta}</span>
      <input name={nombre} value={datos[nombre]} onChange={cambiar} aria-invalid={!!errores[nombre]} {...props} />
      {errores[nombre] && <small className="error">{errores[nombre]}</small>}
    </label>
  )

  return (
    <form className="registro cuenta" onSubmit={enviar} noValidate>
      <h2>Crear cuenta de cliente</h2>
      <p className="ayuda">Con tu cuenta puedes buscar prestadores y enviarles solicitudes.</p>

      {estado === 'fallo' && (
        <p className="error caja" role="alert">No pudimos comunicarnos con el servidor. Inténtalo de nuevo.</p>
      )}

      <div className="rejilla">
        {campo('nombres', 'Nombres', { autoComplete: 'given-name' })}
        {campo('apellidos', 'Apellidos', { autoComplete: 'family-name' })}
        {campo('username', 'Usuario', { autoComplete: 'username' })}
        {campo('email', 'Correo', { type: 'email', autoComplete: 'email' })}
        {campo('password', 'Contraseña', { type: 'password', autoComplete: 'new-password' })}
      </div>

      <button type="submit" className="boton" disabled={estado === 'enviando'}>
        {estado === 'enviando' ? 'Creando cuenta…' : 'Crear cuenta'}
      </button>
      <p className="pie-cuenta">¿Ya tienes cuenta? <Link to="/ingresar">Inicia sesión</Link>.</p>
    </form>
  )
}

export default RegistroCliente
