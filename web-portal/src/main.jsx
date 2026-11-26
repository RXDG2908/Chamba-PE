import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './estilos/index.css'
import App from './App.jsx'
// Después de App: el tema ajusta los estilos de todas las pantallas.
import './estilos/Tema.css'
// Al final: movimiento, iconos y detalles del navegador.
import './estilos/Movimiento.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
