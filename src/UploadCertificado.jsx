import React, { useState } from 'react';
import './UploadCertificado.css';

export default function UploadCertificado() {
  const [file, setFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [responseMsg, setResponseMsg] = useState(null);
  const [errorMsg, setErrorMsg] = useState(null);

  const handleFileChange = (e) => {
    const selectedFile = e.target.files[0];
    if (selectedFile) {
      if (selectedFile.size > 5 * 1024 * 1024) {
        setErrorMsg('El archivo supera el límite de 5 MB.');
        setFile(null);
        return;
      }
      setErrorMsg(null);
      setFile(selectedFile);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!file) {
      setErrorMsg('Por favor selecciona un archivo PDF o JPG.');
      return;
    }

    const formData = new FormData();
    formData.append('file', file);

    setLoading(true);
    setErrorMsg(null);
    setResponseMsg(null);

    try {
      const res = await fetch('http://127.0.0.1:8000/api/certificaciones/upload/', {
        method: 'POST',
        body: formData,
      });

      const data = await res.json();

      if (res.ok) {
        setResponseMsg(data);
        setFile(null);
      } else {
        setErrorMsg(data.error || 'Ocurrió un error al subir el archivo.');
      }
    } catch (err) {
      setErrorMsg('No se pudo conectar con el servidor backend de Django.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="upload-container">
      <h2>CHAMBA PE — Panel de Administración</h2>
      <h3>Subir Certificación de Prestador</h3>

      <form onSubmit={handleSubmit} className="upload-form">
        <input 
          type="file" 
          accept=".pdf, .jpg, .jpeg" 
          onChange={handleFileChange} 
        />
        <p className="hint">Formatos permitidos: PDF, JPG (Máx. 5 MB)</p>

        <button type="submit" disabled={loading}>
          {loading ? 'Subiendo...' : 'Enviar Certificado'}
        </button>
      </form>

      {errorMsg && <div className="alert error">{errorMsg}</div>}

      {responseMsg && (
        <div className="alert success">
          <p><strong>¡Éxito!</strong> {responseMsg.message}</p>
          <p><strong>Archivo:</strong> {responseMsg.file_name}</p>
          <div className="status-badge">
            Estado: <span>{responseMsg.status}</span>
          </div>
        </div>
      )}
    </div>
  );
}