import { useEffect, useMemo, useRef, useState } from 'react'
import { Building2, Camera, KeyRound, Lock, Save, Trash2, UserRound } from 'lucide-react'
import { enviarJson, erroresPorCampo, leerCookie } from '../../nucleo/api.js'
import { iniciales } from '../../nucleo/formatos.js'
import Cargando from '../../componentes/Cargando.jsx'
import MapaCobertura from '../../componentes/mapa/MapaCobertura.jsx'
import '../publico/RegistroPrestador.css'
import './MiPerfil.css'

const FOTO_MAXIMO = 5 * 1024 * 1024
const FOTO_TIPOS = ['image/jpeg', 'image/png', 'image/webp']
const CAMPOS_CLIENTE = ['nombres', 'apellidos', 'email', 'telefono']
const CAMPOS_PRESTADOR = [...CAMPOS_CLIENTE, 'tipo', 'nombre_comercial', 'ruc', 'descripcion', 'rubros',
  'radio_cobertura_km', 'latitud', 'longitud']

// Mensaje de estado bajo cada sección: { tipo: 'ok' | 'error', texto }.
function Aviso({ aviso }) {
  if (!aviso) return null
  return <p className={aviso.tipo === 'ok' ? 'aviso-ok' : 'error caja'} role={aviso.tipo === 'ok' ? 'status' : 'alert'}>{aviso.texto}</p>
}

function SeccionFoto({ perfil, alCambiar }) {
  const entrada = useRef(null)
  const [estado, setEstado] = useState('listo') // listo | subiendo
  const [aviso, setAviso] = useState(null)

  const subir = async (evento) => {
    const archivo = evento.target.files?.[0]
    evento.target.value = ''
    if (!archivo) return
    if (!FOTO_TIPOS.includes(archivo.type)) return setAviso({ tipo: 'error', texto: 'Sube una foto JPG, PNG o WEBP.' })
    if (archivo.size > FOTO_MAXIMO) return setAviso({ tipo: 'error', texto: 'La foto pesa más de 5 MB.' })
    setEstado('subiendo')
    setAviso(null)
    try {
      const formulario = new FormData()
      formulario.append('foto', archivo)
      const respuesta = await fetch('/api/usuarios/perfil/foto/', {
        method: 'POST', headers: { 'X-CSRFToken': leerCookie('csrftoken') }, body: formulario,
      })
      const cuerpo = await respuesta.json().catch(() => ({}))
      if (!respuesta.ok) throw new Error(erroresPorCampo(cuerpo).foto || 'No se pudo subir la foto.')
      alCambiar({ foto: cuerpo.foto })
      setAviso({ tipo: 'ok', texto: 'Foto actualizada.' })
    } catch (error) {
      setAviso({ tipo: 'error', texto: error.message || 'No se pudo subir la foto.' })
    } finally {
      setEstado('listo')
    }
  }

  const quitar = async () => {
    setEstado('subiendo')
    setAviso(null)
    try {
      const respuesta = await enviarJson('/api/usuarios/perfil/foto/', 'DELETE')
      if (!respuesta.ok) throw new Error()
      alCambiar({ foto: null })
      setAviso({ tipo: 'ok', texto: 'Quitaste tu foto.' })
    } catch {
      setAviso({ tipo: 'error', texto: 'No se pudo quitar la foto.' })
    } finally {
      setEstado('listo')
    }
  }

  return (
    <section className="perfil-seccion perfil-foto" aria-labelledby="titulo-foto">
      <div className="perfil-avatar" aria-hidden={!perfil.foto}>
        {perfil.foto
          ? <img src={perfil.foto} alt="Tu foto de perfil" />
          : <span>{iniciales(perfil.nombres || perfil.username, perfil.apellidos || '')}</span>}
      </div>
      <div>
        <h3 id="titulo-foto">Foto de perfil</h3>
        <p className="ayuda">
          {perfil.rol === 'prestador'
            ? 'Los clientes la ven en la búsqueda. Una foto clara de tu rostro o el logo de tu empresa da confianza.'
            : 'Aparece junto a tu nombre en la cabecera de tu cuenta.'}
          {' '}JPG, PNG o WEBP de hasta 5 MB; se recorta cuadrada.
        </p>
        <div className="perfil-acciones">
          <button type="button" className="boton-secundario" onClick={() => entrada.current?.click()} disabled={estado === 'subiendo'}>
            <Camera size={16} aria-hidden="true" /> {estado === 'subiendo' ? 'Guardando…' : perfil.foto ? 'Cambiar foto' : 'Subir foto'}
          </button>
          {perfil.foto && (
            <button type="button" className="boton-texto" onClick={quitar} disabled={estado === 'subiendo'}>
              <Trash2 size={16} aria-hidden="true" /> Quitar
            </button>
          )}
          <input ref={entrada} type="file" accept={FOTO_TIPOS.join(',')} onChange={subir} hidden
            aria-label="Elegir foto de perfil" />
        </div>
        <Aviso aviso={aviso} />
      </div>
    </section>
  )
}

function SeccionContrasena({ perfil }) {
  const [datos, setDatos] = useState({ actual: '', nueva: '', confirmar: '' })
  const [errores, setErrores] = useState({})
  const [aviso, setAviso] = useState(null)
  const [enviando, setEnviando] = useState(false)

  if (!perfil.tiene_contrasena) {
    return (
      <section className="perfil-seccion" aria-labelledby="titulo-contrasena">
        <h3 id="titulo-contrasena"><KeyRound size={18} aria-hidden="true" /> Contraseña</h3>
        <p className="ayuda">Tu cuenta entra con la app (Google/Firebase), así que no tiene contraseña aquí.</p>
      </section>
    )
  }

  const cambiar = (e) => setDatos({ ...datos, [e.target.name]: e.target.value })
  const enviar = async (evento) => {
    evento.preventDefault()
    const e = {}
    if (!datos.actual) e.actual = 'Escribe tu contraseña actual.'
    if (datos.nueva.length < 8) e.nueva = 'La contraseña nueva debe tener al menos 8 caracteres.'
    else if (datos.nueva !== datos.confirmar) e.confirmar = 'Las contraseñas no coinciden.'
    setErrores(e)
    setAviso(null)
    if (Object.keys(e).length) return
    setEnviando(true)
    try {
      const respuesta = await enviarJson('/api/usuarios/perfil/contrasena/', 'POST', { actual: datos.actual, nueva: datos.nueva })
      if (respuesta.ok) {
        setDatos({ actual: '', nueva: '', confirmar: '' })
        setAviso({ tipo: 'ok', texto: 'Cambiaste tu contraseña.' })
        return
      }
      const cuerpo = await respuesta.json().catch(() => ({}))
      setErrores(erroresPorCampo(cuerpo))
      if (cuerpo.detail) setAviso({ tipo: 'error', texto: cuerpo.detail })
    } catch {
      setAviso({ tipo: 'error', texto: 'No pudimos comunicarnos con el servidor.' })
    } finally {
      setEnviando(false)
    }
  }

  const campo = (nombre, etiqueta, autoComplete) => (
    <label className="campo">
      <span>{etiqueta}</span>
      <input type="password" name={nombre} value={datos[nombre]} onChange={cambiar} autoComplete={autoComplete}
        aria-invalid={!!errores[nombre]} />
      {errores[nombre] && <small className="error">{errores[nombre]}</small>}
    </label>
  )

  return (
    <form className="perfil-seccion" onSubmit={enviar} noValidate aria-labelledby="titulo-contrasena">
      <h3 id="titulo-contrasena"><KeyRound size={18} aria-hidden="true" /> Contraseña</h3>
      {/* Para los gestores de contraseñas: el usuario de la cuenta a la que pertenece. */}
      <input type="text" name="username" value={perfil.username} autoComplete="username" readOnly hidden />
      <div className="rejilla">
        {campo('actual', 'Contraseña actual', 'current-password')}
        {campo('nueva', 'Contraseña nueva', 'new-password')}
        {campo('confirmar', 'Repite la contraseña nueva', 'new-password')}
      </div>
      <Aviso aviso={aviso} />
      <button type="submit" className="boton-secundario" disabled={enviando}>
        <KeyRound size={16} aria-hidden="true" /> {enviando ? 'Cambiando…' : 'Cambiar contraseña'}
      </button>
    </form>
  )
}

function MiPerfil({ usuario, alActualizar }) {
  const [perfil, setPerfil] = useState(null)
  const [datos, setDatos] = useState(null)
  const [rubros, setRubros] = useState([])
  const [errores, setErrores] = useState({})
  const [estado, setEstado] = useState('cargando') // cargando | listo | guardando | fallo
  const [aviso, setAviso] = useState(null)
  const esPrestador = usuario?.rol === 'prestador'
  // Misma referencia mientras no se mueva el centro: el mapa no redibuja el círculo en cada tecla.
  const latitud = datos?.latitud
  const longitud = datos?.longitud
  const centro = useMemo(() => (latitud == null ? null : [latitud, longitud]), [latitud, longitud])

  useEffect(() => {
    fetch('/api/usuarios/perfil/')
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then((cuerpo) => {
        setPerfil(cuerpo)
        setDatos(cuerpo)
        setEstado('listo')
      })
      .catch(() => setEstado('fallo'))
    if (esPrestador) {
      fetch('/api/prestadores/rubros/').then((r) => (r.ok ? r.json() : [])).then(setRubros).catch(() => setRubros([]))
    }
  }, [esPrestador])

  // La cabecera (nombre y foto) se actualiza con /yo/ después de cada cambio.
  const refrescarCabecera = () => {
    fetch('/api/usuarios/yo/').then((r) => (r.ok ? r.json() : null)).then((yo) => yo && alActualizar?.(yo)).catch(() => {})
  }

  if (estado === 'cargando') return <Cargando className="registro" texto="Cargando tu perfil…" />
  if (!perfil) return <p className="registro error" role="alert">No pudimos cargar tu perfil. Recarga la página.</p>

  const cambiar = (e) => {
    const valor = e.target.type === 'range' ? Number(e.target.value) : e.target.value
    setDatos({ ...datos, [e.target.name]: valor })
  }
  const alternarRubro = (id) => setDatos({
    ...datos, rubros: datos.rubros.includes(id) ? datos.rubros.filter((r) => r !== id) : [...datos.rubros, id],
  })

  const validar = () => {
    const e = {}
    if (!datos.nombres.trim()) e.nombres = 'Ingresa tus nombres.'
    if (!datos.apellidos.trim()) e.apellidos = 'Ingresa tus apellidos.'
    if (!/^\S+@\S+\.\S+$/.test(datos.email)) e.email = 'Ingresa un correo válido.'
    if (esPrestador) {
      if (datos.tipo === 'empresa' && !datos.nombre_comercial.trim()) e.nombre_comercial = 'Escribe el nombre de tu empresa.'
      if (datos.ruc && !/^(10|15|17|20)\d{9}$/.test(datos.ruc)) e.ruc = 'El RUC tiene 11 dígitos y empieza con 10, 15, 17 o 20.'
      if (datos.rubros.length === 0) e.rubros = 'Elige al menos un rubro.'
    }
    return e
  }

  const guardar = async (evento) => {
    evento.preventDefault()
    const e = validar()
    setErrores(e)
    setAviso(null)
    if (Object.keys(e).length) return
    setEstado('guardando')
    // Solo se envía lo que cambió: así un prestador verificado no «toca» su nombre del DNI.
    const campos = esPrestador ? CAMPOS_PRESTADOR : CAMPOS_CLIENTE
    const cambios = Object.fromEntries(campos
      .filter((c) => JSON.stringify(datos[c]) !== JSON.stringify(perfil[c]))
      .map((c) => [c, datos[c]]))
    if (Object.keys(cambios).length === 0) {
      setEstado('listo')
      setAviso({ tipo: 'ok', texto: 'No hay cambios por guardar.' })
      return
    }
    try {
      const respuesta = await enviarJson('/api/usuarios/perfil/', 'PATCH', cambios)
      const cuerpo = await respuesta.json().catch(() => ({}))
      if (!respuesta.ok) {
        setErrores(erroresPorCampo(cuerpo))
        setAviso({ tipo: 'error', texto: 'Revisa los campos marcados.' })
        setEstado('listo')
        return
      }
      setPerfil(cuerpo)
      setDatos(cuerpo)
      setAviso({ tipo: 'ok', texto: 'Guardamos tu perfil.' })
      setEstado('listo')
      refrescarCabecera()
    } catch {
      setAviso({ tipo: 'error', texto: 'No pudimos comunicarnos con el servidor. Inténtalo de nuevo.' })
      setEstado('listo')
    }
  }

  const alCambiarFoto = (cambio) => {
    setPerfil({ ...perfil, ...cambio })
    refrescarCabecera()
  }

  const campo = (nombre, etiqueta, props = {}) => (
    <label className="campo">
      <span>{etiqueta}</span>
      <input name={nombre} value={datos[nombre] ?? ''} onChange={cambiar} aria-invalid={!!errores[nombre]} {...props} />
      {errores[nombre] && <small className="error">{errores[nombre]}</small>}
    </label>
  )

  const nombresBloqueados = esPrestador && !perfil.nombres_editables
  const nombreVisible = datos.tipo === 'empresa' && datos.nombre_comercial?.trim()
    ? datos.nombre_comercial.trim()
    : `${datos.nombres} ${datos.apellidos}`.trim()

  return (
    <div className="registro mi-perfil">
      <h2><span className="icono-titulo" aria-hidden="true"><UserRound size={22} /></span>Mi perfil</h2>
      <p className="ayuda">
        {esPrestador
          ? 'Así te presentas ante los clientes. Los cambios se ven en la búsqueda en cuanto los guardas.'
          : 'Tus datos de cuenta: tu foto, tu nombre, cómo contactarte y tu contraseña.'}
      </p>

      <SeccionFoto perfil={perfil} alCambiar={alCambiarFoto} />

      <form onSubmit={guardar} noValidate>
        <fieldset>
          <legend>Tus datos</legend>
          {nombresBloqueados && (
            <p className="perfil-nota"><Lock size={15} aria-hidden="true" /> Tu nombre ya se verificó con tu DNI y no se puede cambiar. Si hay un error, escribe a administración.</p>
          )}
          <div className="rejilla">
            {campo('nombres', 'Nombres', { autoComplete: 'given-name', maxLength: 100, disabled: nombresBloqueados })}
            {campo('apellidos', 'Apellidos', { autoComplete: 'family-name', maxLength: 100, disabled: nombresBloqueados })}
            {campo('email', 'Correo', { type: 'email', autoComplete: 'email', maxLength: 254 })}
            {campo('telefono', 'Teléfono', { type: 'tel', autoComplete: 'tel', inputMode: 'tel', maxLength: 16, placeholder: '987 654 321' })}
            <label className="campo">
              <span>Usuario</span>
              <input value={perfil.username} disabled />
            </label>
            {esPrestador && (
              <label className="campo">
                <span>DNI</span>
                <input value={perfil.dni} disabled />
              </label>
            )}
          </div>
        </fieldset>

        {esPrestador && (
          <>
            <fieldset>
              <legend>¿Cómo te presentas?</legend>
              <div className="perfil-tipos" role="radiogroup" aria-label="Tipo de prestador">
                {[
                  ['independiente', UserRound, 'Independiente', 'Con tu nombre y apellidos.'],
                  ['empresa', Building2, 'Empresa', 'Con el nombre de tu negocio y tu RUC.'],
                ].map(([valor, Icono, titulo, texto]) => (
                  <label key={valor} className={`perfil-tipo ${datos.tipo === valor ? 'activo' : ''}`}>
                    <input type="radio" name="tipo" value={valor} checked={datos.tipo === valor} onChange={cambiar} />
                    <Icono size={22} aria-hidden="true" />
                    <span><strong>{titulo}</strong><small>{texto}</small></span>
                  </label>
                ))}
              </div>
              {datos.tipo === 'empresa' && (
                <div className="rejilla perfil-empresa">
                  {campo('nombre_comercial', 'Nombre de la empresa', { maxLength: 120, autoComplete: 'organization' })}
                  {campo('ruc', 'RUC (opcional)', { inputMode: 'numeric', maxLength: 11 })}
                </div>
              )}
              <p className="perfil-vista">Los clientes te verán como <strong>{nombreVisible || '—'}</strong></p>
              <label className="campo">
                <span>Sobre ti o tu empresa</span>
                <textarea name="descripcion" value={datos.descripcion} onChange={cambiar} maxLength={500} rows={4}
                  placeholder="Experiencia, especialidades, horarios…" aria-invalid={!!errores.descripcion} />
                <small className="contador">{datos.descripcion.length}/500</small>
                {errores.descripcion && <small className="error">{errores.descripcion}</small>}
              </label>
            </fieldset>

            <fieldset>
              <legend>Rubros</legend>
              <div className="rubros">
                {rubros.map((r) => (
                  <label key={r.id} className={`rubro ${datos.rubros.includes(r.id) ? 'activo' : ''}`}>
                    <input type="checkbox" checked={datos.rubros.includes(r.id)} onChange={() => alternarRubro(r.id)} />
                    {r.nombre}
                  </label>
                ))}
              </div>
              {errores.rubros && <small className="error">{errores.rubros}</small>}
            </fieldset>

            <fieldset>
              <legend>Área de cobertura</legend>
              <p className="ayuda">Toca el mapa para mover el centro de tu zona.</p>
              <MapaCobertura centro={centro} radioKm={datos.radio_cobertura_km}
                onElegir={([latitud, longitud]) => setDatos({ ...datos, latitud, longitud })} />
              <label className="campo">
                <span>Radio de cobertura: {datos.radio_cobertura_km} km</span>
                <input type="range" name="radio_cobertura_km" min="1" max="50" value={datos.radio_cobertura_km} onChange={cambiar} />
              </label>
              {(errores.latitud || errores.longitud || errores.radio_cobertura_km) && (
                <small className="error">{errores.latitud || errores.longitud || errores.radio_cobertura_km}</small>
              )}
            </fieldset>
          </>
        )}

        <Aviso aviso={aviso} />
        <button type="submit" className="boton" disabled={estado === 'guardando'}>
          <Save className="icono" size={18} aria-hidden="true" /> {estado === 'guardando' ? 'Guardando…' : 'Guardar cambios'}
        </button>
      </form>

      <SeccionContrasena perfil={perfil} />
    </div>
  )
}

export default MiPerfil
