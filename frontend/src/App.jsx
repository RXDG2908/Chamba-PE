import './App.css'

// Datos ficticios para el primer avance visual de T3.2.
const trabajadores = [
  { id: 1, nombre: 'Carlos Mendoza', iniciales: 'CM', rubro: 'Gasfitería', calificacion: '4,8', distancia: '1,2 km', color: 'verde' },
  { id: 2, nombre: 'Lucía Torres', iniciales: 'LT', rubro: 'Electricidad', calificacion: '4,9', distancia: '2,5 km', color: 'azul' },
  { id: 3, nombre: 'José Ramírez', iniciales: 'JR', rubro: 'Carpintería', calificacion: '4,7', distancia: '3,0 km', color: 'naranja' },
]

function App() {
  return (
    <>
      <header className="cabecera">
        <div className="contenedor">
          <h1>Chamba<span>PE</span></h1>
          <p>Talento local, cerca de ti.</p>
        </div>
      </header>

      <main className="contenedor resultados">
        <div className="introduccion">
          <p className="etiqueta">PRESTADORES DE SERVICIOS</p>
          <h2>Encuentra una mano para tu hogar</h2>
          <p>Conoce a los trabajadores y sus especialidades.</p>
        </div>

        <aside className="aviso" aria-label="Información sobre los datos">
          <strong>Datos de ejemplo.</strong> Los perfiles, calificaciones y distancias
          son ficticios y se muestran para esta demostración visual.
        </aside>

        <section aria-labelledby="titulo-resultados">
          <div className="resumen">
            <h3 id="titulo-resultados">Lista de prestadores</h3>
            <span>{trabajadores.length} trabajadores</span>
          </div>
          <ul className="lista-prestadores">
            {trabajadores.map((trabajador) => (
              <li key={trabajador.id} className="tarjeta">
                <div className={`avatar ${trabajador.color}`} aria-hidden="true">
                  {trabajador.iniciales}
                </div>
                <div className="perfil">
                  <h4>{trabajador.nombre}</h4>
                  <p className="rubro">{trabajador.rubro}</p>
                </div>
                <dl className="detalles">
                  <div>
                    <dt>Calificación</dt>
                    <dd><span className="estrella" aria-hidden="true">★</span> {trabajador.calificacion} <span className="escala">/ 5</span></dd>
                  </div>
                  <div>
                    <dt>Distancia</dt>
                    <dd>{trabajador.distancia}</dd>
                  </div>
                </dl>
              </li>
            ))}
          </ul>
        </section>
        <p className="nota">ChambaPE · Primer avance visual de la pantalla de resultados.</p>
      </main>
    </>
  )
}

export default App
