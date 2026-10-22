import { Navigate, NavLink, Route, Routes } from 'react-router-dom'
import './App.css'
import RegistroPrestador from './RegistroPrestador.jsx'
import ResultadosPrestadores from './ResultadosPrestadores.jsx'
import UploadCertificado from './UploadCertificado.jsx'
import RevisionDocumentos from './RevisionDocumentos.jsx'

function App() {
  return (
    <>
      <header className="cabecera">
        <div className="contenedor">
          <h1>Chamba<span>PE</span></h1>
          <p>Talento local, cerca de ti.</p>
          <nav className="menu">
            <NavLink to="/" end>Registro de prestador</NavLink>
            <NavLink to="/resultados">Resultados</NavLink>
            <NavLink to="/certificaciones">Cargar certificado</NavLink>
            <NavLink to="/administracion/revisiones">Revisión de documentos</NavLink>
          </nav>
        </div>
      </header>

      <Routes>
        <Route path="/" element={<RegistroPrestador />} />
        <Route path="/resultados" element={<ResultadosPrestadores />} />
        <Route path="/certificaciones" element={<UploadCertificado />} />
        <Route path="/administracion/revisiones" element={<RevisionDocumentos />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  )
}

export default App
