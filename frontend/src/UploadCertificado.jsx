import { useState } from 'react'
import './UploadCertificado.css'

const MAX_FILE_SIZE = 5 * 1024 * 1024; 
const ALLOWED_EXTENSIONS = ['application/pdf', 'image/jpeg', 'image/jpg'];

export default function UploadCertificado() {
  const [archivo, setArchivo] = useState(null);
  const [estadoCarga, setEstadoCarga] = useState('inicial'); 
  const [mensajeError, setMensajeError] = useState('');
  const [datosRespuesta, setDatosRespuesta] = useState(null);

  const manejarSeleccionArchivo = (e) => {
    const file = e.target.files[0];
    setMensajeError('');
    setDatosRespuesta(null);

    if (!file) return;

    if (file.size > MAX_FILE_SIZE) {
      setMensajeError('El archivo excede el límite permitido de 5 MB.');
      setArchivo(null);
      return;
    }

    if (!ALLOWED_EXTENSIONS.includes(file.type)) {
      setMensajeError('Formato no válido. Solo se permiten archivos PDF o JPG.');
      setArchivo(null);
      return;
    }

    setArchivo(file);
  };

  const enviarSolicitud = async (e) => {
    e.preventDefault();
    if (!archivo) {
      setMensajeError('Por favor selecciona un archivo válido antes de enviar.');
      return;
    }

    setEstadoCarga('cargando');
    setMensajeError('');

    const formData = new FormData();
    formData.append('file', archivo);

    try {
      const respuesta = await fetch('/api/certificaciones/upload/', {
        method: 'POST',
        body: formData,
      });

      const resultado = await respuesta.json();

      if (respuesta.ok) {
        setDatosRespuesta(resultado);
        setEstadoCarga('exito');
      } else {
        setMensajeError(resultado.error || 'Ocurrió un error al procesar la solicitud.');
        setEstadoCarga('error');
      }
    } catch (err) {
      setMensajeError('No se pudo establecer conexión con el servidor.');
      setEstadoCarga('error');
    }
  };

  return (
    <div className="container-certificacion" style={{ maxWidth: '600px', margin: '40px auto', padding: '20px', fontFamily: 'Arial' }}>
      <h2>Envío de Solicitud de Certificación</h2>
      <p className="descripcion">Adjunta tu documento acreditativo en formato PDF o JPG (Máximo 5 MB).</p>

      <form onSubmit={enviarSolicitud} className="form-certificacion" style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
        <input 
          type="file" 
          accept=".pdf,.jpg,.jpeg" 
          onChange={manejarSeleccionArchivo}
          style={{ padding: '10px', border: '1px solid #ccc', borderRadius: '4px' }}
        />

        <button 
          type="submit" 
          disabled={estadoCarga === 'cargando' || !archivo}
          style={{ padding: '10px', backgroundColor: '#007BFF', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
        >
          {estadoCarga === 'cargando' ? 'Enviando solicitud...' : 'Enviar Certificación'}
        </button>
      </form>

      {/* Mensaje de Error en Cliente o Servidor */}
      {mensajeError && (
        <div style={{ marginTop: '20px', padding: '12px', backgroundColor: '#f8d7da', color: '#721c24', borderRadius: '4px' }}>
          <strong>Error: </strong> {mensajeError}
        </div>
      )}

      {/* Pantalla de Visualización y Cambio de Estado (Éxito) */}
      {estadoCarga === 'exito' && datosRespuesta && (
        <div style={{ marginTop: '25px', padding: '20px', backgroundColor: '#d4edda', color: '#155724', borderRadius: '6px', border: '1px solid #c3e6cb' }}>
          <h3>¡Solicitud Enviada con Éxito!</h3>
          <p><strong>Archivo registrado:</strong> {datosRespuesta.file_name}</p>
          <div style={{ margin: '15px 0' }}>
            <strong>Estado de la Solicitud: </strong>
            <span style={{ 
              display: 'inline-block', 
              marginLeft: '8px', 
              padding: '6px 12px', 
              backgroundColor: '#fff3cd', 
              color: '#856404', 
              borderRadius: '4px', 
              fontWeight: 'bold',
              border: '1px solid #ffeeba'
            }}>
              {datosRespuesta.status}
            </span>
          </div>
          <p>
            <a 
              href={datosRespuesta.file_url} 
              target="_blank" 
              rel="noopener noreferrer" 
              style={{ color: '#0056b3', textDecoration: 'underline' }}
            >
              Ver documento cargado en el servidor
            </a>
          </p>
        </div>
      )}
    </div>
  );
}