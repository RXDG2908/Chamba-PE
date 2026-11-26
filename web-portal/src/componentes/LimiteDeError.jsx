import { Component } from 'react'
import { RotateCcw, TriangleAlert } from 'lucide-react'

// Si una pantalla falla al dibujarse, se muestra este aviso en su lugar y el resto de la web
// (cabecera, menú, sesión) sigue funcionando. Antes, un solo error dejaba la página en blanco.
// Con `alternativa` se dibuja otra cosa (por ejemplo, el mapa de respaldo) y se avisa con `onError`.
export default class LimiteDeError extends Component {
  state = { error: null }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    console.error('Error en la interfaz:', error, info?.componentStack)
    this.props.onError?.(error)
  }

  reintentar = () => this.setState({ error: null })

  render() {
    if (!this.state.error) return this.props.children
    if ('alternativa' in this.props) return this.props.alternativa
    return (
      <main className="pagina-estado contenedor" role="alert">
        <p className="etiqueta">Algo salió mal</p>
        <h2><span className="icono-titulo" aria-hidden="true"><TriangleAlert size={22} /></span>No pudimos mostrar esta pantalla</h2>
        <p>Tus datos están a salvo. Vuelve a intentarlo; si sigue pasando, recarga la página.</p>
        <div className="acciones-estado">
          <button type="button" className="boton boton-principal" onClick={this.reintentar}>
            <RotateCcw className="icono" size={18} aria-hidden="true" />Volver a intentar
          </button>
        </div>
      </main>
    )
  }
}
