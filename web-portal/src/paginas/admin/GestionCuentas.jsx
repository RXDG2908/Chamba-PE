// HU-13 (T13.2): el administrador suspende, bloquea y reactiva cuentas indicando el motivo.
import { useEffect, useState } from 'react'
import { enviarJson, erroresPorCampo, NOMBRE_ROL } from '../../nucleo/api.js'
import './RevisionDocumentos.css'
import './GestionCuentas.css'
import { Ban, RotateCcw, Search, UserCheck, UserX, Users, X } from 'lucide-react'
import Cargando from '../../componentes/Cargando.jsx'
import { formatoEntero } from '../../nucleo/formatos.js'

const API = '/api/administracion/cuentas/'
const FILTROS = [['', 'Todas'], ['activa', 'Activas'], ['suspendida', 'Suspendidas'], ['bloqueada', 'Bloqueadas']]
const ESTADO = { activa: 'Activa', suspendida: 'Suspendida', bloqueada: 'Bloqueada' }

function fecha(iso) {
  return new Date(iso).toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' })
}

function FormularioSancion({ cuenta, onListo, onCancelar }) {
  const [tipo, setTipo] = useState('suspension')
  const [dias, setDias] = useState('7')
  const [motivo, setMotivo] = useState('')
  const [errores, setErrores] = useState({})
  const [enviando, setEnviando] = useState(false)
  const id = `sancion-${cuenta.id}`

  const enviar = async (evento) => {
    evento.preventDefault()
    const datos = { tipo, motivo: motivo.trim() }
    if (tipo === 'suspension') datos.dias = Number(dias)
    setEnviando(true)
    setErrores({})
    try {
      const respuesta = await enviarJson(`${API}${cuenta.id}/sancionar/`, 'POST', datos)
      const cuerpo = await respuesta.json().catch(() => ({}))
      if (respuesta.ok) onListo(cuerpo)
      else if (respuesta.status === 403) setErrores({ detail: 'Tu sesión de administrador expiró. Vuelve a iniciar sesión.' })
      else setErrores(erroresPorCampo(cuerpo))
    } catch {
      setErrores({ detail: 'No se pudo conectar con el servidor.' })
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form className="cuenta-formulario" onSubmit={enviar} noValidate>
      <fieldset>
        <legend>Acción</legend>
        <label><input type="radio" name={`${id}-tipo`} value="suspension" checked={tipo === 'suspension'} onChange={() => setTipo('suspension')} /> Suspender por unos días</label>
        <label><input type="radio" name={`${id}-tipo`} value="bloqueo" checked={tipo === 'bloqueo'} onChange={() => setTipo('bloqueo')} /> Bloquear hasta reactivarla</label>
      </fieldset>
      {tipo === 'suspension' && (
        <div className="cuenta-campo">
          <label htmlFor={`${id}-dias`}>Días de suspensión (1 a 365)</label>
          <input id={`${id}-dias`} type="number" min="1" max="365" inputMode="numeric" value={dias}
            onChange={(e) => setDias(e.target.value)} aria-invalid={Boolean(errores.dias)} />
          {errores.dias && <p className="cuenta-error">{errores.dias}</p>}
        </div>
      )}
      <div className="cuenta-campo">
        <label htmlFor={`${id}-motivo`}>Motivo (lo verá la persona al intentar entrar)</label>
        <textarea id={`${id}-motivo`} rows="3" maxLength="500" value={motivo}
          onChange={(e) => setMotivo(e.target.value)} aria-invalid={Boolean(errores.motivo)} />
        {errores.motivo && <p className="cuenta-error">{errores.motivo}</p>}
      </div>
      {(errores.detail || errores.tipo) && <p className="cuenta-error" role="alert">{errores.detail || errores.tipo}</p>}
      <div className="revision-botones">
        <button type="submit" className="boton-rechazar" disabled={enviando}>
          <Ban className="icono" size={18} aria-hidden="true" />
          {enviando ? 'Guardando…' : tipo === 'bloqueo' ? 'Bloquear cuenta' : 'Suspender cuenta'}
        </button>
        <button type="button" className="boton-secundario" onClick={onCancelar} disabled={enviando}><X className="icono" size={18} aria-hidden="true" />Cancelar</button>
      </div>
    </form>
  )
}

function FormularioReactivar({ cuenta, onListo, onCancelar }) {
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  const enviar = async (evento) => {
    evento.preventDefault()
    setEnviando(true)
    setError('')
    try {
      const respuesta = await enviarJson(`${API}${cuenta.id}/reactivar/`, 'POST', { motivo: motivo.trim() })
      const cuerpo = await respuesta.json().catch(() => ({}))
      if (respuesta.ok) onListo(cuerpo)
      else setError(cuerpo.motivo?.[0] || cuerpo.detail || 'No se pudo reactivar la cuenta.')
    } catch {
      setError('No se pudo conectar con el servidor.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form className="cuenta-formulario" onSubmit={enviar} noValidate>
      <div className="cuenta-campo">
        <label htmlFor={`reactivar-${cuenta.id}`}>¿Por qué se reactiva?</label>
        <textarea id={`reactivar-${cuenta.id}`} rows="2" maxLength="500" value={motivo} onChange={(e) => setMotivo(e.target.value)} />
      </div>
      {error && <p className="cuenta-error" role="alert">{error}</p>}
      <div className="revision-botones">
        <button type="submit" className="boton-aprobar" disabled={enviando}><RotateCcw className="icono" size={18} aria-hidden="true" />{enviando ? 'Guardando…' : 'Reactivar cuenta'}</button>
        <button type="button" className="boton-secundario" onClick={onCancelar} disabled={enviando}><X className="icono" size={18} aria-hidden="true" />Cancelar</button>
      </div>
    </form>
  )
}

function TarjetaCuenta({ cuenta, onCambio }) {
  const [accion, setAccion] = useState(null)
  const { sancion } = cuenta
  const listo = (actualizada) => {
    setAccion(null)
    onCambio(actualizada)
  }

  return (
    <li className={`revision-tarjeta cuenta-${cuenta.estado}`}>
      <div className="revision-cabecera">
        <div className="revision-datos">
          <h3>{cuenta.nombre || cuenta.usuario}</h3>
          <dl>
            <div><dt>Usuario</dt><dd>{cuenta.usuario}</dd></div>
            <div><dt>Rol</dt><dd>{NOMBRE_ROL[cuenta.rol] ?? '—'}</dd></div>
            <div><dt>Correo</dt><dd>{cuenta.correo || '—'}</dd></div>
            {cuenta.dni && <div><dt>DNI</dt><dd>{cuenta.dni}</dd></div>}
          </dl>
        </div>
        <span className={`cuenta-estado cuenta-estado-${cuenta.estado}`}>{ESTADO[cuenta.estado]}</span>
      </div>

      {sancion && (
        <p className="cuenta-sancion">
          <strong>{sancion.tipo_display}</strong>
          {sancion.hasta ? ` hasta el ${fecha(sancion.hasta)}` : ' sin fecha de fin'}
          {` · por ${sancion.creada_por ?? '—'} el ${fecha(sancion.creada)}`}
          <br />Motivo: {sancion.motivo}
        </p>
      )}

      <div className="revision-acciones">
        {accion === 'sancionar' && <FormularioSancion cuenta={cuenta} onListo={listo} onCancelar={() => setAccion(null)} />}
        {accion === 'reactivar' && <FormularioReactivar cuenta={cuenta} onListo={listo} onCancelar={() => setAccion(null)} />}
        {!accion && (
          <div className="revision-botones">
            {cuenta.estado === 'activa'
              ? <button type="button" className="boton-rechazar" onClick={() => setAccion('sancionar')}><UserX className="icono" size={18} aria-hidden="true" />Suspender o bloquear</button>
              : <button type="button" className="boton-aprobar" onClick={() => setAccion('reactivar')}><UserCheck className="icono" size={18} aria-hidden="true" />Reactivar</button>}
          </div>
        )}
      </div>
    </li>
  )
}

export default function GestionCuentas() {
  const [cuentas, setCuentas] = useState([])
  const [estado, setEstado] = useState('cargando')
  const [filtro, setFiltro] = useState('')
  const [texto, setTexto] = useState('')
  const [busqueda, setBusqueda] = useState('')

  useEffect(() => {
    const controlador = new AbortController()
    const parametros = new URLSearchParams()
    if (filtro) parametros.set('estado', filtro)
    if (busqueda) parametros.set('q', busqueda)
    fetch(`${API}?${parametros}`, { signal: controlador.signal })
      .then((respuesta) => {
        if (respuesta.status === 403) throw new Error('sin-permiso')
        if (!respuesta.ok) throw new Error('error')
        return respuesta.json()
      })
      .then((datos) => {
        setCuentas(datos)
        setEstado('listo')
      })
      .catch((error) => {
        if (!controlador.signal.aborted) setEstado(error.message === 'sin-permiso' ? 'sin-permiso' : 'error')
      })
    return () => controlador.abort()
  }, [filtro, busqueda])

  const buscar = (evento) => {
    evento.preventDefault()
    setEstado('cargando')
    setBusqueda(texto.trim())
  }

  const elegirFiltro = (valor) => {
    if (valor === filtro) return
    setEstado('cargando')
    setFiltro(valor)
  }

  // Tras sancionar o reactivar, la tarjeta se actualiza; si ya no encaja en el filtro, sale de la lista.
  const actualizar = (actualizada) => {
    setCuentas((actuales) => actuales
      .map((c) => (c.id === actualizada.id ? actualizada : c))
      .filter((c) => !filtro || c.estado === filtro))
  }

  return (
    <main className="contenedor revision">
      <p className="etiqueta">ADMINISTRACIÓN</p>
      <div className="revision-titulo">
        <div>
          <h2><span className="icono-titulo" aria-hidden="true"><Users size={22} /></span>Cuentas de usuarios</h2>
          <p>Suspende o bloquea cuentas por incumplimiento, siempre con el motivo. Mientras dure, la cuenta no puede iniciar sesión ni enviar o recibir solicitudes.</p>
        </div>
      </div>

      <form className="cuentas-filtros" onSubmit={buscar} role="search">
        <label htmlFor="buscar-cuenta" className="visualmente-oculto">Buscar cuenta</label>
        <input id="buscar-cuenta" type="search" placeholder="Nombre, usuario, correo o DNI" value={texto} onChange={(e) => setTexto(e.target.value)} />
        <button type="submit" className="boton-secundario"><Search className="icono" size={18} aria-hidden="true" />Buscar</button>
      </form>
      <div className="cuentas-pestanas" role="group" aria-label="Filtrar por estado">
        {FILTROS.map(([valor, etiqueta]) => (
          <button key={valor} type="button" aria-pressed={filtro === valor} onClick={() => elegirFiltro(valor)}>{etiqueta}</button>
        ))}
      </div>

      {estado === 'cargando' && <Cargando className="revision-aviso" texto="Cargando cuentas…" />}
      {estado === 'sin-permiso' && <p className="revision-aviso revision-aviso-error" role="alert">Esta pantalla es solo para administradores.</p>}
      {estado === 'error' && <p className="revision-aviso revision-aviso-error" role="alert">No se pudieron cargar las cuentas. Revisa que Django esté encendido.</p>}
      {estado === 'listo' && cuentas.length === 0 && <p className="revision-aviso">No hay cuentas con ese filtro.</p>}
      {estado === 'listo' && cuentas.length > 0 && (
        <>
          <p className="revision-resumen">{formatoEntero.format(cuentas.length)} {cuentas.length === 1 ? 'cuenta' : 'cuentas'}</p>
          <ul className="revision-lista">
            {cuentas.map((cuenta) => <TarjetaCuenta key={cuenta.id} cuenta={cuenta} onCambio={actualizar} />)}
          </ul>
        </>
      )}
    </main>
  )
}
