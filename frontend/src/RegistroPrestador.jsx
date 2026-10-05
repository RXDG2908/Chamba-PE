import { useEffect, useState } from 'react'
import { Circle, MapContainer, TileLayer, useMapEvents } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import './RegistroPrestador.css'

const LIMA = [-12.046374, -77.042793]
const VACIO = {
  nombres: '', apellidos: '', dni: '', telefono: '', username: '', email: '', password: '',
  radio_cobertura_km: 5,
}

function SeleccionarCentro({ onElegir }) {
  useMapEvents({ click: (e) => onElegir([e.latlng.lat, e.latlng.lng]) })
  return null
}

function RegistroPrestador() {
  const [rubros, setRubros] = useState([])
  const [datos, setDatos] = useState(VACIO)
  const [rubrosElegidos, setRubrosElegidos] = useState([])
  const [centro, setCentro] = useState(null)
  const [errores, setErrores] = useState({})
  const [estado, setEstado] = useState('inicial') // inicial | enviando | listo | fallo

  useEffect(() => {
    fetch('/api/prestadores/rubros/')
      .then((r) => r.json())
      .then(setRubros)
      .catch(() => setEstado('fallo'))
  }, [])

  const cambiar = (e) => setDatos({ ...datos, [e.target.name]: e.target.value })

  const alternarRubro = (id) =>
    setRubrosElegidos((actual) =>
      actual.includes(id) ? actual.filter((r) => r !== id) : [...actual, id])

  // Validaciones del lado del cliente (el backend vuelve a validar todo).
  const validar = () => {
    const e = {}
    if (!datos.nombres.trim()) e.nombres = 'Ingresa tus nombres.'
    if (!datos.apellidos.trim()) e.apellidos = 'Ingresa tus apellidos.'
    if (!/^\d{8}$/.test(datos.dni)) e.dni = 'El DNI debe tener 8 dígitos.'
    if (!datos.username.trim()) e.username = 'Elige un nombre de usuario.'
    if (!/^\S+@\S+\.\S+$/.test(datos.email)) e.email = 'Ingresa un correo válido.'
    if (datos.password.length < 8) e.password = 'La contraseña debe tener al menos 8 caracteres.'
    if (rubrosElegidos.length === 0) e.rubros = 'Elige al menos un rubro.'
    if (!centro) e.cobertura = 'Marca tu zona de cobertura en el mapa.'
    return e
  }

  const enviar = async (evento) => {
    evento.preventDefault()
    const e = validar()
    setErrores(e)
    if (Object.keys(e).length) return

    setEstado('enviando')
    try {
      const respuesta = await fetch('/api/prestadores/registro/', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...datos,
          rubros: rubrosElegidos,
          latitud: centro[0].toFixed(6),
          longitud: centro[1].toFixed(6),
          radio_cobertura_km: Number(datos.radio_cobertura_km),
        }),
      })
      if (respuesta.ok) {
        setEstado('listo')
        return
      }
      const cuerpo = await respuesta.json()
      const delServidor = {}
      for (const [campo, mensajes] of Object.entries(cuerpo)) {
        delServidor[campo === 'latitud' || campo === 'longitud' ? 'cobertura' : campo] =
          [].concat(mensajes).join(' ')
      }
      setErrores(delServidor)
      setEstado('inicial')
    } catch {
      setEstado('fallo')
    }
  }

  if (estado === 'listo') {
    return (
      <section className="registro exito" role="status">
        <h2>¡Registro enviado!</h2>
        <p>Tu perfil quedó <strong>Pendiente de verificación</strong>. Te avisaremos cuando sea revisado.</p>
      </section>
    )
  }

  const campo = (nombre, etiqueta, props = {}) => (
    <label className="campo">
      <span>{etiqueta}</span>
      <input name={nombre} value={datos[nombre]} onChange={cambiar} aria-invalid={!!errores[nombre]} {...props} />
      {errores[nombre] && <small className="error">{errores[nombre]}</small>}
    </label>
  )

  return (
    <form className="registro" onSubmit={enviar} noValidate>
      <h2>Regístrate como prestador</h2>
      <p className="ayuda">Completa tus datos, elige tus rubros y marca en el mapa dónde atiendes.</p>

      {estado === 'fallo' && (
        <p className="error caja" role="alert">No pudimos comunicarnos con el servidor. Inténtalo de nuevo.</p>
      )}

      <fieldset>
        <legend>Tus datos</legend>
        <div className="rejilla">
          {campo('nombres', 'Nombres', { autoComplete: 'given-name' })}
          {campo('apellidos', 'Apellidos', { autoComplete: 'family-name' })}
          {campo('dni', 'DNI', { inputMode: 'numeric', maxLength: 8 })}
          {campo('telefono', 'Teléfono (opcional)', { type: 'tel', autoComplete: 'tel' })}
        </div>
      </fieldset>

      <fieldset>
        <legend>Tu cuenta</legend>
        <div className="rejilla">
          {campo('username', 'Usuario', { autoComplete: 'username' })}
          {campo('email', 'Correo', { type: 'email', autoComplete: 'email' })}
          {campo('password', 'Contraseña', { type: 'password', autoComplete: 'new-password' })}
        </div>
      </fieldset>

      <fieldset>
        <legend>Rubros</legend>
        <div className="rubros">
          {rubros.map((r) => (
            <label key={r.id} className={`rubro ${rubrosElegidos.includes(r.id) ? 'activo' : ''}`}>
              <input type="checkbox" checked={rubrosElegidos.includes(r.id)} onChange={() => alternarRubro(r.id)} />
              {r.nombre}
            </label>
          ))}
        </div>
        {errores.rubros && <small className="error">{errores.rubros}</small>}
      </fieldset>

      <fieldset>
        <legend>Área de cobertura</legend>
        <p className="ayuda">Toca el mapa para marcar el centro de tu zona.</p>
        <MapContainer center={LIMA} zoom={11} className="mapa" scrollWheelZoom>
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <SeleccionarCentro onElegir={setCentro} />
          {centro && <Circle center={centro} radius={datos.radio_cobertura_km * 1000} />}
        </MapContainer>
        <label className="campo">
          <span>Radio de cobertura: {datos.radio_cobertura_km} km</span>
          <input type="range" name="radio_cobertura_km" min="1" max="50" value={datos.radio_cobertura_km} onChange={cambiar} />
        </label>
        {errores.cobertura && <small className="error">{errores.cobertura}</small>}
        {errores.radio_cobertura_km && <small className="error">{errores.radio_cobertura_km}</small>}
      </fieldset>

      <button type="submit" className="boton" disabled={estado === 'enviando'}>
        {estado === 'enviando' ? 'Enviando…' : 'Registrarme'}
      </button>
    </form>
  )
}

export default RegistroPrestador
