// HU-20: textos de las decisiones y motivos de la comparación facial (los usa también el panel).
export const DECISIONES = {
  verificado: { titulo: 'Rostro verificado', clase: 'ok' },
  revision_manual: { titulo: 'En revisión manual', clase: 'revision' },
  rechazado: { titulo: 'No coincide', clase: 'rechazo' },
}

export const MOTIVOS_FACIALES = {
  zona_gris: 'La similitud quedó en duda: un administrador comparará las dos fotos.',
  similitud_baja: 'Tu rostro no se parece lo suficiente a la foto de tu DNI. Prueba con más luz y de frente.',
  sin_rostro_selfie: 'No encontramos un rostro en la selfie. Mira de frente a la cámara.',
  varios_rostros_selfie: 'Hay más de una persona en la selfie. Tómala solo.',
  sin_rostro_dni: 'No encontramos un rostro en la foto de tu DNI: un administrador la revisará.',
  comparador_no_disponible: 'La comparación automática no está disponible por ahora: un administrador la revisará.',
  dni_en_revision: 'Tu DNI sigue en revisión manual; el administrador revisará ambas cosas.',
}
