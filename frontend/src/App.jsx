import { useState } from 'react'
import './App.css'
import RegistroPrestador from './RegistroPrestador.jsx'
import ResultadosPrestadores from './ResultadosPrestadores.jsx'

function App() {
  const [vista, setVista] = useState('registro')

  return (
    <>
      <header className="cabecera">
        <div className="contenedor">
          <h1>Chamba<span>PE</span></h1>
          <p>Talento local, cerca de ti.</p>
          <nav className="menu">
            <button type="button" aria-current={vista === 'registro'} onClick={() => setVista('registro')}>Registro de prestador</button>
            <button type="button" aria-current={vista === 'resultados'} onClick={() => setVista('resultados')}>Resultados</button>
          </nav>
        </div>
      </header>

      {vista === 'registro' ? <RegistroPrestador /> : <ResultadosPrestadores />}
    </>
  )
}

export default App
